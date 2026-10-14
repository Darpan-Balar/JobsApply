# Known Issues, Limitations and Deferred Work

## Not implemented (by design, later phases)
- **Authentication:** none yet. In LOCAL identity mode every `/api/*` request acts as the local user, which is safe only because the server binds to 127.0.0.1 (Phase 8). Catalog writes (employers, sources, config import) have no role checks yet; admin roles arrive with login.
- **No job-source adapters** are installed, so the shipped sources are disabled (Phase 3).
- **Matching vocabulary** is declared, not implemented (Phase 4). Configs referencing it validate, but nothing evaluates them.
- **No discovery, matching, preparation, approval, attempts or crash recovery services** (Phases 3–6).
- **Audit:** no listener writes `audit_event` yet (Phase 6). Pseudonymization is allowed by the DB trigger but has no service.

## Risks and technical debt
- **CSV:** `metadata` and per-binding `enabled=false` can't be expressed. CSV imports leave metadata unchanged and list only enabled bindings (undeclared ones get disabled). Use YAML for full fidelity.
- **Formula neutralization:** CSV export prefixes cells starting with `= + - @` with `'`. If such a CSV is re-imported, the apostrophe becomes part of the value.
- **Validation messages:** errors for key/slug patterns show the raw regex (`must match "^[a-z0-9]..."`). This is functional but could be friendlier.
- **Employer search scale:** `bindingOwners()` and `viewsById()` load every row. That's fine for thousands of employers but should be revisited if the catalog grows very large.
- **PII at rest:** candidate snapshots are plain jsonb. Column encryption is planned for Phase 4.
- **Log redaction:** there is no global redacting log customizer yet. Mitigations: PII records override `toString`, entities have no `toString` beyond their ID, exception messages carry no PII, and a scan of the integration-test output found no PII. Planned for Phase 4.
- **Posting dedup:** `fk_application_listing (job_listing_id, job_posting_id)` will block re-pointing a listing that an application uses to another posting during a dedup merge. This must be designed in Phase 3.
- **Tenant context timing:** `UserContext` must be bound before a transaction starts. A transaction opened without it sees nothing and can't insert owned rows (fails closed).
- **Classpath:** Jackson 2 is present via springdoc. Our code must use only Jackson 3 (`tools.jackson.*`).
- **Docker dependency:** integration tests require a running Docker daemon.
- **Spotless/formatter** was deferred. palantir-java-format compatibility with JDK 25 hasn't been evaluated.
