# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and later React + TypeScript. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-14)
- Phases 1–3 are merged and pushed (`master` @ 46717d2, GitHub Darpan-Balar/JobsApply, public repo).
- **Phase 4** (candidates and matching) is complete on the local branch `phase-4-matching`. It is committed locally only, **not pushed or merged**, and waiting for the user's review.
- `./mvnw clean install`: 298 tests, 0 failures.
- The local Compose database is at V9. `.env` has `JOBS_ENCRYPTION_KEY`, generated locally and git-ignored; the user must back it up.
- No real candidate profile or employers exist yet. The user imports their own profile through the API.

## Decisions made with the user
- Personal data is strictly private per user, with login (Phase 8). Employers, sources and postings form a shared catalog.
- 13 application states. A single user today; onboarding another is quick.
- Work on a branch; merge and push only after review.

## Next action
The user reviews Phase 4. Once approved: merge and push. Then Phase 5: application preparation. That covers resume templates and content generation with claim traceability (every claim cites verified facts), a document renderer (HTML, then PDF), screening-answer drafts with ALWAYS_ASK_USER policies, application packages with validation warnings, and an availability recheck before preparation.
