# ADR-0004: Versioned YAML configuration imported into the database

- Status: Accepted (2026-10-14)
- Date: 2026-10-14

## Context
Role profiles, employers, sources, skills and job families have to be data-driven, human-editable and reviewable in git. At runtime they need referential integrity, and they need versioning so that past matches can be reproduced.

## Options
1. YAML only, read at startup. Rejected: nothing in the DB can reference a version, and history is lost.
2. Database only, edited through the UI. Rejected as the *only* path: it isn't diffable or reviewable.
3. **YAML as the input format, imported into versioned DB rows.** The DB is the runtime source of truth.

## Decision
Option 3. Every file has `schemaVersion` and `kind`. The import pipeline is: parse (Jackson 3 YAML) → `ConfigUpgrader` chain → Bean Validation plus semantic validation → `file:path` error report → upsert by key and content hash. Role-profile content is immutable per version, and matches reference the exact version they used. Dry runs are supported.

## Tradeoffs
+ Configuration is reproducible, diffable and idempotent.
− If YAML and UI edits both happen, the DB can drift from YAML. UI edits create new versions, and an export command writes YAML back out.
