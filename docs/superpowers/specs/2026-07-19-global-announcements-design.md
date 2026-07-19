# Global Announcements Design

## Scope

Add a production-ready global announcement lifecycle without changing the existing contest announcement contract. Public clients can list currently visible global announcements, read one visible announcement, and request a bounded current-announcement feed. Administrators can create and edit drafts, schedule publication, publish immediately, withdraw to a draft, archive announcements, and inspect every lifecycle state with pagination.

The schema reserves `scope` and `contest_id` so a later migration can consolidate contest announcements. This increment only accepts `GLOBAL`; existing `/v1/contests/{id}/announcements` behavior remains intact.

## Lifecycle and time semantics

Persisted states are `DRAFT`, `SCHEDULED`, `PUBLISHED`, and `ARCHIVED`. `EXPIRED` is an effective read state when a scheduled or published announcement has `expires_at <= now`. A scheduled announcement becomes publicly visible without a scheduler when `publish_at <= now < expires_at`; its effective read state is `PUBLISHED`.

All API timestamps are ISO-8601 instants. Java uses `Instant` and a UTC `Clock`; MySQL stores millisecond-precision UTC values. Public visibility is determined entirely by the database query using the request's captured `now` value. A window is invalid when expiry is not strictly after publication.

## API

- `GET /v1/announcements?page=1&size=20`: visible global page.
- `GET /v1/announcements/current?limit=5`: bounded visible feed for navigation/homepage surfaces.
- `GET /v1/announcements/{id}`: visible global detail.
- `GET /v1/admin/announcements?page=1&size=20&status=`: complete administrative page with optional effective-state filter.
- `POST /v1/admin/announcements`: create a draft.
- `PUT /v1/admin/announcements/{id}`: update mutable content and pin ordering unless archived.
- `POST /v1/admin/announcements/{id}/schedule`: set a future publication window.
- `POST /v1/admin/announcements/{id}/publish`: publish immediately with optional expiry.
- `POST /v1/admin/announcements/{id}/withdraw`: return a scheduled/published item to draft and clear publication metadata.
- `POST /v1/admin/announcements/{id}/archive`: terminal archival transition.

Page size is capped at 100 and current-feed limit at 20. Public ordering is pinned first, ascending `pin_order`, then newest publication and id. Admin ordering is newest update and id.

## Data and concurrency

`t_announcement` stores scope, lifecycle, Markdown content, pin controls, publication window, creator/updater/publisher audit ids, timestamps, archive timestamp, and an optimistic `version`. Mutations use compare-and-set updates on lifecycle/version so concurrent administrative actions cannot silently overwrite each other.

## Security and errors

Public GET routes are anonymous. Every admin route is guarded by `ADMIN` or `SUPER_ADMIN` method authorization. Missing announcements return 404; invalid state transitions or publication windows return 422; stale writes return 409; validation failures return 400.

## Tests

Contract tests cover the forward-only Flyway migration, lifecycle/window rules with a fixed clock, stable public ordering and pagination through JDBC integration tests, controller validation and role enforcement, anonymous visibility, and documentation/security configuration. The full Maven suite remains the release gate.
