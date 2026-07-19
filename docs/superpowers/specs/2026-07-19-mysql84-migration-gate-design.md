# MySQL 8.4 Migration Gate Design

## Context

`MigrationContractTest` checks that expected SQL fragments exist, but it never asks MySQL to execute the migrations. That leaves syntax, engine behavior, historical upgrade, constraint enforcement, and index-shape regressions capable of passing CI. The forum resource migration is especially sensitive because it adds a non-null discriminator, nullable identifier, `CHECK` constraint, and composite feed index to a table that can already contain rows.

## Decision

Add one repository-owned Bash verifier that exercises the real upgrade path against an ephemeral, digest-pinned MySQL 8.4 container. The verifier will use Flyway's Maven plugin with the same Flyway version managed by Spring Boot, migrate a clean database to V6, insert a representative legacy forum post, and then migrate to V7.

After migration it will query MySQL directly from inside the database container and assert:

- Flyway recorded exactly V1 through V7 as successful versioned migrations.
- The legacy row was backfilled to `resource_type = 'GENERAL'` and `resource_id IS NULL`.
- `chk_forum_resource_ref` exists and is enforced.
- `idx_forum_resource_feed` has exactly the ordered columns `resource_type,resource_id,status,pinned,created_at`.
- A valid problem-associated post is accepted.
- An invalid `GENERAL` post with a non-null `resource_id` is rejected by MySQL.

The script owns container startup, readiness, credentials, port allocation, and cleanup. It exposes a small set of environment overrides for diagnosis, while secure throwaway defaults make the documented command simply `scripts/verify-mysql-migrations.sh`.

## Alternatives Considered

1. **JUnit/Testcontainers:** strong Java integration, but it adds test-only container dependencies and hides the operational SQL assertions behind a Java test. It also duplicates the CI/service lifecycle less transparently.
2. **GitHub Actions service plus inline SQL:** concise in CI, but difficult to reproduce locally and tends to duplicate orchestration between YAML and shell.
3. **Ephemeral MySQL container plus repository script (chosen):** one path locally and in CI, no host MySQL client, explicit cleanup, and easy shell/static validation.

## CI and Security

The workflow will run on pull requests that touch migrations, the verifier, Maven configuration, or the workflow itself. It will use `permissions: contents: read`, a job timeout, concurrency cancellation, pinned action commits, Maven dependency caching, and an immutable multi-architecture MySQL 8.4 image reference. Root credentials only bootstrap the disposable container; Flyway and assertions use a database-scoped non-root user created by the official MySQL entrypoint.

## Compatibility and Scope

Published migrations remain immutable. This change does not start the application or alter runtime database configuration. It verifies V1–V7 only and deliberately does not add production deployment behavior, seed data, or a general database test framework.

## Failure Behavior

The verifier fails fast with a named assertion, retains no database after exit, and prints the MySQL container logs when startup fails. Expected constraint rejection is captured explicitly so its diagnostic does not pollute successful CI output. Unexpected successful invalid writes fail the build.

