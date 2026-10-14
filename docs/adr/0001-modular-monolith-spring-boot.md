# ADR-0001: Modular monolith on Java 25 / Spring Boot 4.1 with Spring Modulith

- Status: Accepted (2026-10-14)
- Date: 2026-10-14

## Context
We need one locally runnable platform with many features (discovery, matching, preparation, workflow, browser automation). It has a single operator today and a few users later. Module boundaries have to stay clean so that features remain replaceable.

## Options
1. Microservices. Rejected: network boundaries, deployment overhead, and distributed consistency problems with no requirement that justifies them.
2. Multi-module Maven with one Maven module per feature. Rejected for now: boundaries are enforced at compile time, but every cross-feature change adds a lot of POM and wiring ceremony.
3. **A single Spring Boot application, package-by-feature, with Spring Modulith verification.** A separate Maven module is used only where compile-time isolation really matters: `jobs-spi` and `jobs-adapters`.

## Decision
Option 3. Java 25 (LTS), Spring Boot 4.1.1, versions managed by the Boot BOM, Maven with the wrapper, PostgreSQL 18 (Compose locally, Testcontainers in tests).

## Reason
- `ApplicationModules.verify()` checks the boundaries in a test, without a microservice's cost.
- The Modulith event publication registry gives us a durable transactional outbox for cross-module events.
- `jobs-spi` is the one boundary that third parties compile against, so it gets a real module.

## Tradeoffs
+ One deployable and one transaction manager, so local runs are simple.
− Boundaries inside `jobs-app` are only verified by tests, not by the compiler.
− Spring Modulith adds a dependency. If it proves incompatible with Boot 4.1, the fallback is ArchUnit rules.
