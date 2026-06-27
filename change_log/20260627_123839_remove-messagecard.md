# Remove orphaned MessageCard, retarget OTP tests to CopyOtpChip

Implements [plans/20260627_122725_remove-messagecard.md](../plans/20260627_122725_remove-messagecard.md).
Follow-up to [plans/20260627_121829_remove-dead-code.md](../plans/20260627_121829_remove-dead-code.md),
which had deferred `MessageCard`.

## What changed

`MessageCard` was orphaned production code (the old inbox card, superseded by `ConversationCard`
/ `ThreadBubble` / `MessageSmartCard`) kept compiled only by two Robolectric OTP tests. Removed it
and pointed the tests at the real unit under test.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Deleted the `MessageCard` composable (~143 lines).
- Changed `CopyOtpChip` from `private fun` to `internal fun` so test sources in the same module
  can render it directly. (`detectOtp` was already public.)

### `app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt`
- Replaced the `MessageCard` import with `CopyOtpChip` and `detectOtp` imports; added the
  previously-missing `androidx.compose.ui.test.assertDoesNotExist` import.
- Rewrote both OTP tests to render the production OTP logic directly:
  `val otp = detectOtp(msg.body); if (otp != null) CopyOtpChip(otp, msg.id)`.
  Assertions are unchanged — OTP message still shows `copy_otp_button_15` / `Copy OTP (987152)`,
  and the normal message still has no `copy_otp_button_16`.

## Not changed (no cascade)

- `SwipeAction`, `EntityChip`, `categoryColor`, `detectOtp` remain used by `ConversationCard` and
  other screens after `MessageCard`'s removal, so nothing else became orphaned.

## Verification

- Grep confirms zero remaining references to `MessageCard` in `app/src`.
- A full Gradle build / Robolectric run was not executed here (no Gradle wrapper in repo). The
  retargeted tests reproduce the exact production OTP path (`detectOtp` + `CopyOtpChip`), so the
  same tag/text assertions hold.
