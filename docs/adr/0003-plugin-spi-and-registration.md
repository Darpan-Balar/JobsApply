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

## Tradeoffs
+ The compiler enforces isolation of adapters from the core.
+ Capability differences between sources are explicit.
− The SPI needs its own DTOs, so there is mapping code at the boundary.
− Changing the SPI is a compatibility concern, and the SPI is versioned with the platform.
