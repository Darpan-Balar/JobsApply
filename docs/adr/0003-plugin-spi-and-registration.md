# ADR-0003: Plugin SPI module and explicit registration

- Status: Accepted (2026-10-14)
- Date: 2026-10-14

## Context
Job sources, matching rules and strategies, document renderers, answer drafters, application (browser) adapters and LLM providers must all be addable without changing core services. Provider payloads and browser details must not leak into the domain.

## Decision
- A separate `jobs-spi` Maven module contains records, interfaces and sealed result types only. It has no Spring or JPA dependency, and Lombok is `provided` scope.
- Each plugin exposes a `PluginDescriptor` with: ID, version, kind, capabilities, settings type, settings schema version and compliance notes.
- `PluginRegistry<T>` collects Spring beans, fails at startup on duplicate IDs or invalid descriptors, and activates only the IDs in the `jobs.plugins.enabled` allow-list.
- Built-in plugins live in `jobs-adapters`, which compiles against `jobs-spi` only. `jobs-app` depends on it at `runtime` scope. External plugins are added as JARs with a Boot `AutoConfiguration.imports` entry.
- Expected failures are returned as `PluginResult.Failed` or `Unsupported`, not thrown.
- Capabilities are declared up front and checked before calls.

## Amendment: Phase 2 (2026-10-14)

### Context
The original decision activated only the plugin IDs listed in `jobs.plugins.enabled`. In practice a plugin is never *used* merely because it is registered: a job source must name its adapter explicitly in configuration. That explicit reference is already an opt-in.

### Decision
- **Deny-list instead of allow-list.** Every plugin bean on the classpath is registered and validated, and `jobs.plugins.disabled` excludes specific IDs. The registry still fails startup on a missing or invalid descriptor, a kind mismatch, or a duplicate ID.
- **Enabled sources must have an installed adapter.** An *enabled* job source must name an installed adapter, and its `settings` must bind to the adapter's typed settings record. That means strict Bean Validation, with unknown keys rejected.
- **Disabled sources may name a missing adapter.** A *disabled* source may name an adapter that isn't installed, which produces a notice rather than an error. The shipped Greenhouse, Lever and Ashby sources are therefore disabled until Phase 3, so no stub adapter pretends to work.
- **Literal secrets never reach a plugin.** Literal secret values in settings are rejected before any binding, and only `env:`/`secret:` references are accepted.

### Tradeoffs
+ Installing a plugin JAR needs no extra configuration step.
− A misbehaving plugin JAR on the classpath is still instantiated at startup. Use `jobs.plugins.disabled` to exclude it.

## Tradeoffs
+ The compiler enforces isolation of adapters from the core.
+ Capability differences between sources are explicit.
− The SPI needs its own DTOs, so there is mapping code at the boundary.
− Changing the SPI is a compatibility concern, and the SPI is versioned with the platform.
