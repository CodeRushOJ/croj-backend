# Global Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver anonymous global announcement reads and a complete, audited administrator publication lifecycle.

**Architecture:** A focused `announcement` domain owns lifecycle policy and JDBC persistence. Thin public/admin controllers expose the domain through the existing `Result` envelope and Spring Security. Effective lifecycle is derived from persisted lifecycle plus a captured UTC instant, avoiding cron-driven correctness.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC/Security/Validation, JdbcTemplate, Flyway MySQL, JUnit 5, Mockito, MockMvc, H2 MySQL mode.

---

### Task 1: Schema contract

**Files:**
- Create: `src/main/resources/db/migration/V6__global_announcements.sql`
- Modify: `src/test/java/com/zephyr/croj/database/MigrationContractTest.java`

- [ ] Write a failing migration contract asserting the table, lifecycle/window fields, audit fields, optimistic version, and public/admin indexes.
- [ ] Run `./mvnw -q -Dtest=MigrationContractTest test` and verify failure reports missing V6.
- [ ] Add the forward-only MySQL migration with UTC-compatible `DATETIME(3)` values and constraints represented in service policy.
- [ ] Re-run the migration contract and verify it passes.

### Task 2: Lifecycle domain and repository

**Files:**
- Create: `src/main/java/com/zephyr/croj/announcement/AnnouncementLifecycle.java`
- Create: `src/main/java/com/zephyr/croj/announcement/AnnouncementApiException.java`
- Create: `src/main/java/com/zephyr/croj/announcement/AnnouncementRequests.java`
- Create: `src/main/java/com/zephyr/croj/announcement/AnnouncementRepository.java`
- Create: `src/main/java/com/zephyr/croj/announcement/AnnouncementService.java`
- Create: `src/main/java/com/zephyr/croj/config/ClockConfiguration.java`
- Create: `src/test/java/com/zephyr/croj/announcement/AnnouncementServiceTest.java`

- [ ] Write failing fixed-clock tests for draft creation, invalid windows, scheduling, immediate publication, withdrawal, archival, effective expiry, page bounds, not-found, and optimistic conflicts.
- [ ] Run `./mvnw -q -Dtest=AnnouncementServiceTest test` and verify compilation/failing behavior is caused by the absent domain.
- [ ] Add request records, lifecycle derivation, HTTP-aware domain errors, UTC clock, and the minimal service/repository methods required by the tests.
- [ ] Re-run the service tests and refactor only while green.

### Task 3: HTTP API and authorization

**Files:**
- Create: `src/main/java/com/zephyr/croj/controller/AnnouncementController.java`
- Create: `src/main/java/com/zephyr/croj/controller/AdminAnnouncementController.java`
- Modify: `src/main/java/com/zephyr/croj/config/SecurityConfig.java`
- Modify: `src/main/java/com/zephyr/croj/common/exception/GlobalExceptionHandler.java`
- Create: `src/test/java/com/zephyr/croj/announcement/AnnouncementApiSecurityIntegrationTest.java`

- [ ] Write failing MockMvc tests proving anonymous public reads, authenticated non-admin denial, admin success, bounded inputs, body validation, and 404/422/409 response mapping.
- [ ] Run the focused test and verify the new endpoints are absent.
- [ ] Add thin controllers, public GET matcher, method authorization, validated request parameters, and exception mapping.
- [ ] Re-run focused tests until green.

### Task 4: Persistence integration, API docs, and release record

**Files:**
- Create: `src/test/java/com/zephyr/croj/announcement/AnnouncementPersistenceIntegrationTest.java`
- Create: `docs/api/announcements.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] Write a failing H2 JDBC integration test for active-window filtering, stable pin ordering, detail visibility, admin status filtering, and CAS updates.
- [ ] Run the focused persistence test and verify it exposes missing/incorrect SQL behavior.
- [ ] Complete portable SQL mappings and keep service tests green.
- [ ] Document every route, lifecycle, UTC rule, validation limit, example request, and response semantics; update README and CHANGELOG.
- [ ] Run `./mvnw test`, fix regressions, and record the exact test count.

### Task 5: Independent review and publication

**Files:** all branch changes.

- [ ] Review the diff against Issue #19 and this design for Critical/Important correctness, security, concurrency, migration, and compatibility issues.
- [ ] Add a failing regression test for each valid Critical/Important finding, implement the fix, and rerun focused plus full tests.
- [ ] Commit intentionally, push `codex/announcements-api`, open a Draft PR based on `codex/contest-core-api`, and post tested scope/PR linkage to Issue #19.
