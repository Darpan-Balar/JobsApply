# Latest Session: 2026-10-14

1. **Direction changed twice.** The user switched from a Python proposal to Java + Spring Boot + PostgreSQL + React, then required Java 25 and multi-user support (private per-user data, quick onboarding).
2. **Design.** Wrote the architecture proposal and ADRs 0001–0007. The user approved them with these decisions:
   - strict per-user privacy with login
   - 4 extra states
   - `git init`
3. **Phase 1.** Implemented:
   - the build (Maven multi-module, Boot 4.1.1, Modulith 2.1.1, Lombok)
   - the SPI contracts
   - migrations V1–V6 with DB-enforced invariants
   - entities and services
   - multi-user tenancy
   - config load, validate and import (YAML, versioned, idempotent, dry run)
   - sample configs
   - 123 tests, all green
   - a verified local run
4. **Problems found and fixed along the way:**
   - Jackson 3 primitive-null default
   - record property ordering, which affects hashing
   - an unclear Lombok trace in the SPI ArchUnit rule
   - port 8080 conflict, so the app moved to 8090
   - non-ASCII CLI output
   - a broken test helper that would have passed vacuously (rewritten)
5. **Next:** stop and wait for approval of Phase 2. See `.ai/current-task.md`.
