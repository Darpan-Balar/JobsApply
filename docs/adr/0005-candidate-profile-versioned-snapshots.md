# ADR-0005: Candidate profiles as immutable versioned fact snapshots

- Status: Accepted (2026-10-14)
- Date: 2026-10-14

## Context
Every generated claim must be traceable to a verified fact. Applications must record exactly which profile version they used. Facts carry a verification status (VERIFIED, USER_PREFERENCE, DERIVED, UNVERIFIED, SUPERSEDED). Profile data per user is small.

## Options
1. One table per fact type (experience, education, skill and so on), edited in place. Rejected: past versions can't be reproduced without temporal tables everywhere.
2. Typed tables with copy-on-write per version. Rejected: it creates a lot of rows and complex migrations for little query benefit.
3. **`candidate_profile_version.snapshot` as `jsonb`**, mapped to typed Java records (`CandidateExperience`, `CandidateEducation` and so on). Each fact has a stable `FactId` that is carried across versions. Version rows are immutable.

## Decision
Option 3. An edit creates a new version. Generated claims reference `(profileVersionId, FactId)`. The master resume file is stored immutably, and generated variants are stored separately.

## Tradeoffs
+ Snapshots are exact and reproducible, and traceability is simple.
− Facts can't be queried across users or profiles in SQL. That isn't needed, because matching loads one snapshot into memory.
− Snapshot schema evolution needs the same upgrader approach as config (ADR-0004).
