package com.zephyr.croj.problem.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zephyr.croj.common.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcProblemImportJobStoreTest {
    private JdbcProblemImportJobStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:problem-import-" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE t_problem_import_job (
                  id VARCHAR(36) PRIMARY KEY,
                  actor_id BIGINT NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  detected_format VARCHAR(32) NOT NULL,
                  file_sha256 CHAR(64) NOT NULL,
                  staging_object_key VARCHAR(512) NOT NULL,
                  summary_json VARCHAR(4096) NOT NULL,
                  imported_count INT NOT NULL,
                  created_at TIMESTAMP(3) NOT NULL,
                  updated_at TIMESTAMP(3) NOT NULL,
                  expires_at TIMESTAMP(3) NOT NULL,
                  committed_at TIMESTAMP(3)
                )
                """);
        store = new JdbcProblemImportJobStore(jdbc);
    }

    @Test
    void bindsJobsToTheirOwnerAndRejectsExpiredJobs() {
        Instant created = Instant.parse("2026-07-19T08:00:00Z");
        store.create(validatedJob("job-1", 7L, created, created.plusSeconds(60)));

        ProblemImportJob found = store.lockOwned("job-1", 7L, created.plusSeconds(30));

        assertThat(found.actorId()).isEqualTo(7L);
        assertThat(found.status()).isEqualTo("VALIDATED");
        assertThrows(BusinessException.class,
                () -> store.lockOwned("job-1", 8L, created.plusSeconds(30)));
        assertThrows(BusinessException.class,
                () -> store.lockOwned("job-1", 7L, created.plusSeconds(61)));
    }

    @Test
    void recordsACommitOnceAndReturnsTheStoredResultForRetry() {
        Instant created = Instant.parse("2026-07-19T08:00:00Z");
        Instant committed = created.plusSeconds(10);
        store.create(validatedJob("job-2", 7L, created, created.plusSeconds(60)));

        store.markCommitted("job-2", 3, committed);
        ProblemImportJob found = store.lockOwned("job-2", 7L, committed.plusSeconds(1));

        assertThat(found.status()).isEqualTo("COMMITTED");
        assertThat(found.importedCount()).isEqualTo(3);
        assertThat(found.committedAt()).isEqualTo(committed);
        assertThrows(BusinessException.class, () -> store.markCommitted("job-2", 4, committed));
    }

    private ProblemImportJob validatedJob(
            String id,
            long actorId,
            Instant created,
            Instant expires) {
        return new ProblemImportJob(
                id,
                actorId,
                "VALIDATED",
                "FPS_XML",
                "a".repeat(64),
                "problem-import-staging/" + id + "/package",
                "{}",
                0,
                created,
                expires,
                null);
    }
}
