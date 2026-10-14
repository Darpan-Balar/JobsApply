# Jobs Platform

A local-first platform for discovering, matching, preparing and tracking job applications. It's extensible to any role family, employer, job source and user, through configuration and plugins rather than core code changes.

> **Status: Phases 1 (Foundation) and 2 (Employer & source registry) complete.**
> - Phase 1: domain model, schema, configuration validation and import, multi-user isolation and plugin contracts.
> - Phase 2: employer registry with search, source bindings and private per-user preferences; the plugin registry; YAML/CSV import and export; the REST API.
>
> **Not implemented yet:** job discovery (no source adapters exist yet, so the shipped sources are disabled), matching, resume and answer generation, browser-assisted applying, the dashboard and login. See [the architecture](docs/architecture/README.md) §10 for the phase plan.

## Stack

| Component | Version |
|---|---|
| Java | 25 (LTS) |
| Spring Boot | 4.1.1 (Spring Framework 7, Hibernate 7.4, Jackson 3) |
| Spring Modulith | 2.1.1 |
| Build | Maven (wrapper) |
| Database | PostgreSQL 18, with Flyway migrations |
| Tests | Testcontainers 2 and ArchUnit |

React + TypeScript (dashboard) and Playwright (browser assistance) arrive in later phases.

## Prerequisites

- JDK 25
- Docker Desktop, running. Integration tests and the local database need it.

## Quick start

```bash
cp .env.example .env              # then set JOBS_DB_PASSWORD (the file is git-ignored)
docker compose up -d --wait       # PostgreSQL 18 on 127.0.0.1:5433
./mvnw clean install              # build + all tests (needs Docker)
./mvnw -pl jobs-app spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile imports `config/` as system templates on startup. Doing so is idempotent: a second start changes nothing.

Once it's running, these endpoints are available:
- `http://127.0.0.1:8090/actuator/health`
- `http://127.0.0.1:8090/v3/api-docs`

The port is set by `JOBS_PORT`, and the server binds to localhost only.

### REST API (Phase 2)

The OpenAPI document is at `/v3/api-docs`. All endpoints live under `http://127.0.0.1:8090/api/v1`. Errors are RFC 9457 problem details.

| Endpoint | Purpose |
|---|---|
| `GET/POST /employers`, `GET/PUT /employers/{key}` | Search (tag, industry, minPriority, source, adapter, enabled, q, sort, page/size), create, and update (send back `version`) |
| `POST /employers/{key}/archive` and `/unarchive` | Soft delete and restore |
| `PUT/DELETE /employers/{key}/bindings/{source}/{externalRef}` | Bind an employer to a job source, or disable the binding |
| `GET/PUT/DELETE /me/employer-preferences[/{key}]` | Your private WATCH/IGNORE list |
| `GET /sources`, `PUT /sources/{key}`, `GET /plugins/job-source-adapters` | Job sources and installed adapters |
| `GET/POST /exchange/employers` | Employer CSV/YAML export, and CSV import (`?dryRun=true`) |
| `POST /config/documents`, `POST /config/import`, `GET /config/validation` | YAML document import, configuration directory import, and validation |

In the current *local identity mode*, every API call acts as the configured local user. The server is reachable only from this machine. Real login arrives with the dashboard (Phase 8).

### Validate configuration (CLI)

```bash
./mvnw -pl jobs-app spring-boot:run \
  -Dspring-boot.run.arguments="--jobs.config.validate-only=true"
# exit 0 = valid; exit 1 = invalid, with errors such as:
#   role-profiles/backend-engineer.yaml:skills.preferred[2].skill: unknown skill 'kafkaa'
```

Add `--jobs.config.directory=/path/to/other/config` to validate a different directory.

## Repository layout

```
jobs-spi/        Plugin contracts (records, interfaces, sealed results). No Spring/JPA. Third-party plugins compile against this.
jobs-app/        Spring Boot modular monolith (package-by-feature; boundaries verified by Spring Modulith)
jobs-adapters/   Built-in plugins (Greenhouse/Lever/Ashby/Playwright/…). Empty in Phase 1.
config/          Versioned YAML: job families, skills, role profiles, sources, employers
docs/            Architecture proposal and ADRs
compose.yaml     Local PostgreSQL
```

## Extending

- **Add a role family or profile.** Add a YAML file to `config/role-profiles/` (and a family to `config/job-families/` if needed), then validate. No code changes. See `config/README.md`.
- **Add skills or synonyms.** Edit `config/skills/`.
- **Add a user.** Call `UserService.createUser(...)`. Each user's candidate profiles, matches and applications are private to them, and the database rejects mixed-owner rows. Login arrives with the dashboard (Phase 8).
- **Add a job source, renderer or browser adapter.** Implement the matching interface from `jobs-spi`. Plugin registration arrives in Phase 2.

## Safety principles

These are enforced in code and in the database, not just documented:
- Personal data is private per user, and isolation is tested.
- Generated claims must cite verified facts.
- Every submission needs its own explicit approval. An uncertain submission is never retried automatically.
- At most one live application per user per vacancy is allowed (a unique index in the database).
- Configuration holds secret *references* only.
- CAPTCHAs, MFA and access controls are never bypassed.
