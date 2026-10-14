# Latest Session: 2026-10-14 (Phase 5)

1. Phase 4 was merged with a fast-forward and pushed. Phase 5 started on `phase-5-preparation`.
2. **Research:**
   - Verified Greenhouse's `questions=true` shape live.
   - Found a burst of about 60 releases of openhtmltopdf starting July 2026, so pinned 1.1.37 with PDFBox 3.0.8 (ADR-0010).
3. **Built:**
   - preparation plugins: resume, cover note, answers, HTML and PDF renderers
   - Greenhouse questions
   - `TraceabilityValidator`
   - the encrypted document store and master resume
   - immutable encrypted packages and `PreparationService` with edits
   - application service, templates config kind, REST
   - V10
4. **Bugs fixed:**
   - availability "confirmed" from stale state
   - binary uploads corrupted by text decoding
   - cover-note sort order
   - the migration-order test now derives its expectations from the shipped files
5. **Verified:**
   - 327 tests green
   - local V10 with the template imported
6. **Next:** wait for review.
