# Backend v1 Release Blockers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all backend v1 release blockers with fail-closed public snapshots and TestBundle contracts.

**Architecture:** Public and administrative DTOs are physically separated. Immutable problem versions own every public projection, while publication is the single aggregate transition point. V11 preserves uncertain history and marks it incomplete instead of copying mutable draft state.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Jackson, Flyway, MySQL 8.4, JUnit 5, Mockito, H2, Bash.

---

### Task 1: Public DTO and OpenAPI isolation

**Files:**
- Modify: `src/main/java/com/zephyr/croj/model/vo/ProblemVO.java`
- Create: `src/main/java/com/zephyr/croj/model/vo/AdminProblemVersionSourceVO.java`
- Create: `src/main/java/com/zephyr/croj/problem/AdminProblemVersionSourceService.java`
- Create: `src/main/java/com/zephyr/croj/controller/AdminProblemVersionSourceController.java`
- Modify: `src/main/java/com/zephyr/croj/config/SwaggerConfig.java`
- Modify: `src/main/java/com/zephyr/croj/controller/ProblemController.java`
- Test: `src/test/java/com/zephyr/croj/problem/PublicProblemContractTest.java`

- [ ] Write serialization and OpenAPI tests proving public responses have no checker source and anonymous operations have no Bearer requirement.
- [ ] Run the focused test and record the expected field/security failures.
- [ ] Remove the public source field, add the protected admin DTO/endpoint, and make security declarations operation-accurate.
- [ ] Run focused and related security tests to GREEN.

### Task 2: TestBundle v1 version compatibility

**Files:**
- Create: `src/main/java/com/zephyr/croj/problem/TestBundleV1Contract.java`
- Modify: `src/main/java/com/zephyr/croj/problem/TestBundleService.java`
- Modify: `src/main/java/com/zephyr/croj/problem/ProblemVersionPublicationService.java`
- Modify: `src/main/java/com/zephyr/croj/problem/AdminTestBundleService.java`
- Test: `src/test/java/com/zephyr/croj/problem/TestBundleServiceTest.java`
- Test: `src/test/java/com/zephyr/croj/problem/ProblemVersionPublicationIntegrationTest.java`
- Test: `src/test/java/com/zephyr/croj/problem/AdminTestBundleServiceIntegrationTest.java`

- [ ] Add focused OI, SPJ, checker mismatch, incomplete projection, and manual-row publication tests.
- [ ] Run them and record expected RED failures.
- [ ] Centralize compatibility validation and call it from attach and publish.
- [ ] Run focused attach/publish/import tests to GREEN.

### Task 3: Immutable version tag projections

**Files:**
- Modify: `src/main/resources/db/migration/V11__complete_problem_version_projection.sql`
- Modify: `src/main/java/com/zephyr/croj/model/entity/ProblemVersion.java`
- Modify: `src/main/java/com/zephyr/croj/service/impl/ProblemServiceImpl.java`
- Modify: `src/main/java/com/zephyr/croj/problem/ProblemVersionPublicationService.java`
- Modify: `src/main/resources/mapper/ProblemMapper.xml`
- Test: `src/test/java/com/zephyr/croj/community/ProblemVersionPublishingTest.java`

- [ ] Add tests proving draft tag edits cannot change public detail, list, or filter state and publication switches tags atomically.
- [ ] Run focused tests and record expected RED failures.
- [ ] Snapshot tags on version creation and read/filter only the published projection for public callers.
- [ ] Replace visible relations from the version snapshot inside publication after problem→version locking.
- [ ] Run focused tests to GREEN.

### Task 4: Explicit batch tag projection

**Files:**
- Create: `src/main/java/com/zephyr/croj/model/projection/ProblemTagProjection.java`
- Modify: `src/main/java/com/zephyr/croj/mapper/ProblemTagMapper.java`
- Modify: `src/main/resources/mapper/ProblemTagMapper.xml`
- Modify: `src/main/java/com/zephyr/croj/service/ProblemTagService.java`
- Modify: `src/main/java/com/zephyr/croj/service/impl/ProblemTagServiceImpl.java`
- Test: `src/test/java/com/zephyr/croj/community/ProblemVersionPublishingTest.java`

- [ ] Add a test with problem IDs different from tag IDs and record the incorrect grouping RED.
- [ ] Return explicit `{problemId,tagId,name,color}` rows and group by `problemId`.
- [ ] Run focused tests to GREEN.

### Task 5: V11 fail-closed historical upgrade

**Files:**
- Modify: `src/main/resources/db/migration/V11__complete_problem_version_projection.sql`
- Modify: `scripts/verify-mysql-migrations.sh`
- Modify: `src/test/java/com/zephyr/croj/database/MigrationContractTest.java`

- [ ] Change contract tests to reject mutable aggregate backfill and require completeness metadata.
- [ ] Run the Java migration contract test and record RED.
- [ ] Add multi-version MySQL fixtures proving rows/JSON are preserved and unsafe public pointers are cleared.
- [ ] Add a complete new draft and prove it can be published after the upgrade.
- [ ] Run the real MySQL 8.4 gate to GREEN.

### Task 6: Documentation and release verification

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/api/test-bundles.md`
- Create: `docs/migrations/V11-problem-version-projections.md`

- [ ] Document public/admin contracts, v1 incompatibility errors, immutable tags, and V11 recovery.
- [ ] Run focused suites, full Maven tests, MySQL gate, shellcheck, actionlint, and `git diff --check`.
- [ ] Request code review and resolve all critical/important findings.
- [ ] Commit clear changes and push `codex/backend-v1-release-clean`.
