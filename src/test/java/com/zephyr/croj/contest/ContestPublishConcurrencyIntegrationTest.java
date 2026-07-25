package com.zephyr.croj.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;

import com.zephyr.croj.model.dto.contest.ContestRequests;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contest-publish-race;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ContestRepository.class, ContestAdminService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Sql(statements = {
        "DROP TABLE IF EXISTS t_contest_problem",
        "DROP TABLE IF EXISTS t_test_bundle",
        "DROP TABLE IF EXISTS t_problem_version",
        "DROP TABLE IF EXISTS t_problem",
        "DROP TABLE IF EXISTS t_contest",
        "CREATE TABLE t_contest (id BIGINT PRIMARY KEY, title VARCHAR(255), description_markdown CLOB, rule_type VARCHAR(16), visibility VARCHAR(16), lifecycle VARCHAR(16), registration_opens_at TIMESTAMP, registration_closes_at TIMESTAMP, starts_at TIMESTAMP, freeze_at TIMESTAMP, ends_at TIMESTAMP, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
        "CREATE TABLE t_problem (id BIGINT PRIMARY KEY, is_deleted INT NOT NULL)",
        "CREATE TABLE t_problem_version (id BIGINT PRIMARY KEY, problem_id BIGINT NOT NULL, state VARCHAR(20) NOT NULL)",
        "CREATE TABLE t_test_bundle (id BIGINT PRIMARY KEY, problem_version_id BIGINT NOT NULL UNIQUE)",
        "CREATE TABLE t_contest_problem (contest_id BIGINT, problem_id BIGINT, problem_version_id BIGINT, label VARCHAR(16), score INT, PRIMARY KEY(contest_id,problem_id), UNIQUE(contest_id,label))",
        "INSERT INTO t_contest VALUES (1,'Weekly','d','ACM','PUBLIC','DRAFT','2026-07-01 00:00:00','2026-07-09 00:00:00','2026-07-10 00:00:00','2026-07-10 01:30:00','2026-07-10 02:00:00',9,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        "INSERT INTO t_problem VALUES (42,0),(43,0)",
        "INSERT INTO t_problem_version VALUES (101,42,'PUBLISHED'),(102,43,'PUBLISHED')",
        "INSERT INTO t_test_bundle VALUES (1,101),(2,102)",
        "INSERT INTO t_contest_problem VALUES (1,42,101,'A',100)"
})
class ContestPublishConcurrencyIntegrationTest {
    @Autowired private ContestAdminService service;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ContestRepository repository;

    @Test
    void publicationAndArrangementAreSerializedOnTheContestAggregate() throws Exception {
        CountDownLatch publisherLocked = new CountDownLatch(1);
        CountDownLatch arrangerAttempted = new CountDownLatch(1);
        CountDownLatch releasePublisher = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("contest-publisher")) {
                Object record = invocation.callRealMethod();
                publisherLocked.countDown();
                if (!releasePublisher.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("publisher lock was not released");
                }
                return record;
            }
            if (Thread.currentThread().getName().equals("contest-arranger")) {
                arrangerAttempted.countDown();
            }
            return invocation.callRealMethod();
        }).when(repository).findByIdForUpdate(1L);

        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("contest-worker");
            return thread;
        });
        try {
            Future<Throwable> publish = pool.submit(() -> runAs("contest-publisher", () -> service.publish(1L)));
            publisherLocked.await(5, TimeUnit.SECONDS);
            var arrangement = new ContestRequests.ProblemArrangement(
                    List.of(new ContestRequests.ProblemItem(43L, 102L, "B", 100)));
            Future<Throwable> arrange = pool.submit(
                    () -> runAs("contest-arranger", () -> service.arrangeProblems(1L, arrangement)));
            arrangerAttempted.await(5, TimeUnit.SECONDS);
            releasePublisher.countDown();

            assertNull(publish.get(5, TimeUnit.SECONDS));
            assertInstanceOf(ContestApiException.class, arrange.get(5, TimeUnit.SECONDS));
        } finally {
            releasePublisher.countDown();
            pool.shutdownNow();
        }

        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT lifecycle FROM t_contest WHERE id=1", String.class));
        assertEquals(42L, jdbc.queryForObject(
                "SELECT problem_id FROM t_contest_problem WHERE contest_id=1", Long.class));
    }

    private Throwable runAs(String threadName, ThrowingRunnable action) {
        Thread.currentThread().setName(threadName);
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
