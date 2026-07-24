# TestBundle storage implementation plan

1. Add red tests for archive/manifest limits, deterministic object keys and publish-without-bundle rejection.
2. Add immutable `TestBundle` metadata model, mapper and configuration properties.
3. Add the S3-compatible private storage port and AWS SDK v2 adapter.
4. Implement attach and publication services with transactional locking and idempotency.
5. Change ordinary problem snapshots to remain draft until the publication gate is called.
6. Document S3/MinIO environment variables and import preflight/commit integration.
7. Run focused tests, full Maven tests and migration contracts; commit and open a stacked draft PR against Contest Core.
