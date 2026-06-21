# Add the Kotlin Android Gradle plugin

Implements [plans/20260613_111819_add-kotlin-android-plugin.md](../plans/20260613_111819_add-kotlin-android-plugin.md).

## Problem

App crashed at launch with `ClassNotFoundException: in.sreerajp.sms_sentry.MainActivity`,
reproducing on a fresh install. The build produced an APK containing only the generated
`BuildConfig` class — none of the 16 `.kt` source files were compiled, because the
`org.jetbrains.kotlin.android` Gradle plugin was never applied (only the Compose compiler
plugin `kotlin-compose` was present). With no Kotlin plugin, `compileDebugKotlin` was never
registered and all Kotlin sources were ignored.

## Changes

- `gradle/libs.versions.toml`: added `kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }`
  to the `[plugins]` table (reuses existing `kotlin = "2.2.10"`).
- `build.gradle.kts` (root): added `alias(libs.plugins.kotlin.android) apply false`.
- `app/build.gradle.kts`: added `alias(libs.plugins.kotlin.android)` to the module `plugins {}`
  block (before `kotlin.compose`).

## Verification (pending on user's machine)

Run a clean build + reinstall:

```
adb uninstall in.sreerajp.sms_sentry
gradle clean :app:installDebug
```

Confirm `app/build/tmp/kotlin-classes/debug/in/sreerajp/sms_sentry/MainActivity.class` is
produced and the app launches without `ClassNotFoundException`.
