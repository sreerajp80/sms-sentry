# Build & Test

## Build

The **Gradle wrapper is checked in** (`gradlew`, `gradlew.bat`, and `gradle/wrapper/`, pinned to
Gradle 9.3.1). Use it, or build/run from Android Studio. A locally installed `gradle` also works.
`local.properties` must point `sdk.dir` at the Android SDK.

```bash
./gradlew :app:assembleDebug       # build debug APK (gradlew.bat on Windows)
./gradlew :app:installDebug        # install to connected device/emulator
```

Dependencies are managed via the version catalog at [../gradle/libs.versions.toml](../gradle/libs.versions.toml)
(reference deps as `libs.xxx` in `build.gradle.kts`). Note `compileSdk`/`targetSdk` 36, `minSdk` 24,
Java 11, Compose BOM.

## Test

```bash
./gradlew :app:testDebugUnitTest      # run all JVM unit tests (Robolectric + Roborazzi)
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (device required)

# single test class / method
./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.GreetingScreenshotTest"
./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.ExampleUnitTest.addition_isCorrect"

# Roborazzi screenshot tests (output: app/src/test/screenshots/)
./gradlew :app:testDebugUnitTest -Proborazzi.test.record    # record/update golden images
./gradlew :app:testDebugUnitTest -Proborazzi.test.verify    # verify against goldens
```

JVM unit tests live in `app/src/test/` and run on **Robolectric** (`@Config sdk = [36]`), with
**Roborazzi** for Compose screenshot tests (e.g. [../app/src/test/java/com/example/GreetingScreenshotTest.kt](../app/src/test/java/com/example/GreetingScreenshotTest.kt)).
Instrumented tests are in `app/src/androidTest/`. There is no dependency-injection framework —
tests construct `SmsOrganizerViewModel(application)` directly, which spins up the real Room DB and
demo seed, so screenshot/UI tests exercise the full ingestion pipeline.

## Signing gotchas

- **Both** debug and release builds are signed with the `sme` signing config, which reads
  `smekeystore.jks` at the repo root (key alias `sms-sentry`).
- The keystore password (used for both store and key) is read from `SME_KEYSTORE_PASSWORD` in
  `local.properties` (gitignored), falling back to the `SME_KEYSTORE_PASSWORD` environment
  variable for CI. Add `SME_KEYSTORE_PASSWORD=<password>` to `local.properties` before building.
- The Secrets Gradle plugin maps `.env` (fallback `.env.example`) into `BuildConfig` —
  create `.env` with `GEMINI_API_KEY` to satisfy the build even though it's unused at runtime.
