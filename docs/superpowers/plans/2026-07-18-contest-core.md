# Contest Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the free ACM contest lifecycle, participation, content, contest submission validation, and freeze-aware scoreboard without changing judge protocol v1.

**Architecture:** Add a forward-only V5 schema and a focused contest module consisting of JDBC repositories, lifecycle/access services, an independently testable ACM scoreboard calculator, and public/admin controllers. MySQL remains the source of truth; scoreboard snapshots are discardable and keyed by cutoff plus a source version derived from terminal submission count, maximum update timestamp, and status aggregate.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Security, JdbcTemplate/MyBatis-Plus, MySQL 8.4, H2 MySQL mode, JUnit 5, Mockito, Flyway.

---

### Task 1: Track scope and lock the V5 contract

**Files:**
- Create: GitHub Issue in `CodeRushOJ/croj-backend`
- Create: `src/main/resources/db/migration/V5__contest_core.sql`
- Modify: `src/test/java/com/zephyr/croj/database/MigrationContractTest.java`

- [ ] Create a complete Contest Core issue covering lifecycle, visibility, ACM rules, migrations, tests, and judge v1 compatibility.
- [ ] Add failing migration assertions for the V5 filename, new tables, `t_submission.contest_id`, unique registration key, and scoreboard indexes.
- [ ] Run `./mvnw -q -Dtest=MigrationContractTest test` in the Java 17 container and confirm failure because V5 is absent.
- [ ] Implement V5 with lifecycle backfill:

```sql
ALTER TABLE t_contest
  ADD COLUMN description_markdown LONGTEXT,
  ADD COLUMN lifecycle VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN registration_opens_at DATETIME(3),
  ADD COLUMN registration_closes_at DATETIME(3),
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
UPDATE t_contest
SET registration_opens_at = created_at,
    registration_closes_at = starts_at
WHERE registration_opens_at IS NULL OR registration_closes_at IS NULL;
ALTER TABLE t_submission ADD COLUMN contest_id BIGINT NULL;
```

- [ ] Add registration, announcement, clarification, reply, and snapshot tables with explicit unique/query indexes.
- [ ] Re-run the migration contract test and commit the schema slice.

### Task 2: Implement lifecycle, validation, and access policy

**Files:**
- Create: `src/main/java/com/zephyr/croj/contest/ContestPhase.java`
- Create: `src/main/java/com/zephyr/croj/contest/ContestPolicy.java`
- Create: `src/main/java/com/zephyr/croj/contest/ContestApiException.java`
- Create: `src/test/java/com/zephyr/croj/contest/ContestPolicyTest.java`
- Modify: `src/main/java/com/zephyr/croj/common/exception/GlobalExceptionHandler.java`

- [ ] Write failing fixed-clock tests for DRAFT/CANCELLED/REGISTRATION/SCHEDULED/RUNNING/FROZEN/ENDED derivation and invalid registration/freeze windows.
- [ ] Run the focused test and verify missing policy types cause RED.
- [ ] Implement phase derivation without persisted RUNNING/ENDED:

```java
if (lifecycle == DRAFT) return DRAFT;
if (lifecycle == CANCELLED) return CANCELLED;
if (!now.isBefore(endsAt)) return ENDED;
if (freezeAt != null && !now.isBefore(freezeAt) && now.isBefore(endsAt)) return FROZEN;
if (!now.isBefore(startsAt)) return RUNNING;
if (!now.isBefore(registrationOpensAt) && now.isBefore(registrationClosesAt)) return REGISTRATION;
return SCHEDULED;
```

- [ ] Implement public/private visibility, self-registration, problem visibility, clarification, submission, and scoreboard-read policy methods.
- [ ] Map typed contest exceptions to 404/403/409/422 and run the policy tests GREEN.

### Task 3: Add contest persistence and participant operations

**Files:**
- Create: `src/main/java/com/zephyr/croj/contest/ContestRepository.java`
- Create: `src/main/java/com/zephyr/croj/contest/ContestService.java`
- Create: `src/main/java/com/zephyr/croj/model/dto/contest/ContestRequests.java`
- Create: `src/main/java/com/zephyr/croj/model/vo/contest/ContestViews.java`
- Create: `src/test/java/com/zephyr/croj/contest/ContestRegistrationIntegrationTest.java`

- [ ] Write H2 integration tests for public paging size cap 100, duplicate concurrent registration, cancellation before start, PRIVATE self-registration denial, and administrator-managed registration.
- [ ] Verify RED due to missing repository/service.
- [ ] Implement JDBC queries using parameter binding and database upsert/CAS:

```sql
INSERT INTO t_contest_registration(contest_id,user_id,status,registered_at,updated_at,managed_by)
VALUES (?,?, 'REGISTERED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), ?)
ON DUPLICATE KEY UPDATE
  status='REGISTERED', updated_at=CURRENT_TIMESTAMP(3), managed_by=VALUES(managed_by)
```

- [ ] Enforce PUBLIC self-service windows and PRIVATE admin-only management in the service, then make registration tests GREEN.

### Task 4: Build admin CRUD, problem arrangement, announcements, and clarifications

**Files:**
- Create: `src/main/java/com/zephyr/croj/controller/ContestController.java`
- Create: `src/main/java/com/zephyr/croj/controller/AdminContestController.java`
- Create: `src/main/java/com/zephyr/croj/contest/ContestAdminService.java`
- Create: `src/test/java/com/zephyr/croj/contest/ContestApiSecurityIntegrationTest.java`
- Create: `src/test/java/com/zephyr/croj/contest/ContestContentIntegrationTest.java`
- Modify: `src/main/java/com/zephyr/croj/config/SecurityConfig.java`

- [ ] Write API security tests proving published PUBLIC reads are anonymous, mutations need JWT, and admin endpoints require ADMIN/SUPER_ADMIN.
- [ ] Write content tests for pre-start problem secrecy, after-start registered access, post-end public access, private clarification visibility, and public official replies.
- [ ] Verify focused API tests RED before adding controllers.
- [ ] Implement public `/v1/contests/**` and admin `/v1/admin/contests/**` endpoints with `@Valid`, page cap, personal registration status, method authorization, draft-only update/arrangement, publish CAS, admin registration, announcements, questions, and replies.
- [ ] Run security/content tests GREEN.

### Task 5: Implement ACM scoreboard and discardable snapshots

**Files:**
- Create: `src/main/java/com/zephyr/croj/contest/AcmScoreboardCalculator.java`
- Create: `src/main/java/com/zephyr/croj/contest/ContestScoreboardService.java`
- Create: `src/test/java/com/zephyr/croj/contest/AcmScoreboardCalculatorTest.java`
- Create: `src/test/java/com/zephyr/croj/contest/ContestScoreboardIntegrationTest.java`

- [ ] Write pure unit tests for wrong-attempt penalty, ignored PENDING/SYSTEM_ERROR/post-AC submissions, deterministic first AC, rank ties, freeze cutoff, and automatic unfreeze after end.
- [ ] Verify calculator tests RED.
- [ ] Implement calculation from ordered submission facts; count statuses 2-6 only before first accepted status 1.
- [ ] Write H2 tests proving public scoreboard is denied in REGISTRATION/SCHEDULED, frozen public results exclude post-freeze submissions, admin live includes them, and OI returns explicit unsupported response.
- [ ] Derive snapshot source version from terminal row count, maximum `update_time`, sum of status and submission IDs; reject cache hits on any mismatch and rebuild from submissions.
- [ ] Run calculator and integration tests GREEN.

### Task 6: Bind submissions to contests without changing judge v1

**Files:**
- Modify: `src/main/java/com/zephyr/croj/model/dto/SubmissionDTO.java`
- Modify: `src/main/java/com/zephyr/croj/model/entity/Submission.java`
- Modify: `src/main/java/com/zephyr/croj/service/impl/SubmissionServiceImpl.java`
- Modify: `src/main/java/com/zephyr/croj/outbox/DatabaseSubmissionOutbox.java`
- Create: `src/test/java/com/zephyr/croj/contest/ContestSubmissionIntegrationTest.java`

- [ ] Write failing tests for unregistered, early, ended, and non-contest problem submissions plus a valid contest submission that pins `contest_id` and `problem_version_id`.
- [ ] Add nullable `contestId` to request/entity and validate membership, phase, registration, problem mapping server-side before save.
- [ ] Assert Outbox schema v1 remains exactly the documented fields and does not depend on `contest_id`.
- [ ] Run contest submission plus judge/outbox regression tests GREEN.

### Task 7: Documentation, MySQL, full regression, and publication

**Files:**
- Create: `docs/api/contests.md`
- Create: `CHANGELOG.md`
- Modify: `README.md`

- [ ] Document API paths, visibility matrix, lifecycle derivation, ACM/freeze rules, admin flows, and judge protocol boundary.
- [ ] Add a truthful changelog entry referencing the existing foundation/community/judge history and Contest Core issue.
- [ ] Run all Maven tests and record exact totals.
- [ ] Validate MySQL 8.4 clean V1→V5 and upgrade V1→V4→V5, including migrated contest windows, registration uniqueness, contest submission index, and snapshot table.
- [ ] Request cross-review focused on time leakage, score correctness, and transaction races; fix any blocking findings with focused tests first.
- [ ] Commit, push `codex/contest-core-api`, and open a Draft PR against `codex/judge-result-ingestion` with Issue and validation evidence.
