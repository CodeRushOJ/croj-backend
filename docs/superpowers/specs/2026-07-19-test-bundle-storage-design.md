# TestBundle object storage and publication gate design

Issue: #21

## Goal

Make every published `ProblemVersion` judge-ready by requiring an immutable hidden-test bundle stored in a private S3-compatible bucket. The database stores only integrity metadata and the content-addressed object key.

## Boundaries

- `TestBundleStorage` owns private object writes. The production adapter uses AWS SDK v2 and supports AWS S3 or MinIO path-style endpoints.
- `TestBundleService` validates archive size, SHA-256 and manifest shape, uploads the immutable object, then records one `t_test_bundle` row per version.
- `ProblemVersionPublicationService` locks the version and bundle rows and atomically marks the version published, updates `t_problem.published_version_id`, and makes the problem public.
- Import adapters produce normalized drafts and archives. They must not publish directly.

## Integrity and failure model

Object keys are `test-bundles/{problemId}/{versionId}/{sha256}.zip`. Re-uploading the same bytes is safe. A database failure after S3 succeeds may leave an unreachable content-addressed object for a later garbage collector; it cannot expose hidden tests or publish an unusable version. Buckets remain private and no public/presigned URL is returned by these APIs.

Limits are configured and validated before upload. The manifest follows the shared
Judging Server v1 schema (`schemaVersion=1`, `judgeMode=ACM`,
`checker=exact|token`, immutable execution limits, string case IDs, and unit
ACM weights). Manifest limits must equal the bound `ProblemVersion` snapshot. The archive must
contain the same normalized JSON as root `manifest.json` plus exactly the
referenced case files. The service uses the same ZIP central-directory view as
Judging instead of a local-header-only stream, and rejects encrypted, symlink,
non-regular, unsupported and truncated entries,
requires strict UTF-8 case content, and enforces per-entry compression ratio plus
manifest, per-case and aggregate uncompressed limits at the final storage
boundary. A bundle contains at most 256 cases and at most 63 MiB uncompressed so
the Judging Server's 64 MiB batch request retains room for source and protocol
overhead. Package parsers additionally enforce their source-format DTD and XXE
limits.

## Publication invariant

`t_problem.published_version_id = v` implies:

1. `t_problem_version(v).state = PUBLISHED`;
2. exactly one `t_test_bundle.problem_version_id = v` exists;
3. the stored object key is content-addressed by the recorded SHA-256.

Submission and contest publication continue to enforce this invariant through their existing judge-ready queries.
