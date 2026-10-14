# ADR-0007: Domain model, entity and Lombok conventions

- Status: Accepted (2026-10-14)
- Date: 2026-10-14

## Context
We want builders and concise code (Lombok) without the usual JPA and Lombok pitfalls: broken `equals`/`hashCode`, lazy loading or PII in `toString`, and setters that bypass invariants.

## Decision
- **Value objects, DTOs, SPI contracts, config records:** Java records with `@Builder(toBuilder = true)` where they have more than about three fields, and Bean Validation on the components.
- **JPA entities as rich aggregates:** `@Getter`, `@Builder`, `@NoArgsConstructor(access = PROTECTED)` and `@AllArgsConstructor(access = PRIVATE)`.
  - No `@Setter`, `@Data` or `@EqualsAndHashCode`. Equality comes from `BaseEntity`, based on ID.
  - No `@ToString`, because entities may hold PII or lazy associations.
  - State changes go through intention-revealing methods (`application.transitionTo(...)`, `listing.observeAvailability(...)`).
- **Spring beans:** `@RequiredArgsConstructor` constructor injection, `@Slf4j`, and no field injection.
- **Cross-module references** use typed IDs only. Each module's entities sit in its `internal` package.
- **`lombok.config`:**
  - `config.stopBubbling = true`
  - `lombok.addLombokGeneratedAnnotation = true` (JaCoCo excludes generated code)
  - `lombok.data.flagUsage = error`
  - `lombok.val.flagUsage = error`
  - `lombok.var.flagUsage = error`
- **JDK 23+:** Lombok is declared explicitly in `annotationProcessorPaths`.

## Refinements (Phase 1)
- Aggregates whose invariants matter, such as `Application` and `SyncRun`, use `@Builder(access = PRIVATE)` with a public static factory, so a builder cannot create an invalid state.
- Optional YAML/JSON flags are `Boolean`, normalized to `false` in the record's compact constructor. Jackson 3 rejects absent values for primitives by default.
- `JsonCodec` disables `SORT_CREATOR_PROPERTIES_FIRST`, so record JSON is fully alphabetical and content hashes don't depend on the order of record components.
- `lombok.addLombokGeneratedAnnotation` leaves `@lombok.Generated` (CLASS retention) in bytecode. That annotation is the one Lombok reference the SPI is allowed to have.

## Tradeoffs
+ Builders and immutability without the JPA pitfalls.
− Entities have `@Builder` with a private all-args constructor. `@Builder.Default` must be used carefully, because no-args construction (used by JPA) ignores defaults. Field initializers will be used for collections.
