# Problem Import Product Closure Implementation Plan

**Goal:** Make the administrator FPS preflight/commit flow create judge-ready published problems safely behind multi-replica Kubernetes Services.

**Architecture:** Persist import job metadata in V8 and stage raw packages in private S3/MinIO. Reparse on commit, build deterministic test ZIPs, validate archives at the TestBundle trust boundary, and atomically publish. Preserve the existing published version during edits and gate image CI on Java tests.

**Tech Stack:** Java 17, Spring Boot, MyBatis/JdbcTemplate, Flyway/MySQL 8.4, AWS SDK S3, StAX, ZIP streams, JUnit 5, MockMvc, GitHub Actions.

### Task 1: Preserve published versions during edits

- [x] Add a failing service test showing an edit preserves `status` and `publishedVersionId` when an old version is public.
- [x] Change update behavior only for already-published problems; keep never-published problems private.
- [x] Run the focused problem-version tests.

### Task 2: Enforce real TestBundle ZIP validation

- [x] Replace string-byte fixtures with deterministic real ZIP fixtures.
- [x] Add failing tests for non-ZIP bytes, traversal, undeclared entries, manifest-size mismatch, limits, and a valid archive.
- [x] Implement streaming archive validation with bounded actual uncompressed bytes and compression ratio.
- [x] Run focused TestBundle storage/publication tests.

### Task 3: Persist import jobs and private staging objects

- [x] Add V8 migration contract tests before the migration.
- [x] Add `t_problem_import_job` V8 with ownership, lifecycle, digest, private object key, summary, expiry, and idempotent commit fields.
- [x] Extend private object storage with staging put/get behavior and focused S3 adapter tests.
- [x] Add repository/job lifecycle tests for owner binding, expiry, and commit idempotency.

### Task 4: Wire parsers and implement preflight/commit

- [x] Add failing Spring wiring tests for import limits, FPS parser, and parser registry beans.
- [x] Add package detector tests for XML, safe single-FPS ZIP, unsafe paths, duplicates, excess expansion, and unsupported content.
- [x] Add MockMvc authorization and multipart/response-contract tests for both admin endpoints.
- [x] Implement preflight summary generation and persistent staging.
- [x] Implement commit revalidation, DTO mapping, deterministic per-problem TestBundle creation, atomic publish, and idempotent response.
- [x] Run focused import controller/service/parser integration tests.

### Task 5: Gate images on Java tests

- [x] Add a workflow contract test or static assertion proving the image job depends on Java 17 Maven tests.
- [x] Add the test job and failure report upload without starting the application.
- [x] Run the repository container contract (`actionlint` is unavailable locally).

### Task 6: Verify, review, and publish

- [x] Update API/README/CHANGELOG with actual endpoint, V8, retry, and storage behavior.
- [x] Run the full Java 17 Maven suite and package; 156 tests passed together and 2 repository lifecycle tests passed in a focused follow-up (158 total, zero failures).
- [x] Run V1-V8 on MySQL 8.4.10 plus ShellCheck, container, and `git diff --check` gates.
- [ ] Request a fresh independent Critical/Important review and resolve all findings.
- [ ] Push the single integration branch and open one Draft PR against `codex/discussion-resources`, linking #20 and #22.
