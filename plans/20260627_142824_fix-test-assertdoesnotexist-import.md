# Fix unresolved `assertDoesNotExist` import in ExampleRobolectricTest

**Status:** completed

## What the issue is

`./gradlew :app:testDebugUnitTest` fails to compile:

```
e: .../ExampleRobolectricTest.kt:4:33 Unresolved reference 'assertDoesNotExist'.
```

[ExampleRobolectricTest.kt:4](../app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt#L4)
has `import androidx.compose.ui.test.assertDoesNotExist`. Unlike its siblings
`assertIsDisplayed` / `onNodeWithTag` / `onNodeWithText` (which are top-level **extension**
functions in `androidx.compose.ui.test` and need imports), `assertDoesNotExist()` (and
`assertExists()`) are **member functions** of `SemanticsNodeInteraction`. There is no top-level
symbol to import, so the import is unresolved — while the actual call at
[ExampleRobolectricTest.kt:79](../app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt#L79)
(`...assertDoesNotExist()`) is valid and needs no import.

This is pre-existing (the test file was already modified in the working tree before the dual-SIM
/ composer-UX work) and unrelated to those changes.

## Files to change

- **`app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt`** — delete the line 4
  import `import androidx.compose.ui.test.assertDoesNotExist`. No other change; the `.assertDoesNotExist()`
  call on line 79 keeps working as a member function.

## Verification

- `./gradlew :app:testDebugUnitTest` compiles and runs (the three tests execute).
