package com.zephyr.croj.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__oj_schema.sql");

    @Test
    void cleanSchemaCoversTheCompleteFreeOjDomain() throws IOException {
        assertTrue(Files.isRegularFile(MIGRATION), "missing clean Flyway schema");
        String sql = Files.readString(MIGRATION).toLowerCase();
        for (String table : new String[] {
            "t_user", "t_refresh_session", "t_problem", "t_problem_version", "t_test_bundle",
            "t_submission", "t_judge_attempt", "t_outbox_event", "t_contest", "t_contest_problem",
            "t_forum_category", "t_forum_post", "t_forum_comment", "t_solution", "t_content_report",
            "t_notification", "t_audit_log",
        }) {
            assertTrue(sql.contains("create table `" + table + "`"), "missing table " + table);
        }
        assertFalse(sql.contains("create database"));
        assertFalse(sql.matches("(?s).*\\nuse\\s+.*"));
    }

    @Test
    void developmentSeedIsSeparatedFromProductionMigrations() {
        Path seed = Path.of("src", "main", "resources", "db", "dev", "R__development_seed.sql");
        assertTrue(Files.isRegularFile(seed));
    }
}
