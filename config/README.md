# Platform configuration

Each file is versioned YAML with a `schemaVersion` and a `kind`. The importer validates the files and loads them into the database idempotently: re-importing unchanged files changes nothing. See ADR-0004.

| Directory | `kind` | Imported in |
|---|---|---|
| `job-families/` | `JobFamilySet` | Phase 1 |
| `skills/` | `SkillSet` | Phase 1 |
| `role-profiles/` | `RoleProfile` (imported as **system templates**, visible to every user) | Phase 1 |
| `sources/` | `JobSource` | Phase 2 |
| `employers/` | `EmployerSet` (employers and their source bindings) | Phase 2 |

The files are matched by `kind`, not by which directory they're in.

**Job sources.**
- An *enabled* source must name an installed adapter plugin, and its `settings` must be valid for that plugin.
- A *disabled* source may name an adapter that isn't installed yet. The shipped Greenhouse, Lever and Ashby sources are disabled until their adapters arrive in Phase 3.
- `GET /api/v1/plugins/job-source-adapters` lists the installed adapters.

**Employers.**
- Import is a declarative upsert keyed by `key`. Bindings the file no longer lists are *disabled*, never deleted.
- A provider identifier (`source` + `externalRef`) can belong to only one employer.
- Omitting `metadata` leaves stored metadata unchanged; `metadata: {}` clears it.
- Employers can also be exchanged as CSV via `/api/v1/exchange/employers`. The columns are `key,displayName,website,careersUrl,industry,tags,headquarters,discoveryPriority,enabled,sources`, where `tags` and `sources` are `;`-separated and each source is `sourceKey:externalRef[:priority]`.

**Never put personal data or secrets here.** This folder is tracked by git.
- Candidate profiles are imported from files outside the repository.
- Source settings may only hold secret *references*, such as `env:SOME_VAR`. The validator rejects literal values for keys that look like secrets.

The sample role profiles are **illustrative examples** (`example: true`), not anyone's verified qualifications. The sample employers are fictional and disabled.
