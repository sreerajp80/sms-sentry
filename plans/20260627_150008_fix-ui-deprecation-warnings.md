# Fix deprecation warnings in SmsOrganizerUi.kt

**Status:** completed

## Issues

13 deprecation warnings in `app/.../ui/SmsOrganizerUi.kt`, in four groups:

1. **AutoMirrored icons** (7 warnings) — `Icons.Default.{DriveFileMove, Send, TrendingUp,
   TrendingDown, ArrowBack}` are deprecated in favor of their `Icons.AutoMirrored.Filled.*`
   equivalents (these icons flip in RTL layouts).
   - 1053 DriveFileMove, 2262 Send, 3326 TrendingUp + TrendingDown, 4342 ArrowBack,
     5599 ArrowBack, 5949 Send.
   - (`ArrowBackIosNew` at 2008/2670/3399 is **not** deprecated — left as-is.)

2. **`Divider` → `HorizontalDivider`** (4 warnings) — Material3 renamed `Divider`. Same
   parameter signature. Lines 3201, 4609, 4681, 5157. (Most call sites in the file already
   use `HorizontalDivider`.)

3. **`Locale("en", "IN")`** (1 warning, line 2459) — the two-arg `Locale` constructor is
   deprecated in Java.

4. **`KeyguardManager.createConfirmDeviceCredentialIntent(...)`** (1 warning, line 2999) —
   deprecated in favor of `BiometricPrompt`. This is in `triggerDeviceAuthentication()`.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

## Fix

1. **Icons** — add imports for the AutoMirrored variants:
   ```
   import androidx.compose.material.icons.automirrored.filled.ArrowBack
   import androidx.compose.material.icons.automirrored.filled.DriveFileMove
   import androidx.compose.material.icons.automirrored.filled.Send
   import androidx.compose.material.icons.automirrored.filled.TrendingDown
   import androidx.compose.material.icons.automirrored.filled.TrendingUp
   ```
   and change the 5 distinct usages from `Icons.Default.X` to `Icons.AutoMirrored.Filled.X`
   (Send and ArrowBack each appear twice). Purely visual parity; adds correct RTL mirroring.

2. **Divider** — rename the 4 remaining `Divider(` calls to `HorizontalDivider(`. Signature is
   identical, so no argument changes.

3. **Locale** — replace `Locale("en", "IN")` with
   `Locale.Builder().setLanguage("en").setRegion("IN").build()` (available since API 21, unlike
   `Locale.of` which needs a newer level). Same resulting locale; only the formatter input.

4. **createConfirmDeviceCredentialIntent** — annotate `triggerDeviceAuthentication()` with
   `@Suppress("DEPRECATION")`. **Recommended:** the deprecated call still works on all current
   Android versions; the real replacement is a full migration of the device-auth flow to
   `androidx.biometric.BiometricPrompt`, which is a behavioral change with a new dependency and
   belongs in its own plan. Suppressing keeps behavior identical now.

## Verification

- Compile the module; confirm the 13 warnings are gone (the credential one suppressed) and no
  new errors/warnings appear.
