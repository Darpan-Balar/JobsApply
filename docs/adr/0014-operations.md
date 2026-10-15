# ADR-0014: Operations: outbox, schedules, retention, backups, export, health

- Status: Accepted (2026-10-15)
- Date: 2026-10-15
- Supersedes: the synchronous audit recording of ADR-0011

## Context
The platform has to run unattended: discover jobs on a schedule, keep its own tables tidy, survive crashes without losing the audit trail, and be recoverable. Each user also needs to be able to take their data with them. ADR-0011 wrote audit entries synchronously, which meant an audit failure failed the business operation; that was deferred to this phase.

## Decision

**Audit through the event publication registry (Spring Modulith 2.1, V14):**
- Domain events are stored in `event_publication` in the transaction that changes the data.
- They are delivered after commit by `@ApplicationModuleListener` (asynchronous, in a new transaction).
- Undelivered events are re-delivered on restart (`republish-outstanding-events-on-restart`) or on request (`IncompleteEventPublications`).
- Because re-delivery is possible, `AuditRecorder` is idempotent: an identical entry is never written twice.
- Events carry their own actor and contain no personal data, so stored events are safe.
- The table DDL is Modulith's own v2 PostgreSQL schema, owned by Flyway.

**Scheduled jobs (`operations` module):**
- Sync of every active binding, three times a day by default.
- Retention daily: finished sync runs older than 90 days, delivered events older than 7 days.
- Each job runs under a PostgreSQL session advisory lock (`ClusterLock`): an overlapping tick or a second instance skips instead of running twice. Per-binding single-flight in the database (ADR-0008) still applies.
- Retention never deletes running syncs, undelivered events, audit entries or personal data.
- Administrators can run both jobs now (`POST /api/v1/admin/system/sync|retention`), which takes the same lock.

**Backups (`scripts/`):**
- `backup.sh` takes `pg_dump -Fc` of the database plus a tarball of the encrypted document store. It restores the dump into a scratch database at once, and records that copy's per-table row counts, the schema version and SHA-256 checksums in the manifest. A backup that can't be restored fails when it's taken.
- `verify-backup.sh` repeats the checks later.
- `restore.sh` only restores into a new database and a new directory. Switching over is a documented manual step with the app stopped, so a restore can't destroy current data.
- Backups never contain the encryption key: it must be kept separately, or the backup is unreadable.
- `BackupRestoreIT` runs the real scripts against PostgreSQL.

**Data export:**
- `GET /api/v1/me/export` returns everything personal (account, profiles with snapshots and versions, master-resume metadata, applications with packages, approvals and attempts, tracking, preferences, audit) as one JSON document.
- It is built only through owner-scoped services, sent with `no-store`, and never stored.
- File bytes (documents, master resumes) are listed with checksums and downloaded separately.

**Health and metrics:**
- Indicators for the encryption key (a round trip), the document store (writable), and the delivery backlog (WARNING after 10 minutes, still HTTP 200 because the platform keeps working).
- Details only for ADMIN.
- Counters: `jobs.application.transitions`, `jobs.submission.outcomes`, `jobs.auth.signins`, `jobs.scheduled.runs`, and the existing `jobs.sync.*`. `/actuator/metrics` is ADMIN-only.

**Hardening:**
- graceful shutdown with a 30-second phase timeout
- Hikari pool limits (10 connections, 10-second connection timeout)

## Tradeoffs
+ A failing audit write no longer fails the user's action, and no audit entry is lost: it waits and is retried.
+ Backups are proven restorable when they're taken. Restores can't overwrite anything.
− The audit log is eventually consistent: an entry appears shortly after the change, not atomically with it.
− Advisory locks and in-memory sessions still assume a single database. Prometheus scraping isn't set up; metrics are read from the admin endpoint.
− Backups use `docker exec` into the database container, which matches the Compose setup. Other deployments need the same commands run against their own PostgreSQL.
