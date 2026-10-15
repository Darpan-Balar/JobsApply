# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and a React + TypeScript dashboard. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-15)
- Phases 1–7 are merged and pushed (`master` @ a01bc7c, GitHub Darpan-Balar/JobsApply, public repo).
- **Phase 8** (accounts, tracking, dashboard; ADR-0013) is complete on the local branch `phase-8-dashboard`. It is committed locally only, **not pushed or merged**, and waiting for the user's review.
- `./mvnw clean install`: 388 Java test cases and 18 Vitest tests, 0 failures.
- The local database is at V13. **The owner still has to create their account**: open http://127.0.0.1:8090, which runs the one-time setup and takes over the legacy `local` user. I deliberately did not do this.
- The user's personal data is not yet entered (candidate profile, master resume, real employers).

## Decisions made with the user
- Personal data is strictly private per user, behind sign-in (done in Phase 8). Employers, sources and postings form a shared catalog.
- 13 application states. A single user today; onboarding another is quick (Admin → Accounts).
- Work on a branch; merge (fast-forward) and push only after the user says "merge and push".

## Next action
The user reviews Phase 8. Once approved: merge and push. Then **Phase 9**:
- scheduled sync with single-flight locking
- backups and export (pg_dump plus a per-user JSON export) with a verified restore
- metrics and monitoring, retention, hardening
- the Spring Modulith event publication registry (outbox) for audit
