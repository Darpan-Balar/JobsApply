# ADR-0011: Approval and submission workflow

- Status: Accepted (2026-10-14)
- Date: 2026-10-14
- Implements: ADR-0006

## Context
ADR-0006 set the safety rules for submissions: single-use approvals bound to the package, `SUBMITTING` persisted before acting, `SUBMITTED` only with evidence, and unconfirmed outcomes resolved only by the user. Phase 6 turns these rules into a working workflow before any browser automation exists (Phase 7). That work raised these questions:
- What may be approved?
- How are duplicate requests handled?
- What counts as evidence?
- How is a crash recovered?
- How is the workflow audited?

## Options
- **Approval gate:**
  1. Approve whatever the application points to.
  2. **The candidate sends the id of the package they reviewed. Approval is refused unless that package is still current and complete.**
- **Duplicate start requests:**
  1. Rely only on the state machine.
  2. **A required `Idempotency-Key`, unique per user in the database. Replays return the same attempt.**
- **Audit:**
  1. Spring Modulith's event publication registry (an asynchronous outbox).
  2. **A synchronous `@EventListener` in the same transaction.**
- **Submission channel before Phase 7:**
  1. None.
  2. **A MANUAL channel: the app records the attempt and gives the candidate the application link. The candidate submits in their own browser and reports the outcome.**

## Decision
The bold option in each case. In detail:

**Approval gates** (`SubmissionService.approve`). Approval is refused unless all of the following hold:
- The package id is the current package.
- The package has zero BLOCKING issues.
- Every required question is answered.
- The availability check made during preparation saw the posting OPEN.

`GET /approval/blockers` lists the reasons approval is refused. The approval stores the package's content hash. Producing a new package version (answer, resume edit, re-preparation) moves the application out of APPROVED, and that revokes the approval.

**Single use.** Starting a submission consumes the approval in the same transaction as APPROVED → SUBMITTING and the creation of the attempt. The database enforces three uniqueness rules:
- one active approval per application
- one attempt per approval
- one in-progress attempt per application

An optimistic-lock version on the application guards parallel requests too. The losers get 409.

**Idempotency.** `POST /submission` requires an `Idempotency-Key` of 8–100 characters from `[A-Za-z0-9._:-]`; if it is missing or malformed the response is 400.
- Replaying the same key returns the same attempt, even after it has finished.
- A key used for another application is refused.
- A replay racing the first request may get 409, but it never creates a second attempt.

**Evidence.** The same rules apply when an outcome is reported and when it is verified.
- `CONFIRMED` (→ SUBMITTED) needs credible evidence: a kind other than NONE plus a description.
- `FAILED` needs a description of the failure.
- `UNCONFIRMED` is the honest answer when the outcome is unclear. Only the candidate can resolve it, through `POST /verification`.

Evidence may contain personal data, so it is stored encrypted with row-bound AAD.

**FAILED.** A failed application needs re-preparation, which moves it back to READY_FOR_REVIEW even when the package is unchanged, and then a fresh approval. Nothing retries automatically.

**Crash recovery.** At startup, `SubmissionRecovery` moves every SUBMITTING application to SUBMISSION_UNCONFIRMED and marks its attempt UNCONFIRMED.
- It finds those applications with the one deliberate cross-user scan: plain SQL that reads only ids and owners.
- It then processes each one in its owner's `UserContext`.
- It assumes a single instance, as ADR-0008 does.

**Audit.** Audit records are written by an `@EventListener` inside the publishing transaction, so an audit record exists exactly when its change commits.
- Each record holds ids, statuses, codes and actor types, plus a `subject_user_id`.
- Records never hold evidence text, URLs or the user's free-text reasons.
- `GET /me/audit` returns only the caller's own records.

## Tradeoffs
+ Approving something other than what the candidate reviewed is impossible. Duplicate and parallel submissions are stopped by the database.
+ An audit record can't be lost after a commit, and none can exist for a change that rolled back.
− A slow or failing audit write fails the business operation. That is acceptable at local, single-user scale. The Modulith outbox (asynchronous, with retries) is deferred to Phase 9.
− Until Phase 7 the outcome is self-reported. That is why `CONFIRMED` requires evidence and `UNCONFIRMED` is always available.
