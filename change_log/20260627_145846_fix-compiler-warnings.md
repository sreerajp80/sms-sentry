# Fix two compiler warnings (always-true condition + unchecked casts)

Implements [plans/20260627_145808_fix-compiler-warnings.md](../plans/20260627_145808_fix-compiler-warnings.md).

## Changes

- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`: changed the OTP
  block guard from `if (isOtp && otp != null)` to `if (otp != null)`, removing the redundant
  always-true clause while keeping the smart-cast of `otp` to non-null inside the block.
  `isOtp` is still used for the display title/body.

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`: added
  `@Suppress("UNCHECKED_CAST")` to the generic `persistedState()` helper. The `as T` casts are
  safe by construction (the `when (defaultValue)` branch selects the matching SharedPreferences
  accessor), so suppression silences the unchecked-cast warnings without behavior change.

Both changes are warning-only cleanups; runtime behavior is unchanged.
