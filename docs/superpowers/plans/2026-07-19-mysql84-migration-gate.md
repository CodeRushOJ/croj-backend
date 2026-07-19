# MySQL 8.4 Migration Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reproducible CI gate proving that Flyway upgrades a real MySQL 8.4 database from V1–V6 data through V7 with the intended backfill, constraint, and index behavior.

**Architecture:** A repository script owns an ephemeral MySQL container, runs Flyway to target V6 and then V7, and executes assertions through the container's MySQL client. A focused GitHub Actions workflow runs that same script with an immutable MySQL image and cached Maven dependencies.

**Tech Stack:** Bash, ShellCheck, Docker, MySQL 8.4, Flyway Maven plugin, Maven Wrapper, GitHub Actions.

---

### Task 1: Add the executable migration verifier test

**Files:**
- Create: `scripts/verify-mysql-migrations.sh`

- [ ] Write a strict Bash script that creates an ephemeral database and waits for readiness.
- [ ] Run Flyway with `target=6`, insert a legacy `t_forum_post`, then migrate to V7.
- [ ] Assert Flyway history, legacy backfill, named `CHECK`, exact index order, valid resource insert, and rejected `GENERAL + resource_id`.
- [ ] Run the verifier before adding the Flyway plugin and record the expected Maven plugin-resolution failure as the TDD red state.
- [ ] Run `shellcheck scripts/verify-mysql-migrations.sh` and fix all findings.

### Task 2: Add the minimal Flyway Maven execution entry point

**Files:**
- Modify: `pom.xml`

- [ ] Add `flyway-maven-plugin` under `build.plugins` without independently overriding the Spring Boot-managed Flyway version.
- [ ] Include the MySQL Flyway database module and connector as plugin dependencies so command-line migration has the same database support as runtime Flyway.
- [ ] Re-run `scripts/verify-mysql-migrations.sh` and verify every real-database assertion passes.

### Task 3: Add the CI gate

**Files:**
- Create: `.github/workflows/mysql-migrations.yml`

- [ ] Trigger on migration, verifier, Maven, and workflow changes plus manual dispatch.
- [ ] Set read-only repository permissions, concurrency cancellation, a bounded job timeout, and pinned action revisions.
- [ ] Configure Java 17 with Maven caching and call the verifier with a digest-pinned MySQL 8.4 multi-platform image.
- [ ] Run ShellCheck before the integration test.

### Task 4: Document operation and release impact

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] Document prerequisites, the one-command local gate, what it proves, cleanup behavior, and optional image override.
- [ ] Record the real MySQL/Flyway migration gate in the unreleased changelog without claiming deployment behavior.

### Task 5: Verify, review, and publish

**Files:**
- Verify all changed files.

- [ ] Run ShellCheck, the real MySQL migration gate, focused migration contract tests, and the full Maven test suite.
- [ ] Inspect the diff for immutable migration changes; the check must report none under `src/main/resources/db/migration`.
- [ ] Request an independent spec/code review and resolve all Critical or Important findings.
- [ ] Commit intentionally, push `codex/mysql84-migration-gate`, and open a Draft PR with base `codex/discussion-resources`.

