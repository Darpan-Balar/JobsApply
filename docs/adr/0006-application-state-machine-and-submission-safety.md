# ADR-0006: Explicit application state machine and submission safety

- Status: Accepted (2026-10-14)
- Date: 2026-10-14

## Context
Submissions are consequential external actions that can't be undone. Batches must never auto-submit. Uncertain outcomes must never be retried blindly.

## Options
1. Spring Statemachine. Rejected: heavy, with a separate persistence model, and more than an enum table needs.
2. **A hand-written transition table (`EnumMap<Status, Set<Status>>`), guards, and an aggregate method `transitionTo(...)`**, with history and events in the same transaction.

## Decision
Option 2, plus these rules:
- The `SUBMITTING` state is persisted *before* any external action. If the app crashes, recovery moves the application to `SUBMISSION_UNCONFIRMED`.
- An approval is single-use and bound to the package content hash, with a partial unique index allowing only one unconsumed approval.
- The `application` table has a partial unique index allowing at most one APPROVED, SUBMITTING, SUBMITTED or UNCONFIRMED application per (user, canonical posting).
- `SUBMITTED` requires recorded confirmation evidence. `SUBMISSION_UNCONFIRMED` is resolved only by a recorded manual verification.
- The SPI has no unattended submit operation. By default the human clicks Submit.

## User decision (2026-10-14)
The user approved the 9 requested states plus `SUBMITTING`, `NOT_ELIGIBLE`, `REJECTED_BY_USER` and `WITHDRAWN`, 13 in total. The transition table lives in `ApplicationStatus` and is tested exhaustively.

The following moves can only be made by an explicit user, never by the system:
- approving
- rejecting
- withdrawing
- resolving `SUBMISSION_UNCONFIRMED`

## Tradeoffs
+ Duplicate and blind submissions are prevented by the database, not only by code.
− More user clicks. That cost is intentional.
