# Rename namespace + applicationId to `in.sreerajp.sms_sentry`

**Status:** completed

## Issue

The app currently ships with the placeholder Android Studio identity:

- `namespace = "com.example"` (app/build.gradle.kts)
- `applicationId = "com.example"` (app/build.gradle.kts)
- All Kotlin source under package root `com.example` (dir tree `app/src/.../java/com/example/...`)

We want a real, owned identity: **`in.sreerajp.sms_sentry`** for both `namespace`
and `applicationId`.

### Kotlin keyword gotcha (decided)

`in` is a hard keyword in Kotlin, so the first package segment must be backtick-escaped
in every `package`/`import` statement: `` `in`.sreerajp.sms_sentry ``. The user chose to
keep `in.*` (over an alternative like `dev.*`) and accept the escaping. Gradle/manifest
string literals are NOT escaped (they are plain strings).

## Files to change

### Build config
1. **app/build.gradle.kts**
   - `namespace = "com.example"` → `"in.sreerajp.sms_sentry"`
   - `applicationId = "com.example"` → `"in.sreerajp.sms_sentry"`

### Manifest (string action constants only; `.MainActivity`/`.receiver.*` relative
names resolve against the new namespace automatically)
2. **app/src/main/AndroidManifest.xml**
   - `com.example.ACTION_COPY_OTP` → `in.sreerajp.sms_sentry.ACTION_COPY_OTP`
   - `com.example.ACTION_DELETE_MESSAGE` → `in.sreerajp.sms_sentry.ACTION_DELETE_MESSAGE`
   - `com.example.ACTION_OPEN_APP` → `in.sreerajp.sms_sentry.ACTION_OPEN_APP`

### Move source directories (package relocation)
Move the file trees so the on-disk path matches the package:
- `app/src/main/java/com/example/`      → `app/src/main/java/in/sreerajp/sms_sentry/`
- `app/src/test/java/com/example/`       → `app/src/test/java/in/sreerajp/sms_sentry/`
- `app/src/androidTest/java/com/example/` → `app/src/androidTest/java/in/sreerajp/sms_sentry/`

(Leaves empty `com/example` dirs behind — remove them.)

### Kotlin source: rewrite `package` / `import` / fully-qualified refs
In every `.kt` file, replace the `com.example` prefix with `` `in`.sreerajp.sms_sentry ``
(backtick-escaped first segment). Affected files:

**main**
- MainActivity.kt — `package`, 3 imports
- util/SmsNotificationHelper.kt — `package`, 3 imports + several fully-qualified
  `com.example.ui.theme.ThemeStyle` refs + 5 action string literals (strings, NOT escaped)
- receiver/SmsReceiver.kt — `package`, 3 imports
- receiver/NotificationActionReceiver.kt — `package`, 3 imports + 4 action string literals (strings, NOT escaped)
- engine/SmsShareUtils.kt — `package`, 2 imports
- engine/SmsClassifier.kt — `package`
- engine/P2PSyncEngine.kt — `package`, 2 imports
- ui/SmsOrganizerUi.kt — `package`, 7 imports
- ui/SmsOrganizerViewModel.kt — `package`, 6 imports + 1 fully-qualified ref
- ui/theme/Color.kt, Type.kt, Theme.kt — `package`
- data/SmsDao.kt, SmsDatabase.kt, SmsEntities.kt — `package`
- data/SmsRepository.kt — `package`, 1 import

**test**
- ExampleUnitTest.kt — `package`
- ExampleRobolectricTest.kt — `package`, 3 imports
- GreetingScreenshotTest.kt — `package`, 3 imports

**androidTest**
- ExampleInstrumentedTest.kt — `package` + assertion string
  `assertEquals("com.example", appContext.packageName)` → `"in.sreerajp.sms_sentry"`
  (string literal, NOT escaped)

Rule of thumb per file:
- `package com.example...`  → `` package `in`.sreerajp.sms_sentry... ``
- `import com.example...`   → `` import `in`.sreerajp.sms_sentry... ``
- inline FQN `com.example.X` → `` `in`.sreerajp.sms_sentry.X ``
- **string literals** `"com.example..."` → `"in.sreerajp.sms_sentry..."` (no backticks)

### Docs (cosmetic, keep accurate)
- docs/architecture.md line 3 — update `com.example` reference
- docs/build-and-test.md lines 24-25 — update test FQNs in the example commands

## Plan / order of operations
1. Edit app/build.gradle.kts (namespace + applicationId).
2. Move the three source dir trees from `com/example` → `in/sreerajp/sms_sentry`; remove
   the now-empty `com/example` dirs.
3. Rewrite `package`/`import`/inline-FQN lines in all moved `.kt` files (backtick-escaped
   `in`), and update string literals (un-escaped).
4. Update AndroidManifest.xml action strings.
5. Update the androidTest packageName assertion + docs references.
6. Verify: `grep -r "com.example"` returns nothing in code; confirm a build/compile if a
   Gradle environment is available.

## Risk / notes
- No Gradle wrapper in this repo (per CLAUDE.md); a clean compile may not be runnable here.
  Will at minimum verify zero residual `com.example` and that all backtick escaping is applied.
- `applicationId` change means the app installs as a *new* package — any previously
  installed `com.example` build will sit side-by-side, not upgrade. Acceptable for a rename.
- No ProGuard `-keep` rules reference `com.example` (proguard-rules.pro not using it).
