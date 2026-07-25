# V11 problem version projections

V11 closes a historical snapshot ambiguity without inventing history. Earlier
versions may lack `source`, tags, difficulty, or checker fields because those
values lived only on the mutable `t_problem` row. Copying the latest draft into
every old version would silently change what a published version meant.

## Migration behavior

V11 adds `t_problem_version.projection_complete` with a fail-closed default.
A version is marked complete only when its own JSON already contains every
public projection field with the required JSON type and value shape:

- statement: title, descriptions, hints, samples, explicit source, and a tags array;
- limits: time, memory, and total score;
- judge configuration: SPJ flag/source/language, mode, checker, and difficulty.

MySQL `JSON_SCHEMA_VALID` rejects null/wrongly typed required fields, malformed
samples and malformed tag objects. A `JSON_TABLE` uniqueness check also rejects
duplicate tag IDs.

The migration never calls `JSON_SET` and never reads mutable `t_problem.source`,
`difficulty`, or tag relations to rewrite a historical version. If a public
problem points to an incomplete version, V11 sets the problem to private and
clears `published_version_id`. The version row and all three JSON documents
remain available for audit; contest references are not deleted.

This is intentionally an availability tradeoff in favor of integrity. Public
reads never fall back from an incomplete version to current draft fields, and
TestBundle attach/publication rejects `projection_complete=0`.

## Audit and recovery

1. List the problem versions with an administrator JWT:

   ```http
   GET /api/v1/admin/problems/{problemId}/versions
   ```

2. Inspect the immutable checker configuration when needed:

   ```http
   GET /api/v1/admin/problems/{problemId}/versions/{versionId}/source
   ```

3. Review the historical JSON directly from a read-only database export if
   statement reconstruction is required. Do not update the old version or set
   `projection_complete=1` by hand.
4. Use the normal administrator problem edit flow to create a new draft with
   an explicit source, validated tags, execution limits, difficulty, and
   checker configuration.
5. Discover the new draft version ID, upload a valid TestBundle v1 with its
   strong ETag, and publish it:

   ```http
   GET  /api/v1/admin/problems/{problemId}/versions
   GET  /api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle
   PUT  /api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle
   POST /api/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle/publish
   ```

Publication locks the problem before the version, revalidates the persisted
manifest against the immutable version, marks the draft published, replaces
the visible tag relations from `statement_json.tags`, and switches the public
pointer in one transaction.

## Verification

Run the real MySQL 8.4 gate from the repository root:

```bash
scripts/verify-mysql-migrations.sh
```

The gate hashes all historical snapshot JSON before and after V11, distinguishes
a self-contained complete snapshot from uncertain history, rejects a
path-complete but wrongly typed snapshot, verifies unsafe pointers are cleared,
then publishes an audited complete replacement and confirms public visibility
and tag projection are restored.
