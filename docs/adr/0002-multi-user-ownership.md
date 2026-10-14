# ADR-0002: Multi-user data ownership from day one; authentication later

- Status: Accepted (2026-10-14)
- Date: 2026-10-14

## Context
The platform must be extensible to other people, each with their own profiles, resumes, matches and applications. Retrofitting ownership into a single-user schema is expensive and error-prone. Job data, by contrast, is the same for everyone.

## Options
1. Single-user now, add ownership later. Rejected: it would mean a migration of every personal table plus a risk of leaks during the retrofit.
2. A database or schema per user. Rejected: it duplicates the shared job catalog and makes syncs N times more expensive.
3. **A shared catalog plus an `owner_user_id` on every personal aggregate.** Hibernate `@TenantId` scopes strictly owned entities automatically; role profiles (system templates plus user forks) are scoped explicitly.
4. PostgreSQL Row-Level Security. This is deferred and remains a possible hardening layer once real authentication exists.

## Decision
Option 3. `app_user` exists from V1, and a local default user is seeded. `CurrentUserProvider` resolves the user from config until Spring Security login arrives (proposed Phase 8). Background jobs must set the user context explicitly.

## Reason
- The catalog is synced once and used by everyone.
- Isolation is enforced in the persistence layer and backed by tests, rather than relying on every query remembering a WHERE clause.

## User decisions (2026-10-14)
- Everything personal is private. Each user logs in and sees only their own data, and no other user may access it.
- Only one user exists today, but onboarding another must be quick. That is `UserService.createUser`, one call with no schema change.

## Implementation notes (Phase 1)
- The current user is held in `UserContext`, a Java 25 `ScopedValue`. Web authentication (Phase 8) and background jobs bind it explicitly. It is never inherited by pooled threads.
- The tenant resolver returns a nil-UUID sentinel when no user is bound. That sentinel matches no rows and cannot be inserted, because it violates the foreign key to `app_user`, so a missing context fails closed.
- Verified with Hibernate 7.4: the tenant filter also hides other users' rows on loads by id. `OwnedEntity` adds a `@PostLoad` ownership guard as a second line of defence.
- Native repository queries are banned by an ArchUnit test (`PersistenceRulesTest`).
- Composite foreign keys such as `(candidate_profile_id, owner_user_id)` make the database reject rows that mix owners.

## Tradeoffs
+ Adding a second user needs no schema changes.
− `@TenantId` doesn't apply to native SQL, so native SQL is banned in owned modules without review.
− Entities that are partly shared and partly owned (role profiles) need explicit query scoping.
− Until login exists, "switching user" is a local configuration action, not a security boundary.
