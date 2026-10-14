# ADR-0008: Discovery: availability evidence, idempotent ingestion and deduplication

- Status: Accepted (2026-10-14)

## Context
Job sources are external and fail in many ways: timeouts, 5xx errors, rate limits, missing boards, partial pages and malformed items. The requirements are strict:
- Never declare a job closed solely because a request failed.
- Never fabricate data.
- Update idempotently.
- Recheck availability before preparing an application.

The same vacancy may also be listed on several sources.

Verified facts about the providers (docs and live responses, 2026-10-14):
- **Greenhouse:** returns the whole board in one response, with `meta.total`.
- **Lever:** pages with `skip`/`limit`.
- **Ashby:** returns every published job in one response.
- **404s:** Greenhouse and Lever return 404 for a missing job *and* for a missing board.
- **Closure semantics:** none of the three documents what a closed job looks like.

## Decision
1. **Availability changes only on authoritative evidence.**
   - A listing becomes OPEN when it is seen in a source listing.
   - It becomes CLOSED only when it is absent from a *complete* listing, or when a job-level 404 arrives *while the board's own listing answers 200*.
   - Failed requests, failed or partial pages, and unparseable items never close anything, and a page limit doesn't either.
   - Non-authoritative observations are recorded as evidence only.
2. **Completeness is the adapter's claim and is conservative.**
   - Greenhouse: complete when the count matches `meta.total`.
   - Lever: complete only when the whole board fits in one page, because offsets shift while paging.
   - Ashby: always complete.
   - The engine treats a listing as incomplete if any page failed, a cursor repeated, the page cap was reached, or an unparseable item couldn't be identified.
3. **Rechecks.** For listings missing from an incomplete listing, the engine asks the source directly, up to a bounded number per run. Sources without a single-job endpoint (Ashby) are rechecked by re-syncing their complete listing. `AvailabilityService.recheck(posting)` is the pre-preparation check.
4. **Idempotent ingestion.**
   - Listings are keyed by `(binding, externalId)`, which is unique in the database. A content hash (SHA-256 of canonical JSON) detects changes.
   - An unchanged job only refreshes `last_seen_at`.
   - Raw values are stored on the listing; normalized values live on the posting.
5. **Normalization never guesses.**
   - Seniority comes from explicit title keywords only; otherwise UNKNOWN.
   - Workplace comes from the source's explicit value, or "all locations remote"; otherwise UNKNOWN.
   - Country comes from ISO codes or a trailing country name.
   - Invalid pay is dropped, not repaired.
6. **Dedup.**
   - A new listing joins an existing, non-closed posting of the same employer with the same fingerprint (normalized title plus location set), but only if that posting has **no listing from the same binding**.
   - Two identical openings on one board therefore stay separate.
   - The oldest listing defines the posting's canonical content.
7. **Run integrity.**
   - At most one RUNNING sync per binding, enforced by the database. A concurrent attempt is reported as SKIPPED.
   - Run history is written in separate transactions.
   - Network I/O happens outside transactions.
   - A dry run applies the changes and rolls them back.
   - At startup, RUNNING runs are marked ABORTED. This is a single-instance assumption.
8. **Stale is a flag, not a closure.** A not-closed posting unseen for `jobs.postings.stale-after` (default 3 days) is flagged.

## Tradeoffs
+ False closures are prevented by construction, and duplicate submissions to the same vacancy across sources become detectable.
− Jobs on multi-page Lever boards close only through per-job rechecks, which are bounded per run, so closure can lag.
− Fingerprint dedup can miss duplicates whose title or location wording differs between sources, and could merge two genuinely distinct openings with identical title and location on *different* sources. Both are visible in the posting's listings.
− Two concurrent syncs of different sources for one employer could both create a posting for the same vacancy. That's rare, and the next sync attaches nothing further.
− Startup recovery assumes a single instance. Multiple instances need leases, which come in Phase 9.
