package com.zephyr.croj.announcement;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {
    private final AnnouncementRepository announcements;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository announcements, Clock clock) {
        this.announcements = announcements;
        this.clock = clock;
    }

    @Transactional
    public long create(AnnouncementRequests.Draft request, long administratorId) {
        return announcements.create(new AnnouncementRepository.CreateCommand(
                request.title().trim(),
                request.contentMarkdown().trim(),
                request.pinned(),
                request.pinOrder(),
                administratorId));
    }

    @Transactional
    public void update(
            long announcementId,
            long expectedVersion,
            AnnouncementRequests.Draft request,
            long administratorId) {
        requireMutable(announcementId, expectedVersion);
        changed(announcements.updateContent(announcementId, expectedVersion, request, administratorId));
    }

    @Transactional
    public void schedule(
            long announcementId,
            long expectedVersion,
            AnnouncementRequests.Schedule request,
            long administratorId) {
        requireMutable(announcementId, expectedVersion);
        Instant now = clock.instant();
        if (!request.publishAt().isAfter(now)) {
            throw AnnouncementApiException.unprocessable("scheduled publication must be in the future");
        }
        validateWindow(request.publishAt(), request.expiresAt());
        changed(announcements.schedule(
                announcementId,
                expectedVersion,
                request.publishAt(),
                request.expiresAt(),
                administratorId));
    }

    @Transactional
    public void publish(
            long announcementId,
            long expectedVersion,
            AnnouncementRequests.Publish request,
            long administratorId) {
        requireMutable(announcementId, expectedVersion);
        Instant now = clock.instant();
        validateWindow(now, request.expiresAt());
        changed(announcements.publish(
                announcementId, expectedVersion, now, request.expiresAt(), administratorId));
    }

    @Transactional
    public void withdraw(long announcementId, long expectedVersion, long administratorId) {
        var current = requireVersion(announcementId, expectedVersion);
        if (current.lifecycle() != AnnouncementLifecycle.SCHEDULED
                && current.lifecycle() != AnnouncementLifecycle.PUBLISHED) {
            throw AnnouncementApiException.unprocessable("only scheduled or published announcements can be withdrawn");
        }
        changed(announcements.withdraw(announcementId, expectedVersion, administratorId));
    }

    @Transactional
    public void archive(long announcementId, long expectedVersion, long administratorId) {
        requireMutable(announcementId, expectedVersion);
        changed(announcements.archive(announcementId, expectedVersion, clock.instant(), administratorId));
    }

    @Transactional(readOnly = true)
    public PublicPage listPublic(int requestedPage, int requestedSize) {
        int page = Math.max(requestedPage, 1);
        int size = Math.min(Math.max(requestedSize, 1), 100);
        long offset = ((long) page - 1L) * size;
        Instant now = clock.instant();
        List<PublicAnnouncement> items = announcements.listVisible(now, offset, size).stream()
                .map(record -> publicView(record, now))
                .toList();
        return new PublicPage(page, size, announcements.countVisible(now), items);
    }

    @Transactional(readOnly = true)
    public List<PublicAnnouncement> current(int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 20);
        Instant now = clock.instant();
        return announcements.listVisible(now, 0, limit).stream()
                .map(record -> publicView(record, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicAnnouncement detail(long announcementId) {
        Instant now = clock.instant();
        return announcements.findVisibleById(announcementId, now)
                .map(record -> publicView(record, now))
                .orElseThrow(AnnouncementApiException::notFound);
    }

    @Transactional(readOnly = true)
    public AdminPage listAdmin(int requestedPage, int requestedSize, String statusValue) {
        int page = Math.max(requestedPage, 1);
        int size = Math.min(Math.max(requestedSize, 1), 100);
        long offset = ((long) page - 1L) * size;
        Instant now = clock.instant();
        AnnouncementLifecycle status = AnnouncementLifecycle.parseFilter(statusValue);
        List<AdminAnnouncement> items = announcements.listAdmin(now, status, offset, size).stream()
                .map(record -> adminView(record, now))
                .toList();
        return new AdminPage(page, size, announcements.countAdmin(now, status), status, items);
    }

    private AnnouncementRepository.AnnouncementRecord require(long announcementId) {
        return announcements.findById(announcementId).orElseThrow(AnnouncementApiException::notFound);
    }

    private AnnouncementRepository.AnnouncementRecord requireVersion(long announcementId, long expectedVersion) {
        var current = require(announcementId);
        if (current.version() != expectedVersion) {
            throw AnnouncementApiException.conflict();
        }
        return current;
    }

    private AnnouncementRepository.AnnouncementRecord requireMutable(long announcementId, long expectedVersion) {
        var current = requireVersion(announcementId, expectedVersion);
        if (current.lifecycle() == AnnouncementLifecycle.ARCHIVED) {
            throw AnnouncementApiException.unprocessable("archived announcements are immutable");
        }
        return current;
    }

    private void validateWindow(Instant publishAt, Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(publishAt)) {
            throw AnnouncementApiException.unprocessable("announcement expiry must be after publication");
        }
    }

    private void changed(int rows) {
        if (rows != 1) {
            throw AnnouncementApiException.conflict();
        }
    }

    private PublicAnnouncement publicView(AnnouncementRepository.AnnouncementRecord record, Instant now) {
        return new PublicAnnouncement(
                record.id(),
                record.title(),
                record.contentMarkdown(),
                record.pinned(),
                record.pinOrder(),
                AnnouncementLifecycle.effective(record.lifecycle(), record.publishAt(), record.expiresAt(), now),
                record.publishAt(),
                record.expiresAt(),
                record.updatedAt());
    }

    private AdminAnnouncement adminView(AnnouncementRepository.AnnouncementRecord record, Instant now) {
        return new AdminAnnouncement(
                record.id(),
                record.title(),
                record.contentMarkdown(),
                record.pinned(),
                record.pinOrder(),
                AnnouncementLifecycle.effective(record.lifecycle(), record.publishAt(), record.expiresAt(), now),
                record.lifecycle(),
                record.publishAt(),
                record.expiresAt(),
                record.createdBy(),
                record.updatedBy(),
                record.publishedBy(),
                record.createdAt(),
                record.updatedAt(),
                record.archivedAt(),
                record.version());
    }

    public record PublicAnnouncement(
            long id,
            String title,
            String contentMarkdown,
            boolean pinned,
            int pinOrder,
            AnnouncementLifecycle status,
            Instant publishAt,
            Instant expiresAt,
            Instant updatedAt) {}

    public record AdminAnnouncement(
            long id,
            String title,
            String contentMarkdown,
            boolean pinned,
            int pinOrder,
            AnnouncementLifecycle status,
            AnnouncementLifecycle storedLifecycle,
            Instant publishAt,
            Instant expiresAt,
            long createdBy,
            long updatedBy,
            Long publishedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            long version) {}

    public record PublicPage(int page, int size, long total, List<PublicAnnouncement> items) {}

    public record AdminPage(
            int page,
            int size,
            long total,
            AnnouncementLifecycle status,
            List<AdminAnnouncement> items) {}
}
