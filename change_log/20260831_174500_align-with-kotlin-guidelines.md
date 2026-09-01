# Align Project Structure, Code, and Docs with Kotlin Guidelines

**Plan:** [plans/20260831_174000_align-with-kotlin-guidelines.md](../plans/20260831_174000_align-with-kotlin-guidelines.md)

## What changed

1. **About Screen Configuration (Pattern A):**
   - Created `app/src/main/assets/config/app_config.json` containing top-level fields (`appName`, `description`, `version`, `build`) and a dynamic `details` map.
   - Removed legacy `app/src/main/assets/about_config.json`.
   - Created `in.sreerajp.sms_sentry.config.AppConfig` model with `fromJson()` and safe `fallback`.
   - Created `in.sreerajp.sms_sentry.config.ConfigService` with `load()` and `loadAndVerify()`.
   - Updated `AboutPage` in `SmsOrganizerUi.kt` to load from `ConfigService` and dynamically render rows from `config.details.entries` without hardcoded row labels.
   - Removed legacy `in.sreerajp.sms_sentry.util.AboutConfig.kt`.

2. **Keystore & Git Ignore Rules:**
   - Added `keystore.properties`, `*.jks`, and `*.keystore` ignore patterns to `.gitignore`.

3. **Root Guidelines Alignment:**
   - Updated `CLAUDE.md` and `AGENTS.md` to follow the canonical section order, declare Applicability Profiles, and maintain full dual alignment.

4. **Documentation Polish & Blueprints:**
   - Fixed outdated `com/example/` links in `docs/architecture.md` and `docs/build-and-test.md`.
   - Added `docs/security.md` as the living security blueprint for Sensitive Data Extension.
   - Added `docs/release_process.md` as the living release runbook for Production App Extension.

## Verification

- Ran `./gradlew testDebugUnitTest` — all 33 unit and Robolectric tests compiled and passed cleanly.
