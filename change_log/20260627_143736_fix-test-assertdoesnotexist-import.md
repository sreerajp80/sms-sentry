# Change log: Fix unresolved `assertDoesNotExist` import in ExampleRobolectricTest

Implements [plans/20260627_142824_fix-test-assertdoesnotexist-import.md](../plans/20260627_142824_fix-test-assertdoesnotexist-import.md).

## What changed

- **`app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt`** — removed the line-4
  import `import androidx.compose.ui.test.assertDoesNotExist`.

## Why

`assertDoesNotExist()` is a **member** function of `SemanticsNodeInteraction` (like `assertExists()`),
not a top-level extension function in `androidx.compose.ui.test` — so there was no symbol to import
and the import was unresolved, failing `:app:compileDebugUnitTestKotlin`. The actual call site
(`...assertDoesNotExist()`) is valid without the import and was left unchanged.

This was a pre-existing breakage in the working tree, unrelated to the dual-SIM and composer-UX
changes.

## Verification

- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL; the suite compiles and the tests run.
