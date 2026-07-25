package com.zephyr.croj.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zephyr.croj.model.dto.contest.ContestRequests;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ContestAdminServiceTest {
    private ContestRepository repository;
    private ContestAdminService service;

    @BeforeEach
    void setUp() {
        repository = mock(ContestRepository.class);
        service = new ContestAdminService(repository);
    }

    @Test
    void arrangementRejectsVersionThatIsNotPublishedForTheProblem() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftContest()));
        when(repository.findAvailableProblemVersionRule(42L, 101L)).thenReturn(Optional.empty());
        var request = new ContestRequests.ProblemArrangement(
                List.of(new ContestRequests.ProblemItem(42L, 101L, "A", 100)));

        ContestApiException error = assertThrows(
                ContestApiException.class, () -> service.arrangeProblems(1L, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(repository, never()).replaceProblems(1L, request.problems());
    }

    @Test
    void oiArrangementRejectsAnAcmProblemVersion() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(oiDraftContest()));
        when(repository.findAvailableProblemVersionRule(42L, 101L))
                .thenReturn(Optional.of(rule(42L, 101L, 100, 0, 100)));
        var request = new ContestRequests.ProblemArrangement(
                List.of(new ContestRequests.ProblemItem(42L, 101L, "A", 100)));

        ContestApiException error = assertThrows(
                ContestApiException.class, () -> service.arrangeProblems(1L, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(repository, never()).replaceProblems(1L, request.problems());
    }

    @Test
    void oiArrangementRequiresPinnedProblemAndContestScoresToMatch() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(oiDraftContest()));
        when(repository.findAvailableProblemVersionRule(42L, 101L))
                .thenReturn(Optional.of(rule(42L, 101L, 100, 1, 100)));
        var request = new ContestRequests.ProblemArrangement(
                List.of(new ContestRequests.ProblemItem(42L, 101L, "A", 80)));

        ContestApiException error = assertThrows(
                ContestApiException.class, () -> service.arrangeProblems(1L, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(repository, never()).replaceProblems(1L, request.problems());
    }

    @Test
    void replyCannotCrossContestBoundary() {
        when(repository.findById(1L)).thenReturn(Optional.of(draftContest()));
        when(repository.clarificationBelongsToContest(20L, 1L)).thenReturn(false);
        var request = new ContestRequests.ClarificationReply("answer", true);

        ContestApiException error = assertThrows(
                ContestApiException.class, () -> service.reply(1L, 20L, request, 9L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verify(repository, never()).addClarificationReply(20L, request, 9L);
    }

    @Test
    void managedRegistrationRejectsMissingOrDisabledUsers() {
        when(repository.findById(1L)).thenReturn(Optional.of(draftContest()));
        when(repository.activeUserExists(7L)).thenReturn(false);

        ContestApiException error = assertThrows(
                ContestApiException.class, () -> service.manageRegistration(1L, 7L, 9L, true));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(repository, never()).register(1L, 7L, 9L);
    }

    @Test
    void publicationLocksTheContestAggregateBeforeChangingLifecycle() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftContest()));
        when(repository.problemCount(1L)).thenReturn(1);
        when(repository.listContestProblemRules(1L))
                .thenReturn(List.of(rule(42L, 101L, 100, 0, 100)));
        when(repository.transitionLifecycle(1L, "DRAFT", "PUBLISHED")).thenReturn(1);

        service.publish(1L);

        verify(repository).findByIdForUpdate(1L);
        verify(repository).transitionLifecycle(1L, "DRAFT", "PUBLISHED");
    }

    @Test
    void publicationRevalidatesProblemsAfterTheDraftRuleChanges() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(oiDraftContest()));
        when(repository.problemCount(1L)).thenReturn(1);
        when(repository.listContestProblemRules(1L))
                .thenReturn(List.of(rule(42L, 101L, 100, 0, 100)));

        ContestApiException error =
                assertThrows(ContestApiException.class, () -> service.publish(1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(repository, never()).transitionLifecycle(1L, "DRAFT", "PUBLISHED");
    }

    @Test
    void invalidScheduleIsReportedAsAnUnprocessableApiRequest() {
        var invalid = new ContestRequests.Upsert(
                "Weekly",
                "d",
                "ACM",
                "PUBLIC",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-11T00:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z"),
                null,
                Instant.parse("2026-07-10T02:00:00Z"));

        ContestApiException error = assertThrows(
                ContestApiException.class, () -> service.create(invalid, 9L));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
    }

    private ContestRepository.ContestRecord draftContest() {
        return draftContest("ACM");
    }

    private ContestRepository.ContestRecord oiDraftContest() {
        return draftContest("OI");
    }

    private ContestRepository.ContestRecord draftContest(String ruleType) {
        return new ContestRepository.ContestRecord(
                1L,
                "Weekly",
                "d",
                ruleType,
                "PUBLIC",
                "DRAFT",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-09T00:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z"),
                Instant.parse("2026-07-10T01:30:00Z"),
                Instant.parse("2026-07-10T02:00:00Z"),
                9L,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"));
    }

    private ContestRepository.ContestProblemRule rule(
            long problemId,
            long problemVersionId,
            int score,
            int judgeMode,
            int totalScore) {
        return new ContestRepository.ContestProblemRule(
                problemId,
                problemVersionId,
                score,
                "{\"totalScore\":" + totalScore + "}",
                "{\"judgeMode\":" + judgeMode + "}");
    }
}
