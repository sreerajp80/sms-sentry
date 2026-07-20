# Fixed Robolectric SDK 36 test failures with a Java 21 test toolchain

Implements [../plans/20260720_071301_robolectric-sdk36-java21-toolchain.md](../plans/20260720_071301_robolectric-sdk36-java21-toolchain.md).

## What was wrong

Four unit tests using `@Config(sdk = [36])` failed from the command line:
`ExampleRobolectricTest`, `GreetingScreenshotTest`, `P2PSyncCryptoTest`, `SyncSettingsTest`.

The failure looked like Robolectric not supporting SDK 36, but the actual message was
`Failed to create a Robolectric sandbox: Android SDK 36 requires Java 21 (have Java 17)`.
Robolectric 4.16.1 supports SDK 36; the SDK 36 system images are Java 21 bytecode and the Gradle
test workers were running on the Java 17 in `JAVA_HOME`. Android Studio never showed the failure
because it runs Gradle on its bundled Java 21 JBR.

## What changed

- **`app/build.gradle.kts`** — added a `tasks.withType<Test>` block that sets `javaLauncher` to a
  Java 21 toolchain. Test workers now run on Java 21 whatever `JAVA_HOME` is. `compileOptions` and
  `jvmTarget` stay on Java 17, so the app build and the shipped APK are unchanged.
- **`app/src/test/java/in/sreerajp/sms_sentry/CopyMessageTest.kt`** — `@Config(sdk = [34])` →
  `sdk = [36]`. The 34 was a workaround for the misdiagnosed failure.
- **`app/src/test/java/in/sreerajp/sms_sentry/ContactSuggestionsTest.kt`** — same change.
- **`docs/build-and-test.md`** — new "Unit tests need Java 21" section explaining the toolchain,
  how the JVM is located, and what the "requires Java 21" error means, with a warning not to lower
  `@Config(sdk)` or bump Robolectric in response to it.
- **`D:\Users\sreerajp\.gradle\gradle.properties`** (outside the repo, machine-specific) — created,
  setting `org.gradle.java.installations.paths` to `E:\Android\Android Studio\jbr` so Gradle reuses
  the installed Java 21 instead of downloading one.

No change to `gradle/libs.versions.toml` — the Robolectric version was never the problem. No change
to any app source.

## Verification

`./gradlew :app:testDebugUnitTest` run with `JAVA_HOME=E:\jdk-17` — the exact configuration that
failed before:

```
tests=56 skipped=0 failures=0 errors=0
BUILD SUCCESSFUL
```

All six Robolectric test classes now run at `sdk = [36]`, matching `compileSdk`/`targetSdk`.
