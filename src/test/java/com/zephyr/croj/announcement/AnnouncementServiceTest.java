package com.zephyr.croj.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AnnouncementServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T02:00:00Z");

    private AnnouncementRepository repository;
    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(AnnouncementRepository.class);
        service = new AnnouncementService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesDraftContentAndCapturesAdministratorAudit() {
        when(repository.create(any())).thenReturn(41L);

        long id = service.create(new AnnouncementRequests.Draft("  Maintenance  ", "  Details  ", true, 3), 9L);

        assertEquals(41L, id);
        var command = ArgumentCaptor.forClass(AnnouncementRepository.CreateCommand.class);
        verify(repository).create(command.capture());
        assertEquals("Maintenance", command.getValue().title());
        assertEquals("Details", command.getValue().contentMarkdown());
        assertEquals(9L, command.getValue().administratorId());
    }

    @Test
    void scheduleRejectsPublicationThatIsNotInTheFuture() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));
        var request = new AnnouncementRequests.Schedule(NOW, NOW.plusSeconds(60));

        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class, () -> service.schedule(1L, 7L, request, 9L));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(repository, never()).schedule(
                anyLong(), anyLong(), any(Instant.class), nullable(Instant.class), anyLong());
    }

    @Test
    void scheduleRejectsAnInvertedVisibilityWindow() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));
        Instant publishAt = NOW.plusSeconds(60);

        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class,
                () -> service.schedule(
                        1L, 7L, new AnnouncementRequests.Schedule(publishAt, publishAt), 9L));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
    }

    @Test
    void scheduleUsesOptimisticVersionAndAdministratorAudit() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));
        when(repository.schedule(1L, 7L, NOW.plusSeconds(60), NOW.plusSeconds(120), 9L)).thenReturn(1);

        service.schedule(
                1L,
                7L,
                new AnnouncementRequests.Schedule(NOW.plusSeconds(60), NOW.plusSeconds(120)),
                9L);

        verify(repository).schedule(1L, 7L, NOW.plusSeconds(60), NOW.plusSeconds(120), 9L);
    }

    @Test
    void immediatePublicationRejectsAnExpiredWindow() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));

        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class,
                () -> service.publish(1L, 7L, new AnnouncementRequests.Publish(NOW), 9L));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(repository, never()).publish(
                anyLong(), anyLong(), any(Instant.class), nullable(Instant.class), anyLong());
    }

    @Test
    void publicationCapturesOneClockInstantForWindowAndAudit() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));
        when(repository.publish(1L, 7L, NOW, NOW.plusSeconds(3600), 9L)).thenReturn(1);

        service.publish(1L, 7L, new AnnouncementRequests.Publish(NOW.plusSeconds(3600)), 9L);

        verify(repository).publish(1L, 7L, NOW, NOW.plusSeconds(3600), 9L);
    }

    @Test
    void staleMutationIsReportedAsConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));
        when(repository.publish(1L, 7L, NOW, null, 9L)).thenReturn(0);

        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class,
                () -> service.publish(1L, 7L, new AnnouncementRequests.Publish(null), 9L));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void archivedAnnouncementCannotBeUpdatedOrRepublished() {
        when(repository.findById(1L)).thenReturn(Optional.of(archived()));

        AnnouncementApiException updateError = assertThrows(
                AnnouncementApiException.class,
                () -> service.update(1L, 8L, new AnnouncementRequests.Draft("New", "Body", false, 0), 9L));
        AnnouncementApiException publishError = assertThrows(
                AnnouncementApiException.class,
                () -> service.publish(1L, 8L, new AnnouncementRequests.Publish(null), 9L));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, updateError.getStatus());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, publishError.getStatus());
    }

    @Test
    void withdrawRequiresScheduledOrPublishedState() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));

        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class, () -> service.withdraw(1L, 7L, 9L));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(repository, never()).withdraw(anyLong(), anyLong(), anyLong());
    }

    @Test
    void archiveIsTerminalAndUsesCurrentUtcInstant() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));
        when(repository.archive(1L, 7L, NOW, 9L)).thenReturn(1);

        service.archive(1L, 7L, 9L);

        verify(repository).archive(1L, 7L, NOW, 9L);
    }

    @Test
    void clientVersionPreventsAStaleEditorFromOverwritingNewerContent() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft()));

        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class,
                () -> service.update(
                        1L,
                        6L,
                        new AnnouncementRequests.Draft("Stale", "Old body", false, 0),
                        9L));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(repository, never()).updateContent(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void effectiveLifecycleTracksScheduleAndExpiryWithoutAJob() {
        assertEquals(
                AnnouncementLifecycle.SCHEDULED,
                AnnouncementLifecycle.effective(
                        AnnouncementLifecycle.SCHEDULED, NOW.plusSeconds(1), null, NOW));
        assertEquals(
                AnnouncementLifecycle.PUBLISHED,
                AnnouncementLifecycle.effective(
                        AnnouncementLifecycle.SCHEDULED, NOW, NOW.plusSeconds(1), NOW));
        assertEquals(
                AnnouncementLifecycle.EXPIRED,
                AnnouncementLifecycle.effective(
                        AnnouncementLifecycle.PUBLISHED, NOW.minusSeconds(2), NOW, NOW));
        assertEquals(
                AnnouncementLifecycle.ARCHIVED,
                AnnouncementLifecycle.effective(
                        AnnouncementLifecycle.ARCHIVED, NOW.minusSeconds(2), NOW.minusSeconds(1), NOW));
    }

    @Test
    void publicPageUsesBoundedPaginationAndOneVisibilityInstant() {
        when(repository.listVisible(NOW, 0, 100)).thenReturn(List.of());
        when(repository.countVisible(NOW)).thenReturn(0L);

        AnnouncementService.PublicPage page = service.listPublic(0, 500);

        assertEquals(1, page.page());
        assertEquals(100, page.size());
        verify(repository).listVisible(NOW, 0, 100);
        verify(repository).countVisible(NOW);
    }

    @Test
    void missingOrInvisiblePublicDetailIsNotFound() {
        when(repository.findVisibleById(77L, NOW)).thenReturn(Optional.empty());

        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class, () -> service.detail(77L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void adminStatusFilterAcceptsEffectiveExpiredButRejectsUnknownValues() {
        when(repository.listAdmin(NOW, AnnouncementLifecycle.EXPIRED, 0, 20)).thenReturn(List.of());
        when(repository.countAdmin(NOW, AnnouncementLifecycle.EXPIRED)).thenReturn(0L);

        assertEquals(
                AnnouncementLifecycle.EXPIRED,
                service.listAdmin(1, 20, "expired").status());
        AnnouncementApiException error = assertThrows(
                AnnouncementApiException.class, () -> service.listAdmin(1, 20, "deleted"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    private AnnouncementRepository.AnnouncementRecord draft() {
        return new AnnouncementRepository.AnnouncementRecord(
                1L, "GLOBAL", null, "Title", "Body", AnnouncementLifecycle.DRAFT,
                false, 0, null, null, 9L, 9L, null,
                NOW.minusSeconds(60), NOW.minusSeconds(60), null, 7L);
    }

    private AnnouncementRepository.AnnouncementRecord archived() {
        return new AnnouncementRepository.AnnouncementRecord(
                1L, "GLOBAL", null, "Title", "Body", AnnouncementLifecycle.ARCHIVED,
                false, 0, NOW.minusSeconds(60), null, 9L, 9L, 9L,
                NOW.minusSeconds(60), NOW.minusSeconds(30), NOW.minusSeconds(30), 8L);
    }
}
