# Add the Kotlin Android Gradle plugin

**Status:** completed

## Issue

The app crashes at launch with:

```
java.lang.ClassNotFoundException: Didn't find class "in.sreerajp.sms_sentry.MainActivity"
  ... on path: DexPathList[[zip file ".../base.apk"] ...]
```

This reproduces on a **fresh install** (new APK hash), so it is not a stale/archived
install — the build itself produces an APK that does not contain the app's compiled
classes.

### Root cause

The Kotlin Android Gradle plugin (`org.jetbrains.kotlin.android`) is **not applied**
anywhere in the project:

- `gradle/libs.versions.toml` `[plugins]` defines `android-application`,
  `kotlin-compose` (the Compose *compiler* plugin, `org.jetbrains.kotlin.plugin.compose`),
  `google-devtools-ksp`, `roborazzi`, `secrets` — but **not** `kotlin-android`.
- `build.gradle.kts` (root) and `app/build.gradle.kts` apply the above, but never
  `kotlin-android`.

The Compose compiler plugin is only a companion to the Kotlin plugin; on its own it does
not register Kotlin compilation. Without `kotlin-android`, Gradle never creates the
`compileDebugKotlin` task, so all 16 `.kt` files are ignored. Only the generated
`BuildConfig.java` is compiled by javac and dexed.

Evidence in the build tree:
- `app/build/intermediates/project_dex_archive/debug/dexBuilderDebug/out/in/sreerajp/sms_sentry/`
  contains only `BuildConfig.dex` — no `MainActivity.dex` or any other app class.
- No `app/build/tmp/kotlin-classes/**` output exists at all (no Kotlin compilation ran).

## Files to change

1. `gradle/libs.versions.toml` — add a `kotlin-android` entry to `[plugins]`.
2. `build.gradle.kts` (root) — declare the plugin with `apply false`.
3. `app/build.gradle.kts` — apply the plugin in the module `plugins {}` block.

## Plan for the fix

1. In `gradle/libs.versions.toml` `[plugins]`, add (reusing the existing `kotlin = "2.2.10"`
   version):

   ```toml
   kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
   ```

2. In root `build.gradle.kts`, add to the `plugins {}` block:

   ```kotlin
   alias(libs.plugins.kotlin.android) apply false
   ```

3. In `app/build.gradle.kts`, add to the `plugins {}` block (before `kotlin.compose`):

   ```kotlin
   alias(libs.plugins.kotlin.android)
   ```

## Verification

- `gradle :app:assembleDebug` and confirm `app/build/tmp/kotlin-classes/debug/in/sreerajp/sms_sentry/MainActivity.class`
  is produced and that `MainActivity` appears in the merged dex / APK.
- `adb uninstall in.sreerajp.sms_sentry` then `gradle :app:installDebug`, launch, and
  confirm the app starts without `ClassNotFoundException`.
