# Fix two compiler warnings (always-true condition + unchecked casts)

**Status:** completed

## Issues

1. **`SmsNotificationHelper.kt:219` — "Condition is always 'true'."**
   Line 208 sets `val isOtp = otp != null`. Line 219 then checks
   `if (isOtp && otp != null)`. Because `isOtp` is just `otp != null`, the `&& otp != null`
   sub-expression is redundant (always true when `isOtp` is true), hence the warning. The
   `otp != null` clause is kept only so `otp` is smart-cast to non-null inside the block.

2. **`SmsOrganizerViewModel.kt` `persistedState()` — "Unchecked cast ... to 'T'."**
   The generic `persistedState<T>()` helper casts `prefs.getBoolean/getString/getInt(...)`
   results to `T` (`as T`). These casts are correct by construction (the branch is chosen by
   `when (defaultValue) { is Boolean -> ... }`) but are unverifiable at the type level, so
   Kotlin emits unchecked-cast warnings.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`

## Fix

1. In `SmsNotificationHelper.kt`, change the block guard from
   `if (isOtp && otp != null)` to `if (otp != null)`. This removes the redundant clause while
   preserving the smart-cast of `otp` to non-null inside the block. `isOtp` remains used at
   lines 208–210 for the display title/body, so it stays.

2. In `SmsOrganizerViewModel.kt`, annotate the `persistedState` function with
   `@Suppress("UNCHECKED_CAST")`. The casts are intentional and safe given the `when`
   branching; suppressing is the standard way to silence the warning for this pattern.

## Verification

- Compile the module and confirm both warnings are gone with no new errors.
