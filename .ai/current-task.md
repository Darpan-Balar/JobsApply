# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and later React + TypeScript. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-14)
- **Phase 1** is merged and pushed (`master`, commit b21e374, GitHub Darpan-Balar/JobsApply, public repo).
- **Phase 2** (employer & source registry) is complete on the local branch `phase-2-employer-registry`. It is committed locally only: **not pushed, not merged**, and is waiting for the user's review.
- `./mvnw clean install`: 178 tests, 0 failures (13 SPI unit tests, 94 app unit tests, 71 integration tests).
- A local run against Compose PostgreSQL (port 8090) was verified. The local Compose database now has V7 applied.

## Decisions made with the user
- Personal data is strictly private per user, with login (Phase 8). Employers, sources and postings form a shared catalog.
- 13 application states.
- A single user today; onboarding another is quick (`UserService.createUser`).
- Work happens on a branch; nothing is merged or pushed without review.

## Phase 2 design changes (recorded in the ADR-0003 amendment and architecture §11)
- Plugin registration is a deny-list (`jobs.plugins.disabled`), and an enabled source needs an installed adapter. The shipped sources are disabled until Phase 3.
- Bindings moved to the employer module, so the dependency direction is employer → source.

## Next action
The user reviews Phase 2. Once approved: merge into master and push. Then Phase 3: Greenhouse, Lever and Ashby adapters (re-verify the official API docs first), sync orchestration, normalization, dedup and freshness.
