# Known Issues, Limitations and Deferred Work

## Not implemented (by design, later phases)
- **Authentication:** none yet. In LOCAL identity mode every `/api/*` request acts as the local user, which is safe only because the server binds to 127.0.0.1 (Phase 8). Catalog writes (employers, sources, config import) have no role checks yet; admin roles arrive with login.
- **No scheduling:** sync runs on demand (`POST /sync`, which is synchronous) until Phase 9.
- **No real employers configured:** the shipped ones are fictional and disabled. The user needs to add employers with verified board tokens.
- **Matching vocabulary** is declared, not implemented (Phase 4). Configs referencing it validate, but nothing evaluates them.
- **No discovery, matching, preparation, approval, attempts or crash recovery services** (Phases 3–6).
- **Audit:** no listener writes `audit_event` yet (Phase 6). Pseudonymization is allowed by the DB trigger but has no service.

## Risks and technical debt
- **Preparation** (ADR-0010):
  - Resumes contain only fact restatements (no generated summary).
  - The PDF uses standard fonts, so non-Latin-1 text may not render (a warning is shown).
  - Encrypted files of deleted users and superseded packages are not garbage-collected.
  - Answer drafting uses only the built-in `rule-based-answers` plugin (not yet configurable per role).
  - Only Greenhouse publishes form questions. For Lever and Ashby you add questions yourself.
- **openhtmltopdf** is pinned to 1.1.37 because of a suspicious release burst upstream. Review upstream before upgrading.
- **Discovery** (also in ADR-0008):
  - Multi-page Lever boards close jobs only through bounded per-run rechecks.
  - Fingerprint dedup can miss duplicates with different wording, or merge identical openings across different sources.
  - A rare race between concurrent syncs of different sources for one employer can create duplicate postings.
  - Startup recovery assumes a single instance.
- **Greenhouse pay ranges** have no stated interval, so the period is stored as unknown. They are only fetched through the details endpoint, not during listing syncs.
- **Description HTML** is stored raw (`job_listing.description_html`) and must be sanitized when rendered (dashboard, Phase 8).
- **CSV:** `metadata` and per-binding `enabled=false` can't be expressed. CSV imports leave metadata unchanged and list only enabled bindings (undeclared ones get disabled). Use YAML for full fidelity.
- **Formula neutralization:** CSV export prefixes cells starting with `= + - @` with `'`. If such a CSV is re-imported, the apostrophe becomes part of the value.
- **Validation messages:** errors for key/slug patterns show the raw regex (`must match "^[a-z0-9]..."`). This is functional but could be friendlier.
- **Employer search scale:** `bindingOwners()` and `viewsById()` load every row. That's fine for thousands of employers but should be revisited if the catalog grows very large.
- **Encryption key:** losing `JOBS_ENCRYPTION_KEY` makes candidate data unreadable, so it must be backed up outside the repository. There is no automated key-rotation re-encryption: old versions stay readable through `jobs.crypto.previous-keys`.
- **Log redaction** masks e-mails and phone-shaped numbers in structured logs only. Plain-text logs (tests) are not redacted. Code must still never log personal data.
- **Matching heuristics** (ADR-0009):
  - Required years of experience are extracted from job text.
  - Job skills come from text mentions.
  - There is no currency conversion.
  - The matched-role lookup in `GET /me/matches/{id}` shows no role when the role profile's active version has since changed.
- **Posting dedup:** `fk_application_listing (job_listing_id, job_posting_id)` will block re-pointing a listing that an application uses to another posting during a dedup merge. This must be designed in Phase 3.
- **Tenant context timing:** `UserContext` must be bound before a transaction starts. A transaction opened without it sees nothing and can't insert owned rows (fails closed).
- **Classpath:** Jackson 2 is present via springdoc. Our code must use only Jackson 3 (`tools.jackson.*`).
- **Docker dependency:** integration tests require a running Docker daemon.
- **Spotless/formatter** was deferred. palantir-java-format compatibility with JDK 25 hasn't been evaluated.
