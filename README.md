# Jobs Platform

A local-first platform for discovering, matching, preparing and tracking job applications. It's extensible to any role family, employer, job source and user, through configuration and plugins rather than core code changes.

> **Status: Phases 1–5 complete.**
> - Phase 1 (Foundation): domain model, schema, configuration, multi-user isolation and plugin contracts.
> - Phase 2 (Employer & source registry): employer registry, plugin registry, YAML/CSV exchange and the REST API.
> - Phase 3 (Discovery): Greenhouse, Lever and Ashby adapters, plus syncing, normalization, cross-source dedup and availability tracking.
> - Phase 4 (Candidates & Matching): encrypted, versioned candidate profiles with fact verification, 11 eligibility rules, 7 score components, and explainable, idempotent matches.
> - Phase 5 (Preparation): tailored resumes (HTML/PDF) and cover notes built only from your verified facts and checked for traceability, plus draft answers that never guess sensitive questions, and immutable, encrypted application packages for your review.
>
> **Not implemented yet:** approval and submission, browser-assisted applying, the dashboard, login and scheduling. See [the architecture](docs/architecture/README.md) §10 for the phase plan.

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
cp .env.example .env              # then set JOBS_DB_PASSWORD and JOBS_ENCRYPTION_KEY (openssl rand -base64 32); the file is git-ignored
                                  # back up JOBS_ENCRYPTION_KEY: without it your candidate data cannot be decrypted
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
| `POST /sync?source=&employer=&dryRun=` | Synchronize the active bindings now (synchronous) |
| `GET /sync-runs`, `GET /sync-runs/{id}` | Sync history, with per-run errors |
| `GET /postings`, `GET /postings/{id}` | Search discovered jobs (employer, availability, workplace, employmentType, seniority, country, q, stale), and job detail with its source listings |
| `POST /postings/{id}/availability-check` | Recheck at the source whether a posting is still open |
| `POST /me/candidate-profiles/import` (YAML), `GET/PUT /me/candidate-profiles/{key}`, `POST /me/candidate-profiles/{key}/verification` | Your encrypted, versioned facts. Imports are UNVERIFIED until you confirm them (see `docs/examples/candidate-profile.example.yaml`) |
| `GET /role-profiles`, `POST /me/role-profiles/{key}/fork`, `POST /me/role-profiles/import` | Shared templates, and your private copies or your own profiles |
| `POST /me/matches/run`, `POST /me/matches/evaluate`, `GET /me/matches`, `GET /me/matches/{id}` | Explainable matches (eligibility kept separate from score, with every rule outcome and the evidence used) |
| `POST /me/applications/prepare`, `GET /me/applications[/{id}]`, `PUT /me/applications/{id}/answers/{q}`, `PUT /me/applications/{id}/resume`, `GET /me/applications/{id}/documents/{doc}`, `POST /me/applications/{id}/reject` | Prepare and review application packages (documents, answers, issues) |
| `GET /me/applications/{id}/approval/blockers`, `POST /me/applications/{id}/approval {packageId}`, `DELETE .../approval`, `GET .../approvals` | Approve the exact package you reviewed, for one submission (refused while anything blocks), or revoke the approval |
| `POST /me/applications/{id}/submission` (requires an `Idempotency-Key` header), `POST .../submission/{attempt}/outcome`, `POST .../verification`, `GET .../attempts`, `POST .../withdrawal` | Start the submission (you submit in your browser), report the outcome with evidence, resolve an unconfirmed outcome, withdraw |
| `GET /me/audit?aggregateId=` | Your audit trail (ids and statuses only) |
| `POST /me/candidate-profiles/{key}/master-resumes` (raw PDF, DOCX or text), `GET .../master-resumes/{id}/content` | Your master resume, kept exactly as uploaded |

**To match:**
1. Import your profile and confirm its facts.
2. Fork or tune a role profile.
3. Sync your employers.
4. `POST /me/matches/run {"candidateProfile":"main","roleProfile":"backend-engineer"}`
5. Read `GET /me/matches?candidateProfile=main&roleProfile=backend-engineer&eligibility=ELIGIBLE`.

**To apply:**
1. Prepare: `POST /me/applications/prepare`.
2. Review the package and answer every required question.
3. Approve: `POST .../approval {"packageId": ...}`.
4. Start: `POST .../submission` with an `Idempotency-Key` header. The response contains the application link.
5. Submit the form yourself in your browser.
6. Report the outcome:
   - `CONFIRMED` needs evidence, such as the confirmation page text or the e-mail subject.
   - If you are not sure what happened, report `UNCONFIRMED` and resolve it later with `POST .../verification` (ADR-0011).

**To discover jobs:** add a real employer with a verified board token (for example via `PUT /employers/{key}/bindings/greenhouse-public/{token}`, or YAML/CSV), make sure it is `enabled`, then `POST /sync`. A job is closed only on authoritative evidence: a failed request never closes anything (ADR-0008).

In the current *local identity mode*, every API call acts as the configured local user. The server is reachable only from this machine. Real login arrives with the dashboard (Phase 8).

### Validate configuration (CLI)

```bash
./mvnw -pl jobs-app spring-boot:run \
  -Dspring-boot.run.arguments="--jobs.config.validate-only=true"
# exit 0 = valid; exit 1 = invalid, with errors such as:
#   role-profiles/backend-engineer.yaml:skills.preferred[2].skill: unknown skill 'kafkaa'
```

Add `--jobs.config.directory=/path/to/other/config` to validate a different directory.

### Live checks against the real job boards (opt-in)

```bash
./mvnw -pl jobs-adapters verify -Plive                                     # adapters vs real public boards
./mvnw -pl jobs-app verify -Plive -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false   # full sync pipeline, twice
```

## Repository layout

```
jobs-spi/        Plugin contracts (records, interfaces, sealed results). No Spring/JPA. Third-party plugins compile against this.
jobs-app/        Spring Boot modular monolith (package-by-feature; boundaries verified by Spring Modulith)
jobs-adapters/   Built-in plugins: Greenhouse, Lever and Ashby job sources (Playwright etc. later). Depend on the SPI only.
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
