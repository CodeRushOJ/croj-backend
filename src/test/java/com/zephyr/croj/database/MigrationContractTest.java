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
    private static final Path OUTBOX_CLAIMS = Path.of(
            "src", "main", "resources", "db", "migration", "V2__add_outbox_claims.sql");
    private static final Path COMMUNITY_CONTENT = Path.of(
            "src", "main", "resources", "db", "migration", "V3__community_content_api.sql");
    private static final Path JUDGE_RESULTS = Path.of(
            "src", "main", "resources", "db", "migration", "V4__judge_result_ingestion.sql");

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
        assertFalse(sql.contains("`claimed_by`"), "published V1 migrations must remain immutable");
        assertFalse(sql.contains("create database"));
        assertFalse(sql.matches("(?s).*\\nuse\\s+.*"));
    }

    @Test
    void developmentSeedIsSeparatedFromProductionMigrations() {
        Path seed = Path.of("src", "main", "resources", "db", "dev", "R__development_seed.sql");
        assertTrue(Files.isRegularFile(seed));
    }

    @Test
    void outboxClaimsAreAddedByAForwardOnlyMigration() throws IOException {
        assertTrue(Files.isRegularFile(OUTBOX_CLAIMS));
        String sql = Files.readString(OUTBOX_CLAIMS).toLowerCase();
        assertTrue(sql.contains("`claimed_by`"));
        assertTrue(sql.contains("`claimed_at`"));
        assertTrue(sql.contains("alter table `t_outbox_event`"));
    }

    @Test
    void communityContentEvolutionUsesANewForwardOnlyMigration() throws IOException {
        assertTrue(Files.isRegularFile(COMMUNITY_CONTENT));
        String sql = Files.readString(COMMUNITY_CONTENT).toLowerCase();
        assertTrue(sql.contains("alter table `t_forum_comment`"));
        assertTrue(sql.contains("alter table `t_solution`"));
        assertTrue(sql.contains("updated_at"));
        assertTrue(sql.contains("idx_forum_post_public_feed"));
        assertTrue(sql.contains("insert into `t_problem_version`"));
        assertTrue(sql.contains("update `t_problem`"));
        assertTrue(sql.contains("`published_version_id`"));
    }

    @Test
    void judgeResultReceiptsUseAForwardOnlyMigration() throws IOException {
        assertTrue(Files.isRegularFile(JUDGE_RESULTS));
        String sql = Files.readString(JUDGE_RESULTS).toLowerCase();
        assertTrue(sql.contains("create table `t_judge_result_receipt`"));
        assertTrue(sql.contains("`result_id`"));
        assertTrue(sql.contains("`payload_sha256`"));
        assertTrue(sql.contains("insert into `t_judge_attempt`"));
    }
}
