# Platform configuration

Each file is versioned YAML with a `schemaVersion` and a `kind`. The importer validates the files and loads them into the database idempotently: re-importing unchanged files changes nothing. See ADR-0004.

| Directory | `kind` | Imported in |
|---|---|---|
| `job-families/` | `JobFamilySet` | Phase 1 |
| `skills/` | `SkillSet` | Phase 1 |
| `role-profiles/` | `RoleProfile` (imported as **system templates**, visible to every user) | Phase 1 |
| `sources/` | `JobSource` | validated in Phase 1, imported in Phase 2 |
| `employers/` | `EmployerSet` | validated in Phase 1, imported in Phase 2 |

The files are matched by `kind`, not by which directory they're in.

**Never put personal data or secrets here.** This folder is tracked by git.
- Candidate profiles are imported from files outside the repository.
- Source settings may only hold secret *references*, such as `env:SOME_VAR`. The validator rejects literal values for keys that look like secrets.

The sample role profiles are **illustrative examples** (`example: true`), not anyone's verified qualifications. The sample employers are fictional and disabled.
