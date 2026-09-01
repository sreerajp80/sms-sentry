# Add Kotlin Guidelines Git Submodule

Implements plan: `plans/20260831_173000_add-kotlin-guidelines-submodule.md`

## What changed

1. **Added Git submodule**: Added `https://github.com/sreerajp80/Kotlin_Guidelines` at `docs/guidelines` and tracked via `.gitmodules`.
2. **Updated `CLAUDE.md`**: Added reference to `docs/GUIDELINES_MANIFEST.md` in the Detailed docs section.
3. **Created root `AGENTS.md`**: Created project-root `AGENTS.md` following the Thin pointer profile from `docs/guidelines/AGENTS_MD_GUIDELINE.md`, providing project identity, architecture rules, build commands, and pointing to `docs/GUIDELINES_MANIFEST.md`.
