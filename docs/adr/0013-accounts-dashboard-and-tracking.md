# ADR-0013: Accounts, dashboard and tracking

- Status: Accepted (2026-10-15)
- Date: 2026-10-15
- Builds on: ADR-0002 (multi-user ownership)
- Replaces: the pre-login LOCAL identity mode

## Context
Phase 8 adds a dashboard, real sign-in and tracking after submission. The user's requirements:
- "a user should login and see everything about him. no another user should have access to this information"
- one user today, with others onboarded quickly
- the same safety rules as before: nothing personal in logs or source control, and every submission is the user's own decision

The pre-login LOCAL mode bound one configured user to every request. It was only safe because the server listens on localhost.

## Options
- **Authentication:**
  1. JWT bearer tokens.
  2. **A server-side session cookie for a dashboard served on the same origin.**
  3. OIDC.
- **First account:**
  1. Credentials from configuration or the environment.
  2. **A one-time setup screen, available only from the machine itself, that takes over the existing pre-login user.**
- **Frontend delivery:**
  1. A separate dev server or host.
  2. **Built by Maven into the `jobs-web` jar and served by `jobs-app`.**
- **API contract:**
  1. Hand-written TypeScript types.
  2. **Types generated from a committed OpenAPI snapshot, which an integration test keeps identical to the live API.**

## Decision
The bold option in each case.

**Sign-in (Spring Security 7.1):**
- `POST /api/v1/auth/login` (form) and `POST /api/v1/auth/logout`.
- The session cookie `JOBS_SESSION` is HttpOnly, SameSite=Strict, with a 4-hour idle timeout.
- The session id changes at sign-in.
- CSRF uses the SPA pattern: cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`. Sign-in itself is protected too.
- Passwords are BCrypt-hashed (`DelegatingPasswordEncoder`), 12 characters to 72 bytes. Longer passwords are refused rather than silently truncated.
- Wrong credentials get one generic answer. Five failures lock that username for 15 minutes, whether or not the user exists.
- A disabled account's open sessions end on their next request.
- Security headers: a strict CSP (`script-src 'self'`, `frame-ancestors 'none'`), `Referrer-Policy: no-referrer` and `nosniff`.

**Authorization:**
- Every `/api/v1/me/**` path and every read needs a signed-in user.
- Changing the shared catalog (employers, sources, configuration import, synchronization) and `/api/v1/admin/**` need ADMIN.
- Personal data stays scoped to its owner through `UserContext` and `@TenantId`, for administrators too.
- The only unauthenticated paths are `/api/v1/auth/**`, health, the OpenAPI document, and the dashboard's static files.

**First account:**
- `POST /api/v1/auth/setup` works only from a loopback address and only while no account can sign in.
- It takes over the legacy `local` user, keeping its id and therefore all of its data, renames it, sets the password, and makes it ADMIN.
- Administrators then create further accounts.

**Tracking (V13):**
- Contacts, interviews (submitted applications only) and follow-ups.
- Names, contact details and notes exist only inside encrypted envelopes with row-bound AAD (ADR-0009).
- Tracking never changes an application's status.

**Dashboard (`jobs-web`):**
- React 19 + TypeScript 5.9 + Vite 8, built into `static/`.
- Client routes are forwarded to `index.html` (never cached). Hashed assets are cached for a year.
- Toolchain pinned and downloaded by the build: Node v22.23.3 and npm 11.20.0. npm 10.9 crashes resolving vitest 5's peer dependencies.

**Contract:**
- `OpenApiContractIT` fails when `jobs-web/src/api/openapi.json` differs from `/v3/api-docs`. `-Dopenapi.update=true` refreshes it.
- The dashboard's types are generated from the snapshot, so its type check shows every affected call.
- Nested DTO types are named with their enclosing type (e.g. `TrackingViewsSummary`). With simple names, colliding types silently overwrote each other in the published API.

## Tradeoffs
+ No tokens in browser storage. CSRF and session handling are Spring Security's, and the same origin means no CORS.
+ The first sign-in keeps everything the single user stored before accounts existed.
+ An API change that breaks the dashboard fails the build.
− Sessions and the lockout counters live in memory: a restart signs everyone out and clears the counters (single instance).
− There's no self-service password reset by e-mail. An administrator sets a new password.
− The build downloads Node and npm once into `jobs-web/node` (git-ignored).
− The dashboard covers the main flows. Resume claim editing, employer editing and employer preferences remain API-only (or YAML/CSV for employers).
