# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and later React + TypeScript. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-14)
- Phases 1–5 are merged and pushed (`master` @ 94fa571, GitHub Darpan-Balar/JobsApply, public repo).
- **Phase 6** (approval and submission workflow, ADR-0011) is complete on the local branch `phase-6-workflow`. It is committed locally only, **not pushed or merged**, and waiting for the user's review.
- `./mvnw clean install`: 342 tests, 0 failures. (Count `<testcase` entries in the XML reports; the `.txt` summaries undercount.) The local Compose database is at V11.
- The user's personal data is not yet entered (candidate profile, master resume, real employers).

## Decisions made with the user
- Personal data is strictly private per user, with login (Phase 8). Employers, sources and postings form a shared catalog.
- 13 application states. A single user today; onboarding another is quick.
- Work on a branch; merge (fast-forward) and push only after the user says "merge and push".

## Next action
The user reviews Phase 6. Once approved: merge and push. Then **Phase 7**, browser-assisted filling:
- a Playwright `ApplicationAdapter` that fills the form from the approved package
- per-application approval
- the human clicks Submit
- the BROWSER_ASSISTED channel records attempts and evidence
- never bypassing CAPTCHA or MFA
