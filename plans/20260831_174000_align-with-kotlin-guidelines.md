# Align Project Structure, Code, and Docs with Kotlin Guidelines

**Status:** completed

## Files to be changed

- `.gitignore` (add keystore and jks ignore rules)
- `app/src/main/assets/config/app_config.json` (new file for About screen metadata Pattern A)
- `app/src/main/assets/about_config.json` (delete old config asset)
- `app/src/main/java/in/sreerajp/sms_sentry/config/AppConfig.kt` (new file for typed About model)
- `app/src/main/java/in/sreerajp/sms_sentry/config/ConfigService.kt` (new file for config loader)
- `app/src/main/java/in/sreerajp/sms_sentry/util/AboutConfig.kt` (delete legacy loader)
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (update AboutPage to data-driven details loop)
- `CLAUDE.md` (update to align with CLAUDE_MD_GUIDELINE.md)
- `AGENTS.md` (update to align with AGENTS_MD_GUIDELINE.md)
- `docs/architecture.md` (fix broken com/example links and align structure)
- `docs/build-and-test.md` (fix broken com/example test links)
- `docs/security.md` (new living security blueprint for Sensitive Data Extension)
- `docs/release_process.md` (new living release runbook for Production App Extension)

## The issue

The repository has integrated `docs/guidelines` as a submodule, but several parts of the project do not yet fully adhere to the guidelines:
1. About metadata in `app/src/main/assets/about_config.json` and `util/AboutConfig.kt` uses legacy ad-hoc structures instead of Pattern A (`assets/config/app_config.json`, `config/AppConfig.kt`, and `config/ConfigService.kt`), and `AboutPage` in `SmsOrganizerUi.kt` hardcodes row items instead of dynamically iterating `details`.
2. `.gitignore` does not include the standard keystore entries (`keystore.properties`, `*.jks`, `*.keystore`).
3. Root `CLAUDE.md` is missing standard canonical sections outlined in `CLAUDE_MD_GUIDELINE.md` and does not fully mirror `AGENTS.md`.
4. `docs/architecture.md` and `docs/build-and-test.md` contain broken links referencing legacy `com/example` paths.
5. As a Sensitive Data and Production Android app, the project needs filled-in living blueprints `docs/security.md` and `docs/release_process.md` conforming to `DOCS_FOLDER_GUIDELINE.md`.

## Plan for the fix

1. **Config & About Screen (Pattern A):**
   - Create `app/src/main/assets/config/app_config.json` with required fields (`appName`, `description`, `version`, `build`, `details`).
   - Remove legacy `app/src/main/assets/about_config.json`.
   - Implement `in.sreerajp.sms_sentry.config.AppConfig` with `fromJson` and `fallback`.
   - Implement `in.sreerajp.sms_sentry.config.ConfigService` with `load` and `loadAndVerify`.
   - Update `AboutPage` in `SmsOrganizerUi.kt` to load from `ConfigService` and dynamically render rows from `config.details.entries`.
   - Delete legacy `in.sreerajp.sms_sentry.util.AboutConfig.kt`.

2. **Keystore & Git Ignore:**
   - Update `.gitignore` with the standard signing patterns (`keystore.properties`, `*.jks`, `*.keystore`).

3. **Root Guidelines Alignment (`CLAUDE.md` & `AGENTS.md`):**
   - Align `CLAUDE.md` and `AGENTS.md` to follow the canonical section order and Thin profile requirements from `CLAUDE_MD_GUIDELINE.md` and `AGENTS_MD_GUIDELINE.md`.

4. **Documentation Polish & Blueprints:**
   - Fix outdated `com/example` links in `docs/architecture.md` and `docs/build-and-test.md`.
   - Add `docs/security.md` covering offline design, AES-GCM P2P sync, biometric authentication, and threat analysis.
   - Add `docs/release_process.md` detailing build commands, versioning, signing configs, and pre-release verification.

5. **Verification:**
   - Run `./gradlew testDebugUnitTest` to verify all unit and Robolectric tests pass cleanly.
