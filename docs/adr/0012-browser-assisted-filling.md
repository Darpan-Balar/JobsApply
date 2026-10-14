# ADR-0012: Browser-assisted filling

- Status: Accepted (2026-10-14)
- Date: 2026-10-14
- Builds on: ADR-0003 (plugins), ADR-0006 and ADR-0011 (submission safety)

## Context
Phase 7 adds help with filling application forms. The brief's rules apply in full:
- Never fabricate anything.
- Never bypass CAPTCHA, MFA or authentication.
- Every submission needs its own explicit approval, and the human submits.
- Never blindly retry an uncertain submission.

Open questions:
- Where does the browser run?
- Who decides what goes into a field?
- What does "pause" mean?
- When is a filled form considered submitted?

## Options
- **Browser placement:**
  1. Inside `jobs-adapters`.
  2. **A separate `jobs-browser` module (SPI-only, auto-configured like a third-party plugin), keeping the Playwright dependency and its driver isolated.**
- **Field values:**
  1. The adapter guesses from labels.
  2. **The platform plans every value from the approved package (`FieldMappingPlanner`). The adapter only reads the form and fills HIGH-confidence mappings.**
- **CAPTCHA:**
  1. Refuse any form that contains a CAPTCHA widget.
  2. **Distinguish two cases.** A *challenge page* (no form; Cloudflare-style interstitial) stops assistance. A *CAPTCHA widget inside the form* is left for the human to complete before submitting, and is never touched.
- **Confirmation:**
  1. Trust any "thank you" text.
  2. **Require a confirmation message *and* evidence that the submission happened (the form has gone or the address changed). Record the message, address and a screenshot as evidence.**

## Decision
The bold option in each case. In detail:

**SPI changes:**
- `ApplicationAdapter` gains `open` and `close`. There is still **no submit operation**.
- `ApplicationSession` is owned by the platform and has `id()` and `saveArtifact()`.
- `FormInspection` carries `humanSteps` and `pageUrl`.
- `FieldMappingPlan.FieldMapping` carries an `Attachment` (bytes) and an `origin`.

**Starting.** `POST /me/applications/{id}/assist` with an `Idempotency-Key` uses the approval exactly like a manual start (ADR-0011): APPROVED → SUBMITTING and the attempt (channel BROWSER_ASSISTED) are persisted before any browser action. Browser work never runs inside a database transaction.

**One window per user.** The user's other applications keep their approvals untouched: nothing advances to another application.

**What gets filled (`FieldMappingPlanner`).** A field is filled only when its value comes from the approved package:
- a reviewed answer matched by question id or by identical wording; for choices, the answer must be one of the form's options
- contact details from the resume header; a name is split into first and last only when it has exactly two parts
- the generated resume PDF, for resume/CV file fields
- the approved cover note, for cover-letter text fields

Always left to the human:
- voluntary self-identification (gender, race, veteran status, disability, pronouns, age and similar)
- consent checkboxes
- combobox widgets, multi-selects and dates
- anything with no matching value

The adapter never overwrites a field that already has a value.

**Pauses.** Assistance pauses and fills nothing on:
- a sign-in page (password field)
- a one-time code (MFA)
- a challenge page
- HTTP 401, 403 or 429
- a page with no form

The human resolves it in the window and calls `/assist/refresh`.

**Submission.** The human presses Submit. `/assist/confirmation` polls (at most `jobs.assist.max-observe`) for credible confirmation:
- If seen, the attempt is recorded CONFIRMED by the system, with the message, address and encrypted screenshot as evidence.
- If not seen, nothing is recorded; the human looks again or reports the outcome.

When a window is closed or lost, the attempt stays in progress until the human reports it. A restart turns it into SUBMISSION_UNCONFIRMED (ADR-0011).

**Failures.**
- A page that is gone (404/410) is a definite failure, recorded as FAILED: nothing could have been submitted.
- A transient failure to open leaves the attempt in progress. The same key reopens the window.

**Threading and privacy.**
- Playwright objects live on one dedicated thread. Waiting for a confirmation polls from the caller's thread.
- Each window has its own ephemeral browser context: no cookies, storage or passwords are kept.
- Headed by default (`jobs.browser.headless=false`). Tests run headless.

## Tradeoffs
+ Every value in the form traces back to something the candidate approved. The adapter can't invent content or press Submit.
+ Pausing on logins and challenges keeps assistance within the sites' access controls.
− Fewer fields are filled automatically: custom widgets, multi-selects and ambiguous names are left to the human. That cost is deliberate.
− Confirmation detection is heuristic (message patterns). When it sees nothing, the human reports; it never guesses SUBMITTED.
− Playwright downloads its own Chromium on first use (about 150 MB under `~/Library/Caches/ms-playwright`) unless `jobs.browser.channel` names an installed browser such as `chrome`.
− Sessions live in memory and assume a single instance.
− Playwright auto-dismisses JavaScript dialogs (alert/confirm) in the window, which could interfere with a site that uses them.
