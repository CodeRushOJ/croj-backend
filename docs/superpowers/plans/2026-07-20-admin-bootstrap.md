# First Super Administrator Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an auditable, idempotent one-shot command that creates the first super administrator without shipping a default password.

**Architecture:** The production image dispatches to a small non-web command only when `CROJ_MODE=bootstrap-admin`. Flyway creates a singleton guard row; the command locks and permanently claims it with the first administrator ID and identity in the same transaction as BCrypt insertion and audit creation, so concurrent Jobs create exactly one account. The long-running Backend Deployment never receives bootstrap credentials.

**Tech Stack:** Java 17, Spring JDBC transactions, Flyway, BCrypt, MySQL 8.4, H2 concurrency tests, Kubernetes Job supplied by `croj-platform`.

---

### Task 1: Prove the database and concurrency contract

**Files:**
- Create: `src/test/java/com/zephyr/croj/bootstrap/AdminBootstrapServiceTest.java`
- Modify: `src/test/java/com/zephyr/croj/database/MigrationContractTest.java`
- Create: `src/main/resources/db/migration/V9__admin_bootstrap_guard.sql`

- [x] Write failing tests for create, exact replay, username/email conflict, eight concurrent callers, BCrypt, and audit insertion.
- [x] Run `./mvnw -Dtest=AdminBootstrapServiceTest,MigrationContractTest test` and capture the missing-type/migration RED result.
- [x] Add the singleton `first-super-admin` guard row in a forward-only V9 migration.
- [x] Run the focused tests and require 15 tests with zero failure, error, or skip.

### Task 2: Implement the transaction service

**Files:**
- Create: `src/main/java/com/zephyr/croj/bootstrap/AdminBootstrapRequest.java`
- Create: `src/main/java/com/zephyr/croj/bootstrap/AdminBootstrapResult.java`
- Create: `src/main/java/com/zephyr/croj/bootstrap/AdminBootstrapConflictException.java`
- Create: `src/main/java/com/zephyr/croj/bootstrap/AdminBootstrapService.java`

- [x] Validate username, email, at least 12 Unicode code points, and the BCrypt limit of 72 UTF-8 bytes before opening the transaction.
- [x] Lock `t_system_bootstrap_lock` before reading identities, then create one active, verified role-2 user and one audit record.
- [x] Return `ALREADY_PRESENT` only for the exact active super-admin identity; never rotate credentials during replay.
- [x] Fail closed for every partial/conflicting identity.

### Task 3: Add the redacted one-shot image mode

**Files:**
- Create: `src/test/java/com/zephyr/croj/bootstrap/AdminBootstrapCommandTest.java`
- Create: `src/main/java/com/zephyr/croj/bootstrap/AdminBootstrapExecutor.java`
- Create: `src/main/java/com/zephyr/croj/bootstrap/AdminBootstrapCommand.java`
- Modify: `src/main/java/com/zephyr/croj/CrojApplication.java`

- [x] Write failing dispatch, missing-config, successful-output, conflict, and secret-redaction tests.
- [x] Make exact `CROJ_MODE=bootstrap-admin` dispatch before the Spring web application starts.
- [x] Run Flyway and the transaction service against `DATABASE_URL`, returning distinct success/configuration/failure exit codes.
- [x] Keep all output free of username, email, database password, and administrator password.

### Task 4: Publish the operator contract and validate production behavior

**Files:**
- Create: `docs/operations/admin-bootstrap.md`
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [x] Document Secret-only inputs, idempotency, conflict behavior, cleanup, and local one-shot execution.
- [x] Run V1–V9 and the command twice against disposable MySQL 8.4; require `created`, then `already present`, one role-2 row, one audit row, and unchanged password hash.
- [x] Run the initial complete Maven suite (170 tests, zero failures/errors/skips).
- [x] Request independent specification and quality review before closing its findings in Task 5.

### Task 5: Close independent review findings

**Files:**
- Modify: `src/main/resources/db/migration/V9__admin_bootstrap_guard.sql`
- Modify: `src/main/java/com/zephyr/croj/bootstrap/*.java`
- Modify: `src/test/java/com/zephyr/croj/bootstrap/*.java`
- Create: `src/test/java/com/zephyr/croj/database/AdminBootstrapIntegrationContractTest.java`
- Create: `tests/integration/admin-bootstrap-mysql84.sh`
- Modify: `.github/workflows/image.yml`
- Modify: operator documentation

- [x] Persist the first administrator ID and identity in the locked guard; reject sequential and concurrent different identities.
- [x] Enforce BCrypt's 72 UTF-8 byte ceiling while retaining a 12 Unicode-character minimum.
- [x] Redact the request representation and reject Connector/J descriptors, userinfo, nested properties, and all non-allowlisted URL parameters.
- [x] Cover ordinary, disabled, and soft-deleted conflicts without credential or privilege mutation.
- [x] Add a production-command MySQL 8.4 gate for V1–V9, replay, conflict, concurrency, hash stability, and output redaction.
- [x] Re-run the complete suite (187 tests), the production-command MySQL 8.4 gate including the legacy-database case, and `git diff --check` after final review fixes.
- [x] Obtain final independent re-review, then commit without publishing.
