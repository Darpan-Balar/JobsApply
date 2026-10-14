# ADR-0010: Application preparation: fact-backed documents, traceability and packages

- Status: Accepted (2026-10-14)

## Context
The requirements:
- Generated application material must contain only claims grounded in the candidate's verified facts or in content the candidate explicitly approved.
- The master resume must be preserved, and generated variants stored separately.
- Availability must be rechecked before preparation.
- The candidate must be able to edit and approve each artifact.
- Nothing may be submitted automatically.

## Decision
1. **Selection, not generation.** The built-in content plugins tailor by *selecting and ordering* verified facts and restate them verbatim, using fixed phrasings for positions and education. They never write new prose about the candidate. An LLM rewriter could be added later as a plugin, but it would have to pass the same validator.
2. **Traceability validator (platform side; plugins cannot bypass it).**
   - *Structural:* every claim cites facts of the package's candidate version that may back claims.
   - *Grounding:* every number and content word of a claim must occur in the cited facts, apart from a fixed list of formatting words. A real fact cited for an invented statement is therefore rejected.
   - Answers derived from facts (for example total years) get the structural check only.
   - `userApproved` content is the candidate's own statement and is exempt.
3. **Answers.**
   - The role profile's `ALWAYS_ASK_USER`, `FROM_FACT` and `SKIP` rules come first.
   - Built-in sensitive topics are never answered: sponsorship, salary, notice period, referrals, relocation, demographics, criminal history.
   - Voluntary self-identification is never answered.
   - Free-text motivation questions are never written for the candidate.
   - A select answer must be one of the offered options.
4. **Packages.**
   - Each preparation or edit produces an immutable package version. Its payload is encrypted (AES-GCM, bound to the row and owner) and fingerprinted with an HMAC over its substance, which makes re-preparation idempotent.
   - Generated files go to an encrypted, write-once document store with generated file names.
   - The master resume is an immutable upload in the same store.
5. **Workflow.**
   - Preparation first rechecks availability. "Open" must be *confirmed by a check made now*; a stored OPEN with failing checks counts as unknown.
   - Next comes the match evaluation: NOT_ELIGIBLE stops before any document exists, and CLOSED expires the application.
   - The application then goes MATCHED → PREPARED → READY_FOR_REVIEW, the last step only when nothing blocks.
   - Blocking issues: untraceable content, unconfirmed availability, no application URL, a missing renderer or generator.
   - Warnings: unanswered questions, unknown eligibility, questions the source doesn't publish, an empty resume, PDF font limits.
6. **Network before transaction.** Availability rechecks and form-question fetches run before the single persisting transaction. If that transaction fails, files written for it are deleted.
7. **PDF dependency.**
   - `io.github.openhtmltopdf` (LGPL-2.1, used unmodified as a library) is pinned to **1.1.37** (January 2026), which predates a burst of roughly 60 rapid releases that began in July 2026 (1.1.38 → 1.1.97).
   - Its PDFBox is overridden to the current 3.0.8.
   - The renderer is a plugin and can be disabled.
   - Upgrade only after reviewing the upstream project's changes.

## Tradeoffs
+ Fabrication is structurally prevented and every statement is auditable.
− Resumes are only as good as the facts entered: no summary paragraph is generated unless the candidate writes and approves one.
− Grounding is lexical, so legitimate paraphrases by a future rewriting plugin would also need the words to appear in the facts. That is intentionally strict.
− The PDF uses the standard fonts, so non-Latin-1 text may not render (a warning is given; HTML is unaffected).
− Encrypted files are not deleted when a user is deleted (orphans), and there is no garbage collection of superseded package files yet.
