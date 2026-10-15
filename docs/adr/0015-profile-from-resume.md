# ADR-0015: Profile from a resume, and a profile editor

- Status: Accepted (2026-10-15)
- Date: 2026-10-15
- Builds on: ADR-0005 (versioned candidate profiles), ADR-0009 (only confirmed facts are used)

## Context
Writing a candidate profile in YAML was the hardest part of setting the platform up. The user asked to upload a resume PDF and take the information from it, and chose local extraction by default with Claude as an option ("Local, with optional Claude").

The brief's rules still apply: nothing may be fabricated, and personal data stays private.

## Options
- **Extraction:**
  1. Local heuristics only (private, moderate quality).
  2. A hosted model only (better quality; the resume leaves the machine).
  3. **Local by default, with Claude opt-in per upload when an API key is configured.** This is the user's choice.
- **Review:**
  1. Save straight away as unconfirmed facts.
  2. **Show the draft in a profile editor (which also replaces YAML for editing), saved only when the user presses Save.**

## Decision
**SPI** (`com.jobsapply.spi.resume`):
- `DocumentReader` turns a file into text.
- `ResumeExtractor` turns text into a `ResumeExtraction`: what the resume states, with `null` for anything it doesn't. It declares `sendsTextExternally()` and a `label()`.

**Plugins:**
- `LocalDocumentReader` (`jobs-adapters`): PDF via PDFBox, DOCX by reading `word/document.xml` directly (no extra library), and plain text. A PDF with no text layer (a scan) is reported, not returned empty.
- `HeuristicResumeExtractor` (`jobs-adapters`):
  - reads section headings, date ranges (month names, numeric, "Present"), job headers ("Title | Employer | City, Country", "Title at Employer", "Title, Employer"), bullet highlights (rejoining wrapped lines), education (institution, degree, field, completion), and skills lists
  - leaves unclear fields empty and says why in `notes`
- `ClaudeResumeExtractor` (new module `jobs-llm-anthropic`):
  - Anthropic Java SDK 2.68.0 (the newest release at least two weeks old), model `claude-opus-5-5`, effort `low`
  - structured outputs with a JSON schema in which every field may be null
  - the server-side refusal fallback (`fallbacks: "default"`) is on; a refusal, or hitting the output limit, becomes a clear error rather than an empty profile
  - registered only when `ANTHROPIC_API_KEY` is set

**Platform** (`resumeimport` module):
- Detects the file type from its content (PDF, DOCX or text only). The local extraction always runs and supplies the contact details.
- An extractor that sends text externally runs only when chosen for that upload, and the dashboard requires an explicit consent tick for it. It receives the text with the e-mail, phone, links and the candidate's name replaced by `[email]`, `[phone]`, `[link]` and `[name]`; dates and employers are kept.
- The result becomes a `ProfileDraft`, a `CandidateSnapshot` in which every fact is UNVERIFIED:
  - highlights become achievements linked to their position
  - skills are mapped onto the catalog with the existing `SkillExtractor`; names not in the catalog are listed and left out
  - degree levels are recognised (Bachelor, Master and so on)
  - fact ids are stable slugs (`exp-beta-ag`)
- Nothing is stored. `POST /api/v1/me/candidate-profiles/drafts` returns the draft. `GET /api/v1/me/candidate-profiles/extractors` lists the options. `GET /api/v1/skills` feeds the editor.

**Never guessing:**
- A year without a month leaves the month empty (with a note); it is not set to January.
- City and country are not guessed from the text by the local reader.
- Claude is told to report only what is stated, with `null` otherwise.

**Dashboard:**
- `/profiles/new` reads a resume (or starts blank). `/profiles/:key/edit` edits any profile.
- The `ProfileEditor` covers contact, positions with achievements and catalog skills, education, skills with optional years and proficiency, and other facts (with suggested keys such as `work-authorization.<cc>` and the values the matching rule accepts).
- Facts the user adds or edits are saved as VERIFIED. Untouched imported facts keep their status unless the user ticks "I have checked everything above and it is true". Required fields are checked before saving.
- On a new profile, the uploaded file is also stored as its master resume.
- YAML import remains under "Advanced".

## Tradeoffs
+ Setting up takes minutes, not a YAML document. Nothing is used before the user has reviewed it, and the resume leaves the machine only by explicit, per-upload choice, with identifiers masked.
− Local extraction is heuristic. Unusual layouts, multi-column PDFs and scanned PDFs read poorly (scans not at all), and the editor is the safety net. Claude reads better but costs a few cents per resume and needs an API key.
− Masking is pattern-based. Identifiers written unusually (or a name spelt differently from the header) may not be masked.
