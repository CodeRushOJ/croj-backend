package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zephyr.croj.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
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
        "CREATE TABLE t_problem (id BIGINT PRIMARY KEY, status INT NOT NULL, published_version_id BIGINT, is_deleted INT NOT NULL DEFAULT 0)",
        "CREATE TABLE t_problem_version (id BIGINT PRIMARY KEY, problem_id BIGINT NOT NULL, state VARCHAR(20) NOT NULL, published_at TIMESTAMP)",
        "CREATE TABLE t_test_bundle (id BIGINT PRIMARY KEY, problem_version_id BIGINT NOT NULL UNIQUE, object_key VARCHAR(512), sha256 CHAR(64), size_bytes BIGINT, manifest_json CLOB)",
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
}
