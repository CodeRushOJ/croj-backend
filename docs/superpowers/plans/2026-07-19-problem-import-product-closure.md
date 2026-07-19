# Problem Import Product Closure Implementation Plan

**Goal:** Make the administrator FPS preflight/commit flow create judge-ready published problems safely behind multi-replica Kubernetes Services.

**Architecture:** Persist import job metadata in V8 and stage raw packages in private S3/MinIO. Reparse on commit, build deterministic test ZIPs, validate archives at the TestBundle trust boundary, and atomically publish. Preserve the existing published version during edits and gate image CI on Java tests.

**Tech Stack:** Java 17, Spring Boot, MyBatis/JdbcTemplate, Flyway/MySQL 8.4, AWS SDK S3, StAX, ZIP streams, JUnit 5, MockMvc, GitHub Actions.

### Task 1: Preserve published versions during edits

- [ ] Add a failing service test showing an edit preserves `status` and `publishedVersionId` when an old version is public.
- [ ] Change update behavior only for already-published problems; keep never-published problems private.
- [ ] Run the focused problem-version tests.

### Task 2: Enforce real TestBundle ZIP validation

- [ ] Replace string-byte fixtures with deterministic real ZIP fixtures.
- [ ] Add failing tests for non-ZIP bytes, traversal, duplicates, undeclared entries, manifest-size mismatch, too many entries, and a valid archive.
- [ ] Implement streaming archive validation with bounded actual uncompressed bytes and compression ratio.
- [ ] Run focused TestBundle storage/publication tests.

### Task 3: Persist import jobs and private staging objects

- [ ] Add V8 migration contract tests before the migration.
- [ ] Add `t_problem_import_job` V8 with ownership, lifecycle, digest, private object key, summary, expiry, and idempotent commit fields.
- [ ] Extend private object storage with staging put/get/delete behavior and focused S3 adapter tests.
- [ ] Add repository/job lifecycle tests for owner binding, expiry, commit idempotency, and retry after rollback.

### Task 4: Wire parsers and implement preflight/commit

- [ ] Add failing Spring wiring tests for import limits, FPS parser, and parser registry beans.
- [ ] Add package detector tests for XML, safe single-FPS ZIP, unsafe paths, duplicates, excess expansion, and unsupported content.
- [ ] Add MockMvc authorization and multipart/response-contract tests for both admin endpoints.
- [ ] Implement preflight summary generation and persistent staging.
- [ ] Implement commit revalidation, DTO mapping, deterministic per-problem TestBundle creation, atomic publish, and idempotent response.
- [ ] Run focused import controller/service/parser integration tests.

### Task 5: Gate images on Java tests

- [ ] Add a workflow contract test or static assertion proving the image job depends on Java 17 Maven tests.
- [ ] Add the test job and failure report upload without starting the application.
- [ ] Run actionlint when available and the repository container contract.

### Task 6: Verify, review, and publish

- [ ] Update API/README/CHANGELOG with actual endpoint, V8, retry, and storage behavior.
- [ ] Run the full Java 17 Maven suite and package; record the exact count.
- [ ] Run migration, ShellCheck, container, and `git diff --check` gates.
- [ ] Request a fresh independent Critical/Important review and resolve all findings.
- [ ] Push the single integration branch and open one Draft PR against `codex/discussion-resources`, linking #20 and #22.
