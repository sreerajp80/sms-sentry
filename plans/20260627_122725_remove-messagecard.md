# Remove orphaned MessageCard, retarget OTP tests to CopyOtpChip

**Status:** completed

## Issue

`MessageCard` ([SmsOrganizerUi.kt:2576-2718](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2576))
is orphaned production code — the old inbox card, superseded when the inbox was redesigned into
per-sender grouped conversations (`ConversationCard` → `ThreadScreen`/`ThreadBubble`) and a
detail screen (`MessageDetailScreen` → `MessageSmartCard`). Nothing in `app/src/main` calls it.

It survives only because `ExampleRobolectricTest` imports and renders it in two tests that verify
the OTP copy button. But that button is **not unique to `MessageCard`**: it is the shared
`CopyOtpChip` composable ([SmsOrganizerUi.kt:2528](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2528)),
which production renders via `ConversationCard` (line 1832) gated on `detectOtp(...) != null`.
The tests can therefore be retargeted to the real unit under test (`detectOtp` + `CopyOtpChip`)
and `MessageCard` deleted.

No cascade: `MessageCard`'s helpers (`SwipeAction`, `EntityChip`, `categoryColor`, `detectOtp`)
all remain used by `ConversationCard`/other screens after its removal.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
  - Delete the `MessageCard` composable (the `@OptIn(ExperimentalMaterial3Api::class)` +
    `@Composable fun MessageCard(...)` block, lines ~2576–2718).
  - Change `CopyOtpChip` from `private fun` to `internal fun` so test sources in the same module
    can render it directly. (`detectOtp` is already public — no change.)
- `app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt`
  - Replace the `MessageCard` import with imports for `detectOtp` and `CopyOtpChip`.
  - In both OTP tests, replace the `MessageCard(...)` render with the production OTP logic:
    `val otp = detectOtp(msg.body); if (otp != null) CopyOtpChip(otp = otp, msgId = msg.id)`.
    This reproduces exactly what the inbox does, so both assertions still hold:
    - OTP message → `detectOtp` returns `987152` → chip renders → `copy_otp_button_15` displayed
      and text `Copy OTP (987152)` displayed.
    - Normal message → `detectOtp` returns `null` → chip not rendered → `copy_otp_button_16`
      does not exist.
  - Add the missing `import androidx.compose.ui.test.assertDoesNotExist` (currently used at the
    bottom test but not imported) so the file compiles cleanly after the edit.

## Plan for the fix

1. Widen `CopyOtpChip` visibility to `internal`.
2. Delete the `MessageCard` composable block.
3. Update the test imports (drop `MessageCard`, add `detectOtp`, `CopyOtpChip`, `assertDoesNotExist`).
4. Rewrite the two `setContent { MyApplicationTheme { MessageCard(...) } }` blocks to
   `setContent { MyApplicationTheme { val otp = detectOtp(msg.body); if (otp != null) CopyOtpChip(otp, msg.id) } }`.
5. Leave the `read string from context` test untouched.

## Verification

- Build the app and run the Robolectric tests (`docs/build-and-test.md`); both OTP tests must
  still pass with the same tag/text assertions.
- Grep confirms zero remaining references to `MessageCard` in `app/src`.

## Notes

- Scope: only `MessageCard` removal + test retarget. The OTP detection/rendering behavior under
  test is preserved (now tested against the live `detectOtp`/`CopyOtpChip` path instead of a dead
  wrapper).
- Follow-up to [20260627_121829_remove-dead-code.md](20260627_121829_remove-dead-code.md), which
  deliberately deferred `MessageCard`.
