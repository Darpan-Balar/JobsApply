# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and later React + TypeScript. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-14)
- Phases 1–4 are merged and pushed (`master` @ e3335f4, GitHub Darpan-Balar/JobsApply, public repo).
- **Phase 5** (application preparation) is complete on the local branch `phase-5-preparation`. It is committed locally only, **not pushed or merged**, and waiting for the user's review.
- `./mvnw clean install`: 327 tests, 0 failures. The local Compose database is at V10.
- The user's personal data is not yet entered (candidate profile, master resume, real employers).

## Decisions made with the user
- Personal data is strictly private per user, with login (Phase 8). Employers, sources and postings form a shared catalog.
- 13 application states. A single user today; onboarding another is quick.
- Work on a branch; merge and push only after review.

## Next action
The user reviews Phase 5. Once approved: merge and push. Then Phase 6: workflow and safety. That covers:
- submission approvals bound to the package hash (single-use)
- application attempts with idempotency keys
- the SUBMITTING → SUBMISSION_UNCONFIRMED crash recovery
- manual verification of unconfirmed submissions
- an audit listener (domain events through Spring Modulith's event publication registry)
- concurrency tests for duplicate approvals
- the remaining application-state REST endpoints
