# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and later React + TypeScript. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-14)
- Phases 1 and 2 are merged and pushed (`master` @ 3251b66, GitHub Darpan-Balar/JobsApply, public repo).
- **Phase 3** (discovery) is complete on the local branch `phase-3-discovery`. It is committed locally only, **not pushed or merged**, and waiting for the user's review.
- `./mvnw clean install`: 247 tests, 0 failures. `-Plive` passed against the real Greenhouse, Lever and Ashby public boards (adapters and full pipeline).
- The local Compose database is at V8. The shipped sources are enabled; the shipped employers are fictional and disabled, so `POST /sync` currently finds no targets.

## Decisions made with the user
- Personal data is strictly private per user, with login (Phase 8). Employers, sources and postings form a shared catalog.
- 13 application states. A single user today; onboarding another is quick.
- Work on a branch; merge and push only after review.

## Next action
The user reviews Phase 3. Once approved: merge and push. Then Phase 4: candidate profile import, fact verification and PII encryption; the role-profile rule and component registry (replacing `DeclaredMatchingVocabulary`); the weighted strategy; skill synonym extraction from posting text; idempotent `JobMatch` rows.
