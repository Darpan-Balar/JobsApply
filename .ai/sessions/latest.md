# Latest Session: 2026-10-14 (Phase 6)

1. Phase 5 was merged with a fast-forward and pushed. Phase 6 started on `phase-6-workflow`.
2. **Built:**
   - V11 (approvals, attempts, audit subject)
   - application module: approve, start, outcome, verify, recover, withdraw, plus domain events
   - new `submission` module: gates, idempotent start, recovery, REST
   - synchronous `AuditRecorder` and `GET /me/audit`
   - `CandidateEvents` and `PreparationEvents`
   - ADR-0011
3. **Bugs fixed:**
   - a FAILED application could never get back to review when the package was unchanged
   - verification accepted CONFIRMED without evidence
   - `isCredible()` was serialised into the stored evidence JSON, which broke reading it back
4. **Verified:**
   - 342 tests green, including races of 4 threads on approve and start
   - a local run applied V11 and the endpoints answer on :8090
5. **Next:** wait for review.
