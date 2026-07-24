package com.zephyr.croj.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:announcement-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=false",
})
@Import(AnnouncementRepository.class)
class AnnouncementPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-19T02:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AnnouncementRepository repository;

    @BeforeEach
    void createSchema() {
        jdbc.execute("DROP TABLE IF EXISTS t_announcement");
        jdbc.execute("""
                CREATE TABLE t_announcement (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  scope VARCHAR(16) NOT NULL,
                  contest_id BIGINT NULL,
                  title VARCHAR(200) NOT NULL,
                  content_markdown LONGTEXT NOT NULL,
                  lifecycle VARCHAR(16) NOT NULL,
                  is_pinned TINYINT NOT NULL DEFAULT 0,
                  pin_order INT NOT NULL DEFAULT 0,
                  publish_at DATETIME(3) NULL,
                  expires_at DATETIME(3) NULL,
                  created_by BIGINT NOT NULL,
                  updated_by BIGINT NOT NULL,
                  published_by BIGINT NULL,
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  archived_at DATETIME(3) NULL,
                  version BIGINT NOT NULL DEFAULT 0
                )
                """);
    }

    @Test
    void visibleFeedFiltersWindowsAndUsesStablePinOrdering() {
        insert(1, "PUBLISHED", false, 0, NOW.minusSeconds(10), null, NOW.minusSeconds(10));
        insert(2, "SCHEDULED", true, 8, NOW.minusSeconds(5), NOW.plusSeconds(50), NOW.minusSeconds(5));
        insert(3, "PUBLISHED", true, 2, NOW.minusSeconds(20), null, NOW.minusSeconds(20));
        insert(4, "SCHEDULED", true, 1, NOW.plusSeconds(1), null, NOW);
        insert(5, "PUBLISHED", true, 1, NOW.minusSeconds(20), NOW, NOW);
        insert(6, "ARCHIVED", true, 0, NOW.minusSeconds(20), null, NOW);
        insert(7, "PUBLISHED", false, 999, NOW.minusSeconds(1), null, NOW.minusSeconds(1));

        List<Long> ids = repository.listVisible(NOW, 0, 20).stream()
                .map(AnnouncementRepository.AnnouncementRecord::id)
                .toList();

        assertEquals(List.of(3L, 2L, 7L, 1L), ids);
        assertEquals(4L, repository.countVisible(NOW));
        assertTrue(repository.findVisibleById(2L, NOW).isPresent());
        assertFalse(repository.findVisibleById(4L, NOW).isPresent());
        assertFalse(repository.findVisibleById(5L, NOW).isPresent());
    }

    @Test
    void adminEffectiveFiltersMatchLifecycleDerivation() {
        insert(1, "DRAFT", false, 0, null, null, NOW.minusSeconds(10));
        insert(2, "SCHEDULED", false, 0, NOW.plusSeconds(1), null, NOW.minusSeconds(9));
        insert(3, "SCHEDULED", false, 0, NOW.minusSeconds(1), null, NOW.minusSeconds(8));
        insert(4, "PUBLISHED", false, 0, NOW.minusSeconds(2), NOW, NOW.minusSeconds(7));
        insert(5, "ARCHIVED", false, 0, NOW.minusSeconds(3), null, NOW.minusSeconds(6));

        assertEquals(List.of(1L), ids(AnnouncementLifecycle.DRAFT));
        assertEquals(List.of(2L), ids(AnnouncementLifecycle.SCHEDULED));
        assertEquals(List.of(3L), ids(AnnouncementLifecycle.PUBLISHED));
        assertEquals(List.of(4L), ids(AnnouncementLifecycle.EXPIRED));
        assertEquals(List.of(5L), ids(AnnouncementLifecycle.ARCHIVED));
        assertEquals(1L, repository.countAdmin(NOW, AnnouncementLifecycle.EXPIRED));
    }

    @Test
    void createAndCompareAndSetUpdatesPreserveAuditAndRejectStaleVersion() {
        long id = repository.create(new AnnouncementRepository.CreateCommand(
                "Maintenance", "Details", true, 2, 9L));
        var created = repository.findById(id).orElseThrow();
        assertEquals(AnnouncementLifecycle.DRAFT, created.lifecycle());
        assertEquals(9L, created.createdBy());
        assertEquals(0L, created.version());

        var update = new AnnouncementRequests.Draft("Changed", "Body", false, 0);
        assertEquals(1, repository.updateContent(id, 0L, update, 10L));
        assertEquals(0, repository.updateContent(id, 0L, update, 11L));
        var changed = repository.findById(id).orElseThrow();
        assertEquals("Changed", changed.title());
        assertEquals(10L, changed.updatedBy());
        assertEquals(1L, changed.version());
    }

    private List<Long> ids(AnnouncementLifecycle status) {
        return repository.listAdmin(NOW, status, 0, 20).stream()
                .map(AnnouncementRepository.AnnouncementRecord::id)
                .toList();
    }

    private void insert(
            long id,
            String lifecycle,
            boolean pinned,
            int pinOrder,
            Instant publishAt,
            Instant expiresAt,
            Instant updatedAt) {
        jdbc.update(
                """
                INSERT INTO t_announcement
                  (id,scope,title,content_markdown,lifecycle,is_pinned,pin_order,publish_at,
                   expires_at,created_by,updated_by,published_by,created_at,updated_at,version)
                VALUES (?,'GLOBAL',?, ?, ?, ?, ?, ?, ?, 9, 9, 9, ?, ?, 0)
                """,
                id,
                "Title " + id,
                "Body " + id,
                lifecycle,
                pinned,
                pinOrder,
                timestamp(publishAt),
                timestamp(expiresAt),
                Timestamp.from(NOW.minusSeconds(100)),
                Timestamp.from(updatedAt));
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
