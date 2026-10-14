# Decisions

The authoritative records are the ADRs in `docs/adr/`. This is an index only.
- 0001: modular monolith on Java 25 / Boot 4.1 with Spring Modulith
- 0002: multi-user ownership from day one (@TenantId, a ScopedValue `UserContext`, composite-owner foreign keys); login in Phase 8
- 0003: plugin SPI module and explicit registration
- 0004: versioned YAML imported into the database (content-hash idempotency, dry run = rollback)
- 0005: candidate profiles as immutable jsonb snapshots with stable fact IDs
- 0006: 13-state application machine, single-use approvals, no automatic retries
- 0007: entity, Lombok and JSON conventions
