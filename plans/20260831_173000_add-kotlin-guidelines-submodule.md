# Add Kotlin Guidelines Git Submodule

**Status:** completed

## Files to be changed

- `docs/guidelines` (new git submodule from `https://github.com/sreerajp80/Kotlin_Guidelines`)
- `.gitmodules` (new file created by git submodule command)
- `CLAUDE.md`
- `AGENTS.md` (new file)

## The issue

The project has `docs/GUIDELINES_MANIFEST.md` added, which points to the shared Kotlin guidelines repository designed to live at `docs/guidelines/`. The submodule has not yet been registered, and the root `CLAUDE.md` and `AGENTS.md` need to reference `docs/GUIDELINES_MANIFEST.md` to ensure AI assistants and developers follow standard project conventions.

## Plan for the fix

1. Add the Git submodule:
   ```bash
   git submodule add https://github.com/sreerajp80/Kotlin_Guidelines docs/guidelines
   ```
2. Update `CLAUDE.md` to reference `docs/GUIDELINES_MANIFEST.md`.
3. Create `AGENTS.md` referencing `docs/GUIDELINES_MANIFEST.md` and repository guidelines/workflows.
4. Verify the submodule files are present and tracked correctly via `git submodule status`.
