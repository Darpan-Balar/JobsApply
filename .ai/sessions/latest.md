# Latest Session: 2026-10-15 (Phase 10)

1. Phase 9 was merged and pushed. `start-local.sh` was made a local-only, git-ignored root script; its `.gitignore` line is still uncommitted, at the user's choice.
2. The user found YAML profiles annoying and asked to build profiles from a resume PDF. They chose "Local, with optional Claude".
3. **Built:**
   - resume SPI
   - local reader and heuristic extractor (`jobs-adapters`)
   - Claude extractor (`jobs-llm-anthropic`, SDK 2.68.0, `claude-opus-5-5`)
   - `resumeimport` module (masking, mapping)
   - dashboard profile editor and new-profile flow
   - ADR-0015
4. **Verified:**
   - 417 Java + 25 Vitest tests green
   - a local run without an API key: the app is UP and Claude is inactive
5. **Next:** wait for review. Commits carry no Claude attribution.
