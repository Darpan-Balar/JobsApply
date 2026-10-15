# Decisions

The authoritative records are the ADRs in `docs/adr/`. This is an index only.
- 0001: modular monolith on Java 25 / Boot 4.1 with Spring Modulith
- 0002: multi-user ownership from day one (@TenantId, a ScopedValue `UserContext`, composite-owner foreign keys); login in Phase 8
- 0003: plugin SPI module and explicit registration
- 0004: versioned YAML imported into the database (content-hash idempotency, dry run = rollback)
- 0005: candidate profiles as immutable jsonb snapshots with stable fact IDs
- 0006: 13-state application machine, single-use approvals, no automatic retries
- 0007: entity, Lombok and JSON conventions
- 0008: discovery, availability only from authoritative evidence, dedup
- 0009: matching via CandidateFacts, UNKNOWN when facts are missing, encrypted snapshots
- 0010: preparation (traceable fact-backed content, immutable encrypted packages)
- 0011: approval and submission workflow (approval gates over the reviewed package, idempotency keys, evidence rules, crash recovery, synchronous audit; Modulith outbox deferred to Phase 9)
- 0012: browser-assisted filling (separate jobs-browser module, platform-planned HIGH-only fills from the approved package, pauses on login/MFA/challenges, human submits, confirmation plus form gone = evidence)
- 0013: accounts (session sign-in, SPA CSRF, BCrypt, USER/ADMIN, loopback one-time setup claiming the legacy user), tracking (encrypted contacts/interviews/follow-ups), dashboard (React/Vite in jobs-web, OpenAPI-contract types)
- 0014: operations (audit via Modulith outbox, advisory-locked schedules, retention, verified backups/non-destructive restore, per-user export, admin-only health details and metrics)
- 0015: profile from a resume (local heuristic extraction by default, optional Claude with per-upload consent and masked text, unconfirmed drafts, dashboard profile editor)
