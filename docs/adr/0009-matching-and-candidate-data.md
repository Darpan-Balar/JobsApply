# ADR-0009: Matching, candidate-fact verification and personal data at rest

- Status: Accepted (2026-10-14)

## Context
The requirements:
- Matching must be explainable, role-specific and versioned.
- Hard eligibility must stay separate from ranking.
- Unknown information must never be treated as a verified pass or fail.
- The core must work without an LLM.
- Uncertain imported data must be confirmed before it is used.
- Personal data must be protected and kept out of logs.

## Decision
1. **Plugins and registries.**
   - Eligibility rules, score components and strategies are SPI plugins in `jobs-adapters`, registered like any third-party plugin.
   - Their plugin id must equal the name used in role profiles; this is checked at startup.
   - Configuration validation uses the *installed* plugins, replacing the Phase 1 declared list.
2. **The facts that count.**
   - Plugins see facts only through `CandidateFacts`, which exposes VERIFIED, USER_PREFERENCE and DERIVED facts.
   - UNVERIFIED and SUPERSEDED facts are invisible, so they can only lead to UNKNOWN or "no evidence".
3. **Rules.**
   - A rule returns UNKNOWN whenever it lacks the facts it needs.
   - The `weighted` strategy evaluates *all* configured rules: any FAIL → NOT_ELIGIBLE, any UNKNOWN → UNKNOWN, else ELIGIBLE.
4. **Scoring.**
   - The score (0–100) is the weighted mean of the components *that have evidence*.
   - Weights of components without evidence are left out, and the coverage is reported with the result, so missing information is neither rewarded nor punished silently.
   - Strategy key and version are stored with every result.
5. **Results are explainable and idempotent.**
   - The full `MatchResult` (rule outcomes with fact references, component scores, matched, missing, uncertain, reasons) is stored per (posting, candidate version, role version, strategy, version).
   - Re-evaluation reports CREATED, UPDATED or UNCHANGED.
6. **Verification gate.**
   - Imported candidate data is UNVERIFIED unless the import is explicitly trusted.
   - The candidate confirms facts, which creates a new immutable version.
   - Data edited directly via the API keeps the declared status: it is the user's own statement.
7. **Personal data at rest.**
   - Candidate snapshots are encrypted with AES-256-GCM, using associated data bound to the row and owner.
   - The JSON envelope records a key id, so keys can be rotated with `previous-keys`.
   - Content fingerprints are keyed HMACs.
   - The key comes from `JOBS_ENCRYPTION_KEY`, and startup fails without it.
   - Candidate documents are rejected from the git-tracked configuration directory.
8. **Logs.** Structured logs pass through a redacting customizer that masks e-mail addresses and phone-shaped numbers.
9. **Skill extraction from job text** is catalog-based and whole-term (`c++`, `c#`). Ambiguous names (`go`, `c`) match only via aliases.

## Tradeoffs
+ No silent passes, and every score is explainable and reproducible.
+ A leaked database dump or backup does not expose candidate details.
− Losing the encryption key makes candidate data unreadable, so the key must be backed up separately.
− Required years are extracted from job text with heuristics. A wrong extraction can produce a FAIL from `max-required-experience`; the explanation shows the number used.
− Job skills come from text mentions, so absence is not proof. This affects soft scoring only.
− There is no currency conversion: pay in another currency is UNKNOWN for `compensation-min`.
