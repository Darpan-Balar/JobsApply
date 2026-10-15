# CLAUDE.md: Jobs Platform

Project guide for Claude sessions. The repository is the source of truth. For the current state, read `.ai/current-task.md` first.

## Commands
- Full build and all tests (Docker must be running; the first run downloads Node/npm for jobs-web and Playwright's Chromium): `./mvnw clean install`
- Dashboard only: `./mvnw -pl jobs-web install` (Vitest runs in `test`). Dev server: `cd jobs-web && npm run dev` (Node ≥ 22.22; `jobs-web/node/node` works)
- Refresh the API contract after an intended API change: `./mvnw -pl jobs-app verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiContractIT -Dopenapi.update=true`
- Unit tests only, no Docker: `./mvnw test`
- Integration tests only: `./mvnw -pl jobs-app verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- Run the app (port 8090): `docker compose up -d --wait && ./mvnw -pl jobs-app spring-boot:run -Dspring-boot.run.profiles=local`
- Backups: `scripts/backup.sh`, `scripts/verify-backup.sh <dir>`, `scripts/restore.sh <dir> <new-db> <new-dir>` (database container must be running)
- Validate config: `./mvnw -pl jobs-app spring-boot:run -Dspring-boot.run.arguments="--jobs.config.validate-only=true"`
- Live tests (real public job-board APIs, opt-in): `./mvnw -pl jobs-adapters verify -Plive`. For the full pipeline, add `-pl jobs-app ... -Plive -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`. Never part of the default build.

## Architecture rules (see docs/adr)
- **Modular monolith.** Each feature module lives under `com.jobsapply.platform.<module>`. The module's base package is its public API, and `internal/` is private. Modules reference each other **by ID only**. `ModularityTest` enforces this, so don't weaken it.
- **`jobs-spi`** must stay free of Spring, JPA, Hibernate and Jackson (`SpiDependencyRulesTest`). `jobs-adapters`, `jobs-browser` (Playwright) and `jobs-llm-anthropic` (optional Claude) compile against the SPI only, and `jobs-app` depends on them at runtime scope.
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

## Discovery rules (ADR-0008), do not weaken
- Only authoritative evidence changes availability: presence in a listing (OPEN), absence from a *complete* listing (CLOSED), or a job-level 404 while the board answers 200 (CLOSED).
- Failures, partial pages and unidentified bad items must never close anything.
- Adapters return failures as values and never throw. Parsers never fabricate: missing means `null`.
- Network I/O never happens inside a DB transaction.

## Matching and personal-data rules (ADR-0009), do not weaken
- Plugins read candidate facts only through `CandidateFacts`, which exposes only claimable facts. Rules return UNKNOWN when facts are missing.
- Components return `value = null` for "no evidence", never a guessed number.
- Candidate snapshots are encrypted at rest (`PersonalDataCipher`, row-bound AAD). Never log snapshots or contact details. Candidate YAML must never go into `config/`.
- Tests use a fixed test key (application-test.yml). Real keys live only in `.env`.

## Preparation rules (ADR-0010), do not weaken
- Content plugins select and restate facts. `TraceabilityValidator` (structural checks and lexical grounding) runs on the platform side for every package and edit.
- Never draft answers for sensitive or self-identification questions, and never write free-text answers for the candidate.
- READY_FOR_REVIEW requires zero BLOCKING issues, including availability *confirmed by a check made now*.
- Master resumes and packages are immutable. Read uploads as raw bytes (`RequestBodies.readBytes`), never as text.

## Profile-from-resume rules (ADR-0015), do not weaken
- Extractors report only what the resume states (`null` otherwise). Never fill a missing month, city or level with a guess.
- Drafts are never stored. Every drafted fact is UNVERIFIED, and only the user's edits or explicit "everything is true" confirm facts.
- The local extraction always runs and supplies contact details. An extractor with `sendsTextExternally()` runs only when chosen per upload (with consent in the UI) and receives `TextMasking`-masked text.
- `jobs-llm-anthropic` registers nothing without `ANTHROPIC_API_KEY`. Use the official SDK (no raw HTTP), model `claude-opus-5-5` unless the user says otherwise, structured outputs, and handle refusals.

## Accounts and dashboard rules (ADR-0013), do not weaken
- There is no unauthenticated access to `/api/v1/**` beyond `/auth/**`. Writes need the CSRF header. Catalog writes and `/admin/**` need ADMIN. Never give ADMIN access to other users' personal data.
- `UserContextFilter` binds the signed-in user. Tests sign in through `HttpApi` (`TestAccounts`), never through a bypass.
- First-account setup is loopback-only and one-time, and takes over the legacy `local` user (keeping its data).
- Tracking details (names, contact data, notes) are stored only in encrypted envelopes.
- The dashboard never uses `dangerouslySetInnerHTML`, inline scripts or external origins (CSP `script-src 'self'`).
- The OpenAPI snapshot `jobs-web/src/api/openapi.json` must match the server (`OpenApiContractIT`). Nested DTO schema names include their enclosing type.

## Operations rules (ADR-0014), do not weaken
- Audit entries are recorded by `@ApplicationModuleListener` via the event publication registry (asynchronous after commit, retried). Events must be self-contained (actor included), serializable, and free of personal data. `AuditRecorder` must stay idempotent. Tests assert audit with `Eventually.eventually`.
- Scheduled jobs run through `ClusterLock.runExclusively` (advisory lock). Retention never deletes personal data, audit entries, running syncs or undelivered events.
- Backups: `scripts/backup.sh` must keep verifying by restoring into a scratch database. `restore.sh` must never overwrite an existing database or directory. Backups never contain the encryption key.
- `/api/v1/me/export` is built only from owner-scoped services.
- Health details and `/actuator/metrics` are ADMIN-only. Metric tags are codes, never ids or personal data.

## Submission rules (ADR-0006, ADR-0011), do not weaken
- Approve only the current, complete package. A new package version revokes the approval.
- An approval is used once.
- `POST /submission` requires an `Idempotency-Key`.
- SUBMITTED needs credible evidence, whether reported or verified. Only the user resolves SUBMISSION_UNCONFIRMED. Nothing is retried automatically.
- At startup, SUBMITTING becomes SUBMISSION_UNCONFIRMED (`SubmissionRecovery`, the only cross-user scan).
- Audit payloads hold ids, statuses and codes only: never evidence text, URLs or user free text. Audit is written asynchronously through the outbox (ADR-0014).

## Browser assistance rules (ADR-0012), do not weaken
- The SPI has no submit operation. The human presses Submit, and the adapter only observes.
- Every filled value comes from the approved package via `FieldMappingPlanner` (platform side). Adapters fill only HIGH mappings and never overwrite a non-empty field.
- Never fill self-identification or consent fields. Never touch CAPTCHA widgets. Pause (fill nothing) on login, MFA, challenge pages and 401/403/429.
- SUBMITTED from assistance requires a confirmation message plus the form gone or the address changed. Otherwise nothing is recorded.
- Allow one window per user. Nothing advances to another application.
- Browser calls never run inside a DB transaction. All Playwright calls run on the adapter's single browser thread.

## Gotchas learned
- **`JsonCodec.canonicalize` must keep decimal scale.** It does, by reading decimals as `BigDecimal`. Comparing with values read as `double` breaks idempotency.
- **jsonb is returned re-formatted** by PostgreSQL. Never compare stored JSON text with our canonical JSON; use `JsonCodec.canonicalize`.
- **Hibernate binds array parameters as `varchar[]`.** Array columns queried with array predicates must be `varchar(n)[]`, not `text[]`.
- **Run `clean` after renaming or removing a migration.** Otherwise `target/classes` keeps the old file and Flyway sees duplicate versions.
- **Jackson serialises `isX()`/`getX()` methods on records as properties**, which breaks reading them back. Name derived methods record-style (`credible()`, `blockingIssues()`).
- **Maven scope override:** declaring a dependency directly with `test` scope *replaces* its transitive runtime scope, and the app then fails at runtime even though the tests pass. Don't redeclare Playwright in `jobs-app`.
- **After changing the root POM**, install it (a full `./mvnw install`) before running `-pl` builds, which resolve sibling modules and the parent from `~/.m2`.
- **npm 10.9 crashes** ("reading 'edgesOut'") resolving vitest 5's peers. The build pins npm 11.20.0; locally use `npx npm@11.20.0 install`.
- **openapi-fetch captures `fetch` at creation**, so the client passes `fetch: (r) => globalThis.fetch(r)` and an absolute base URL. Both are needed for tests and harmless in browsers.
- **Hikari's `connection-timeout` binds a plain number of milliseconds**, not a duration string like `10s`.
- **Async event listeners have no `UserContext`.** Anything a listener needs (such as the actor) must be in the event.
- **jsdom treats a `required` file input as empty even after an upload**, which silently blocks form submit in tests. Guard with a disabled button instead.
- **API docs vs reality.** The ATS docs are incomplete. Verify parsers against a live response (`-Plive`) whenever the provider changes behaviour.
- **Migrations that have been pushed or merged are immutable.** Before editing an unmerged one, check that it hasn't been applied to the local Compose database (`select version from flyway_schema_history order by installed_rank desc limit 1`). Do NOT use `max(version)`: the column is text, so "9" sorts after "10".

## Environment notes
- JDK 25 doesn't run annotation processors implicitly, so Lombok is declared in `annotationProcessorPaths`.
- Homebrew PostgreSQL uses port 5432 and another local app uses 8080. Compose therefore uses 5433, and the app uses 8090.
- Jackson 2 is also on the classpath (via springdoc). Our code uses Jackson 3 (`tools.jackson.*`) only.
