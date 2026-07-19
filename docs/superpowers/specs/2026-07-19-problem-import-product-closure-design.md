# Problem Import Product Closure Design

## Goal

Close the administrator path from a real FPS upload to judge-ready published problems without weakening the existing immutable-version, private-object-storage, or Kubernetes multi-replica contracts. The external HTTP contract remains `POST /api/v1/admin/problem-imports/preflight` followed by idempotent `POST /api/v1/admin/problem-imports/{jobId}/commit`; frontend request helpers omit the `/api` servlet context from their relative route.

## Decisions

### Persistent two-step import jobs

Preflight parses and validates the upload, stores the original package in the existing private S3/MinIO bucket, and records a V8 `t_problem_import_job` row containing only lifecycle, ownership, digest, object key, format, expiry, and presentation-safe summary JSON. Hidden test contents never enter MySQL. A job is bound to the authenticated administrator and expires after 24 hours.

Commit locks the job row. `COMMITTED` returns the prior count, making retries idempotent; `VALIDATED` downloads the staged object, verifies its SHA-256, reparses it, creates every problem and draft version, attaches a verified test bundle, publishes atomically, and marks the job committed. A failed database transaction leaves the job retryable. Content-addressed S3 objects left by a rolled-back transaction are unreachable and may be garbage-collected later.

This database-backed job boundary works behind Kubernetes Service load balancing; an in-memory job map is rejected because preflight and commit may reach different pods.

### Parser registration and package detection

Spring registers `ProblemImportLimits`, `FpsProblemPackageParser`, and `ProblemPackageParserRegistry` explicitly. Raw XML is detected by content and parsed as FPS. ZIP uploads are treated as a transport archive: the archive validator requires exactly one supported XML problem-set entry plus optional referenced resources, rejects unsafe or duplicate paths, and applies entry-count, per-entry, total-uncompressed-size, and compression-ratio limits before selecting the XML. Unsupported packages fail closed with a validation response and cannot be committed.

### TestBundle trust boundary

`TestBundleService` no longer trusts the client manifest. It streams every ZIP entry, rejects encrypted/unsafe/directory/duplicate or undeclared entries, bounds entry count, compression ratio and actual uncompressed bytes, computes each case input/output byte count from the archive, and requires exact agreement with the manifest. Only a verified ZIP can create `t_test_bundle` metadata. Tests use real ZIP fixtures and cover plain bytes, traversal, duplicates, undeclared entries, size mismatches, and valid bundles.

### Publication and edits

Creating a problem produces a private draft. Editing an already published problem creates a new draft snapshot but preserves the existing `status=PUBLIC` and `published_version_id`; public readers continue seeing the stable version until the new draft has a TestBundle and `ProblemVersionPublicationService` atomically switches the pointer. Editing a never-published problem remains private. A regression test covers both cases.

### CI ordering

GitHub Actions adds a Java 17 Maven test job and makes image construction depend on it. The Dockerfile may skip tests because the workflow has already run the full suite, but no image or scan job can run after a failed Java test. Surefire reports are uploaded on failure. The MySQL 8.4 migration workflow is integrated separately; V1-V7 remain byte-identical and V8 is the only new migration.

## API responses

Preflight returns the frontend fields `jobId`, `detectedFormat`, `sha256`, `problemCount`, `testCaseCount`, `errors`, `warnings`, and `problems`. Each problem contains `sourceId`, `title`, `testCaseCount`, `status`, `errors`, and `warnings`. A clean response has no global or per-problem errors. Commit returns `jobId`, `status=COMMITTED`, and `importedCount`.

Both endpoints require `ADMIN` or `SUPER_ADMIN`. Missing/foreign/expired jobs return 404, invalid lifecycle or digest mismatch returns 409/422, invalid multipart or package content returns 400, and storage failures remain retryable server errors without reporting a false success.

## Verification

TDD covers controller authorization and multipart contract, persistent job idempotency, parser bean wiring, FPS XML/ZIP detection, TestBundle archive validation, published-edit continuity, and commit rollback. The final gate is the complete Java 17 suite, Java 17 package, unique V1-V8 migration contract, ShellCheck, production container contract, `git diff --check`, and independent Critical/Important review.
