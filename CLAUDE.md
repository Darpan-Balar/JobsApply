# CLAUDE.md: Jobs Platform

Project guide for Claude sessions. The repository is the source of truth. For the current state, read `.ai/current-task.md` first.

## Commands
- Full build and all tests (Docker must be running): `./mvnw clean install`
- Unit tests only, no Docker: `./mvnw test`
- Integration tests only: `./mvnw -pl jobs-app verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- Run the app (port 8090): `docker compose up -d --wait && ./mvnw -pl jobs-app spring-boot:run -Dspring-boot.run.profiles=local`
- Validate config: `./mvnw -pl jobs-app spring-boot:run -Dspring-boot.run.arguments="--jobs.config.validate-only=true"`

## Architecture rules (see docs/adr)
- **Modular monolith.** Each feature module lives under `com.jobsapply.platform.<module>`. The module's base package is its public API, and `internal/` is private. Modules reference each other **by ID only**. `ModularityTest` enforces this, so don't weaken it.
- **`jobs-spi`** must stay free of Spring, JPA, Hibernate and Jackson (`SpiDependencyRulesTest`). `jobs-adapters` compiles against the SPI only, and `jobs-app` depends on it at runtime scope.
- **Multi-user (ADR-0002):**
  - Personal aggregates extend `OwnedEntity`, whose `@TenantId` sets the owner automatically.
  - Bind `UserContext.runAs(userId, …)` **before** opening a transaction.
  - Never pass an owner ID from a caller.
  - Native SQL repository queries are banned (`PersistenceRulesTest`).
  - Role profiles with a NULL owner are system templates, scoped by explicit queries.
- **State machine (ADR-0006):** change state only through `Application.transitionTo`. The transition table is in `ApplicationStatus`. Human-only transitions require `Actor.user`.
- **Schema:**
  - Flyway only (`jobs-app/src/main/resources/db/migration`). Hibernate runs `ddl-auto=validate`.
  - Never edit an applied migration; add a new one.
  - Enums are stored as text with a `CHECK`. Keep `CHECK` lists in sync with the Java enums.
- **JSON:** persisted JSON goes through `JsonCodec` (Jackson 3, canonical output). jsonb columns map to `String` with `@JdbcTypeCode(SqlTypes.JSON)`.
- **Time:** inject `Clock`. Never call `Instant.now()` in domain or service code.

## Conventions (ADR-0007)
- **Records** are used for values, DTOs, config and SPI contracts, with `@Builder` when there are many fields. Optional booleans are `Boolean`, normalized to false.
- **Entities:**
  - Use `@Getter @Builder @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PRIVATE)`.
  - No `@Setter`, `@Data` (a compile error via `lombok.config`) or `@ToString`.
  - Mutate state through intention-revealing methods.
  - Invariant-heavy aggregates use a private builder plus a static factory.
- **Services** use `@RequiredArgsConstructor` constructor injection.
- **Errors:**
  - Domain exceptions live in `shared.error` and are mapped to RFC 9457 problem details.
  - Messages must never contain personal data.
- **Tests:**
  - Unit tests are `*Test` (Surefire). Integration tests are `*IT` with `@IntegrationTest` (Failsafe, Testcontainers PG 18).
  - Database constraints are tested with plain SQL through `TestData`.
- **Never commit** secrets or personal data. `.env`, `local/` and `data/` are git-ignored, and config holds only `env:` secret references.

## Gotchas learned
- **jsonb is returned re-formatted** by PostgreSQL. Never compare stored JSON text with our canonical JSON; use `JsonCodec.canonicalize`.
- **Hibernate binds array parameters as `varchar[]`.** Array columns queried with array predicates must be `varchar(n)[]`, not `text[]`.
- **Run `clean` after renaming or removing a migration.** Otherwise `target/classes` keeps the old file and Flyway sees duplicate versions.
- **Migrations that have been pushed or merged are immutable.** Before editing an unmerged one, check that it hasn't been applied to the local Compose database (`select max(version) from flyway_schema_history`).

## Environment notes
- JDK 25 doesn't run annotation processors implicitly, so Lombok is declared in `annotationProcessorPaths`.
- Homebrew PostgreSQL uses port 5432 and another local app uses 8080. Compose therefore uses 5433, and the app uses 8090.
- Jackson 2 is also on the classpath (via springdoc). Our code uses Jackson 3 (`tools.jackson.*`) only.
