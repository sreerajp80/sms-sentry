# Fix Robolectric SDK 36 test failures with a Java 21 test toolchain

**Status:** completed

## What the issue is

Four JVM unit tests fail: `ExampleRobolectricTest`, `GreetingScreenshotTest`,
`P2PSyncCryptoTest`, `SyncSettingsTest`. All four use `@Config(sdk = [36])`.

The earlier guess was that Robolectric 4.16.1 does not support SDK 36. **That is wrong.**
The real error message is:

```
java.lang.UnsupportedOperationException: Failed to create a Robolectric sandbox:
    Android SDK 36 requires Java 21 (have Java 17)
    at org.robolectric.plugins.DefaultSdkProvider$DefaultSdk.verifySupportedSdk(DefaultSdkProvider.java:170)
```

So:

- Robolectric 4.16.1 **does** support SDK 36. The `android-all-instrumented-16-…` jar is
  already downloaded in the local Maven cache.
- The block is the **JVM that runs the tests**. Android 16 (API 36) is built against Java 21
  bytecode, so Robolectric refuses to build a sandbox on anything older.
- On this machine `JAVA_HOME` is `E:\jdk-17`, so Gradle runs the test workers on Java 17 and
  the four SDK 36 tests fail. This is why the failures look like a Robolectric problem but are
  really a toolchain problem.
- Android Studio does not hit this, because Studio runs Gradle with its bundled JBR
  (`E:\Android\Android Studio\jbr`, Java 21.0.8). The failure only shows up from the command
  line.

Because the cause was misread, the two newer tests were written with `@Config(sdk = [34])` as a
workaround. That is a downgrade, not a fix: those tests then no longer run on the same API level
the app targets (`compileSdk`/`targetSdk` 36).

### Proof

Same command, different JVM, nothing else changed:

| Test JVM | Result |
| --- | --- |
| `E:\jdk-17` (current default) | 4 failures, all `verifySupportedSdk` |
| `E:\jdk-24` | pass |
| `E:\Android\Android Studio\jbr` (21.0.8) | **56 tests, 0 failures, 0 errors** |

## The plan for the fix

Do **not** change the Robolectric version, and do **not** downgrade any `@Config(sdk = …)`.
Instead, tell Gradle to run unit tests on Java 21.

1. **Pin the test JVM with a Gradle Java toolchain.** In `app/build.gradle.kts`, make every
   `Test` task use a Java 21 launcher:

   ```kotlin
   // Android SDK 36 images are Java 21 bytecode, so Robolectric refuses to build a
   // sandbox on an older JVM. Compilation stays on Java 17 (see compileOptions).
   tasks.withType<Test>().configureEach {
     javaLauncher.set(
       javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
     )
   }
   ```

   Compilation stays at Java 17 (`compileOptions` / `jvmTarget`), so the shipped app is
   unaffected. Only the test workers move to 21.

2. **Make the toolchain resolvable on any machine.** `settings.gradle.kts` already applies
   `org.gradle.toolchains.foojay-resolver-convention`, so if no Java 21 is installed Gradle
   downloads one. Nothing to add there. To make it use the JBR that is already on this machine
   instead of downloading, add the install path to the **user-level**
   `D:\Users\sreerajp\.gradle\gradle.properties` (not the repo, since the path is
   machine-specific):

   ```properties
   org.gradle.java.installations.paths=E:\\Android\\Android Studio\\jbr
   ```

3. **Restore the SDK level in the two tests that were worked around.** Change
   `@Config(sdk = [34])` back to `@Config(sdk = [36])` in:
   - `app/src/test/java/in/sreerajp/sms_sentry/CopyMessageTest.kt`
   - `app/src/test/java/in/sreerajp/sms_sentry/ContactSuggestionsTest.kt`

   Now the whole suite runs on one API level, matching `targetSdk`.

4. **Document it** in `docs/build-and-test.md`: unit tests need Java 21+, the toolchain handles
   it, and the "requires Java 21 (have Java 17)" error means the toolchain was bypassed.

5. **Verify** with `./gradlew :app:testDebugUnitTest` using the plain `E:\jdk-17` `JAVA_HOME`.
   This is the case that fails today; it must pass afterwards, proving the toolchain (not the
   ambient `JAVA_HOME`) is what selects the test JVM.

### Why not the alternatives

- *Bump Robolectric* — pointless. 4.16.1 already supports SDK 36; the version is not the block.
- *Set `JAVA_HOME` to 21* — works, but it is an unwritten machine setting. A new checkout or a
  CI runner breaks again. The toolchain records the requirement in the build.
- *Use Java 24* — it passes, but prints `restricted method … java.lang.System::load` warnings
  from Robolectric's native runtime and is not a JVM the Android toolchain targets. 21 is the
  version Robolectric asks for and the one Studio already uses.
- *Keep everything on `sdk = [34]`* — hides the problem and stops testing against the API level
  the app actually targets.

## Files to be changed

| File | Change |
| --- | --- |
| `app/build.gradle.kts` | Add the `tasks.withType<Test>` Java 21 toolchain launcher |
| `app/src/test/java/in/sreerajp/sms_sentry/CopyMessageTest.kt` | `sdk = [34]` → `sdk = [36]` |
| `app/src/test/java/in/sreerajp/sms_sentry/ContactSuggestionsTest.kt` | `sdk = [34]` → `sdk = [36]` |
| `docs/build-and-test.md` | Note the Java 21 test requirement and the error message |
| `D:\Users\sreerajp\.gradle\gradle.properties` (outside the repo) | Point Gradle at the JBR install |

No change to `gradle/libs.versions.toml`, and no change to any app source.

## Risk

Low. Test-only. Compilation and the released APK keep Java 17. The one behaviour change is that
the four SDK 36 tests start actually running — if any of them has a real bug it will now surface
instead of erroring at sandbox creation. The full-suite run on JBR 21 already showed 56 passing
and 0 failing, so none is expected.
