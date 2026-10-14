# Known Issues, Limitations and Deferred Work

## Not implemented (by design, later phases)
- **No REST APIs** beyond actuator and OpenAPI. Services exist but no controllers yet. `UserContext` isn't bound for web requests yet, because no endpoint needs it (Phase 2 for local mode, Phase 8 for login).
- **Employers and job sources:** config is validated but not imported (Phase 2). No plugin registry yet.
- **Matching vocabulary** is declared, not implemented (Phase 4). Configs referencing it validate, but nothing evaluates them.
- **No discovery, matching, preparation, approval, attempts or crash recovery services** (Phases 3–6).
- **Audit:** no listener writes `audit_event` yet (Phase 6). Pseudonymization is allowed by the DB trigger but has no service.

## Risks and technical debt
- **PII at rest:** candidate snapshots are plain jsonb. Column encryption is planned for Phase 4.
- **Log redaction:** there is no global redacting log customizer yet. Mitigations: PII records override `toString`, entities have no `toString` beyond their ID, exception messages carry no PII, and a scan of the integration-test output found no PII. Planned for Phase 4.
- **Posting dedup:** `fk_application_listing (job_listing_id, job_posting_id)` will block re-pointing a listing that an application uses to another posting during a dedup merge. This must be designed in Phase 3.
- **Tenant context timing:** `UserContext` must be bound before a transaction starts. A transaction opened without it sees nothing and can't insert owned rows (fails closed).
- **Classpath:** Jackson 2 is present via springdoc. Our code must use only Jackson 3 (`tools.jackson.*`).
- **Docker dependency:** integration tests require a running Docker daemon.
- **Spotless/formatter** was deferred. palantir-java-format compatibility with JDK 25 hasn't been evaluated.
