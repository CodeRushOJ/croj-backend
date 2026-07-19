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

Limits are configured and validated before upload. The manifest must be a JSON object with a non-empty `cases` array, unique positive case IDs, safe relative input/output paths, and declared aggregate uncompressed bytes within the configured limit. Package parsers remain responsible for ZIP entry-count, compression-ratio, traversal, DTD and XXE defenses before they call this boundary.

## Publication invariant

`t_problem.published_version_id = v` implies:

1. `t_problem_version(v).state = PUBLISHED`;
2. exactly one `t_test_bundle.problem_version_id = v` exists;
3. the stored object key is content-addressed by the recorded SHA-256.

Submission and contest publication continue to enforce this invariant through their existing judge-ready queries.

