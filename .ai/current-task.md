# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and a React + TypeScript dashboard. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-15)
- Phases 1–9 are merged and pushed (`master` @ 4ddb50c, GitHub Darpan-Balar/JobsApply, public repo).
- **Phase 10** (profile from a resume, ADR-0015) is complete on the local branch `phase-10-resume-import`. It is **not pushed or merged** and is waiting for the user's review.
- `./mvnw clean install`: 417 Java test cases and 25 Vitest tests, 0 failures.
- The local database is at V14. Backup and verify scripts were run against the real Compose database: verified, 32 tables.
- **The owner still has to create their account** at http://127.0.0.1:8090 (one-time setup; takes over the legacy `local` user).
- The user's personal data is not yet entered.

## Decisions made with the user
- Personal data is private per user, behind sign-in. Employers, sources and postings form a shared catalog.
- Work on a branch; merge (fast-forward) and push only after the user says "merge and push".
- **Commits carry only the user's authorship: no Claude co-author trailer** (stated 2026-10-15).

## Next action
The user reviews Phase 10. Once approved: merge (and push only if asked). Possible follow-ups:
- enter real data: account setup, then Profiles → New profile from my resume, then employers
- dashboard gaps (resume claim editing, employer editing)
- Prometheus endpoint
- an LLM provider
