# Current Task

## Objective
Build an extensible, multi-user, local-first job discovery and application platform: Java 25, Spring Boot 4.1.1, PostgreSQL 18, Maven, Lombok, and later React + TypeScript. Implementation proceeds phase by phase, stopping after each phase for user approval.

## Current state (2026-10-14)
- **Phase 1 (Foundation): complete and verified.** Waiting for user approval to start Phase 2.
- `./mvnw clean install` passes 123 tests: SPI 13, app unit tests 63, integration tests 47.
- The app ran against Compose PostgreSQL 18 on port 8090. Health was UP, OpenAPI was served, and the config import was idempotent across restarts.
- Nothing is committed yet. The user approved `git init` only.

## Decisions made with the user
- Personal data is strictly private per user, with login (planned for Phase 8). Employers, sources and postings form a shared catalog.
- 13 application states: the requested 9 plus SUBMITTING, NOT_ELIGIBLE, REJECTED_BY_USER and WITHDRAWN.
- There's a single user today; onboarding another is `UserService.createUser`.
- The stack is Java 25 + Spring Boot (not Python).

## Next action: Phase 2 (employer & source registry)
Employer CRUD, REST and OpenAPI; YAML/CSV import and export of employers and sources (they're already validated); `PluginRegistry` with startup validation; `user_employer_preference`; filtering by tag, priority and source type; the Modulith event publication table once events are introduced. Acceptance criteria are in `docs/architecture/README.md` §10.
