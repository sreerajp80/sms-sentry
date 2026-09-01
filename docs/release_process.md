# Release Process — SMS Sentry

This document details the release runbook, signing configuration, versioning policy, and verification checklist for SMS Sentry release builds.
Read this before preparing or publishing a release APK or bundle.

**Read first:**
- [../AGENTS.md](../AGENTS.md) — mandatory repository rules and guidelines
- [../CLAUDE.md](../CLAUDE.md) — Claude Code instructions
- [build-and-test.md](build-and-test.md) — detailed build and test task reference
- [guidelines/release_process.md](guidelines/release_process.md) — Kotlin release process reference

---

## 1. Release Scope

- **App:** SMS Sentry
- **Release profile:** `Production App Extension`
- **Supported platform:** Android (minSdk 24, targetSdk 36, compileSdk 36)
- **Artifact type:** Standalone APK / App Bundle (AAB)

---

## 2. Versioning Policy

- **Version format:** `versionName = "MAJOR.MINOR"` (e.g. `16.11`); `versionCode = INTEGER` (e.g. `16`).
- **Source of truth:** `app/build.gradle.kts` in `defaultConfig`.
- **Sync with config asset:** Keep `version` and `build` in `app/src/main/assets/config/app_config.json` synchronized with `versionName` and `versionCode`.
- **Git tag format:** `v<versionName>` (e.g. `v16.11`).

---

## 3. Keystore & Signing Configuration

- **Keystore file:** `smekeystore.jks` at the repository root (git-ignored).
- **Key Alias:** `sms-sentry`.
- **Password Source:** Read from `SME_KEYSTORE_PASSWORD` in `local.properties` (git-ignored), falling back to the `SME_KEYSTORE_PASSWORD` environment variable for CI builds.
- **Git ignore:** `keystore.properties`, `*.jks`, and `*.keystore` MUST remain in `.gitignore`.

---

## 4. Build Commands

```bash
# Clean project
./gradlew clean

# Run full unit and Robolectric tests
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lint

# Build signed release APK
./gradlew assembleRelease

# Output artifact location
# app/build/outputs/apk/release/app-release.apk
```

---

## 5. Pre-Release Quality Checklist

Before publishing any release build:

- [ ] All unit and Robolectric tests pass cleanly (`./gradlew testDebugUnitTest`).
- [ ] Roborazzi screenshot verification passes cleanly (`./gradlew testDebugUnitTest -Proborazzi.test.verify`).
- [ ] Android Lint reports zero errors (`./gradlew lint`).
- [ ] Version and build numbers in `app/build.gradle.kts` and `app_config.json` match and increment from prior release.
- [ ] Release APK installs cleanly on target device (`adb install -r <apk-path>`).
- [ ] Biometric prompt and lock/reveal on Accounts tab functions correctly.
- [ ] P2P sync crypto functions and pairings succeed cleanly.
- [ ] No debug logs exposing SMS contents, OTP codes, or credentials in logcat.

---

## 6. Post-Release Tasks

1. Tag the release commit in Git:
   ```bash
   git tag -a v16.11 -m "Release v16.11"
   git push origin v16.11
   ```
2. Archive the release APK alongside its mapping file (if R8 enabled).
3. Create a changelog entry in `change_log/` documenting user-facing changes and bug fixes.
