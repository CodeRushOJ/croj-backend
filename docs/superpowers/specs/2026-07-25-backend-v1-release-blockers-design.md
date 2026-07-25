# Backend v1 Release Blockers Design

## Goal

Close the six backend release blockers without weakening existing submission,
CORS, FPS import, or contest behavior.

## Public and administrative problem contracts

`ProblemVO` is a public response type and therefore cannot contain checker source
at all. The field is removed from the type rather than conditionally cleared.
Administrators read immutable version checker configuration through a separate
admin-only endpoint and DTO guarded by `ADMIN`/`SUPER_ADMIN`.

Public OpenAPI operations explicitly declare no security requirement. The global
Bearer requirement is removed; protected operations continue to opt in with the
existing named scheme.

## TestBundle v1 contract

A single validator checks the entire manifest and compares both sides of the
contract:

- the immutable `ProblemVersion` must be ACM (`judgeMode=0`), non-SPJ, and have
  complete positive execution limits;
- the manifest must be schema v1, `judgeMode=ACM`, and use only `exact` or
  `token`;
- the manifest must have the exact supported field set and contain bounded,
  unique cases with safe unique paths and ACM weight `1`;
- manifest limits must equal version limits.

Attach validates before storage. Publish validates the persisted bundle again so
manual database writes and older call paths cannot bypass the contract. Unsupported
OI/SPJ versions fail closed with a stable API conflict/unprocessable response.
The FPS import path remains ACM/exact and its preflight rejection of SPJ/interactor
resources remains intact.

## Immutable tag snapshots

Draft creation stores ordered `{id,name,color}` tag values in the immutable
`statement_json.tags` projection after validating the requested tag IDs. Published
read models use only the published version's tags. Public list filtering also
queries the published snapshot, while administrators retain draft-oriented
management behavior.

Publication locks `t_problem` before `t_problem_version`, then atomically marks
the version published, switches `published_version_id`, and replaces the mutable
relation table with the published snapshot. Thus draft tag edits cannot change
anonymous detail/list/search/filter behavior before publish.

Batch tag reads use an explicit projection
`{problemId,tagId,name,color}`. Grouping is by `problemId`; no entity field or
tag ID is overloaded as a problem ID.

## Safe V11 history handling

V11 never copies current `t_problem.source`, `difficulty`, or tags into arbitrary
historical versions. It adds a `projection_complete` marker. Existing versions
remain byte-for-byte unchanged and are marked incomplete unless their own JSON
passes the MySQL JSON Schema/type contract for every required public projection
field (including explicit source, difficulty, and tags) and has unique tag IDs.

If a problem points at an incomplete version, V11 clears
`published_version_id` and makes the aggregate private. Incomplete versions stay
available for audit and existing contest references, but TestBundle attach and
ordinary publication reject them. An administrator restores public visibility by
creating and publishing a new complete draft.

The Docker-backed MySQL 8.4 upgrade gate covers multiple versions with divergent
values and proves preservation, pointer invalidation, rejection, and recovery.

## Error and verification model

Malformed/unsupported uploaded bundles return the existing stable 422 contract;
publication of an incompatible or incomplete version returns the existing stable
409 contract. Public snapshot incompleteness never falls back to current draft
fields.

Each change is developed test-first. Focused RED/GREEN evidence is followed by
the full Maven suite, real MySQL migration gate, shellcheck, actionlint, and a
whitespace/diff check.
