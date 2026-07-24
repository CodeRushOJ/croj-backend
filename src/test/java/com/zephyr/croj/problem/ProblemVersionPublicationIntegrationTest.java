package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.ProblemMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:problem-publication;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(statements = {
        "DROP TABLE IF EXISTS t_test_bundle",
        "DROP TABLE IF EXISTS t_problem_version",
        "DROP TABLE IF EXISTS t_problem",
        "DROP TABLE IF EXISTS t_submission",
        "CREATE TABLE t_problem (id BIGINT PRIMARY KEY, status INT NOT NULL, published_version_id BIGINT, is_deleted INT NOT NULL DEFAULT 0)",
        "CREATE TABLE t_problem_version (id BIGINT PRIMARY KEY, problem_id BIGINT NOT NULL, state VARCHAR(20) NOT NULL, published_at TIMESTAMP)",
        "CREATE TABLE t_test_bundle (id BIGINT PRIMARY KEY, problem_version_id BIGINT NOT NULL UNIQUE, object_key VARCHAR(512), sha256 CHAR(64), size_bytes BIGINT, manifest_json CLOB)",
        "CREATE TABLE t_submission (id BIGINT PRIMARY KEY, problem_id BIGINT NOT NULL, user_id BIGINT NOT NULL, status INT NOT NULL, is_deleted INT NOT NULL DEFAULT 0)",
        "INSERT INTO t_problem VALUES (42,1,NULL,0)",
        "INSERT INTO t_problem_version VALUES (101,42,'DRAFT',NULL)"
})
class ProblemVersionPublicationIntegrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void refusesToPublishAVersionWithoutATestBundle() {
        ProblemVersionPublicationService service = new ProblemVersionPublicationService(jdbc);

        assertThrows(BusinessException.class, () -> service.publish(42L, 101L));

        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT state FROM t_problem_version WHERE id=101", String.class));
        assertNull(jdbc.queryForObject(
                "SELECT published_version_id FROM t_problem WHERE id=42", Long.class));
    }

    @Test
    void atomicallyPublishesAJudgeReadyVersionAndSwitchesTheProblemPointer() {
        jdbc.update("INSERT INTO t_test_bundle VALUES (7,101,'test-bundles/42/101/a.zip',REPEAT('a',64),12,'{}')");
        ProblemVersionPublicationService service = new ProblemVersionPublicationService(jdbc);

        service.publish(42L, 101L);

        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT state FROM t_problem_version WHERE id=101", String.class));
        assertEquals(101L, jdbc.queryForObject(
                "SELECT published_version_id FROM t_problem WHERE id=42", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT status FROM t_problem WHERE id=42", Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicationLocksTheProblemAggregateBeforeItsVersion() {
        JdbcTemplate mockedJdbc = mock(JdbcTemplate.class);
        when(mockedJdbc.query(
                        contains("FROM t_problem WHERE"),
                        any(RowMapper.class),
                        eq(42L)))
                .thenReturn(List.of(42L));
        when(mockedJdbc.query(
                        contains("t_problem_version"),
                        any(RowMapper.class),
                        eq(101L),
                        eq(42L)))
                .thenReturn(List.of("PUBLISHED"));
        when(mockedJdbc.update(any(String.class), anyLong(), anyLong())).thenReturn(1);
        ProblemVersionPublicationService service = new ProblemVersionPublicationService(mockedJdbc);

        service.publish(42L, 101L);

        InOrder order = inOrder(mockedJdbc);
        order.verify(mockedJdbc).query(
                contains("FROM t_problem WHERE"),
                any(RowMapper.class),
                eq(42L));
        order.verify(mockedJdbc).query(
                contains("t_problem_version"),
                any(RowMapper.class),
                eq(101L),
                eq(42L));
    }

    @Test
    void submissionStatusDistinguishesAcceptedAttemptedAndNeverSubmitted() throws Exception {
        Select annotation = ProblemMapper.class
                .getMethod("getUserSubmitStatus", Long.class, Long.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", annotation.value())
                .replace("#{problemId}", "?")
                .replace("#{userId}", "?");

        assertEquals(0, jdbc.queryForObject(sql, Integer.class, 42L, 7L));

        jdbc.update("INSERT INTO t_submission VALUES (1,42,7,3,0)");
        assertEquals(2, jdbc.queryForObject(sql, Integer.class, 42L, 7L));

        jdbc.update("INSERT INTO t_submission VALUES (2,42,7,1,0)");
        assertEquals(1, jdbc.queryForObject(sql, Integer.class, 42L, 7L));
    }
}
