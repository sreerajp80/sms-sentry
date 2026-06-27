# Fix deprecation warnings in SmsOrganizerUi.kt

Implements [plans/20260627_150008_fix-ui-deprecation-warnings.md](../plans/20260627_150008_fix-ui-deprecation-warnings.md).

All changes in `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`.

## Changes

1. **AutoMirrored icons** — added imports for `automirrored.filled.{ArrowBack, DriveFileMove,
   Send, TrendingDown, TrendingUp}` and switched the usages from `Icons.Default.X` to
   `Icons.AutoMirrored.Filled.X`: DriveFileMove (1053), Send (2262, 5949), TrendingUp/TrendingDown
   (3326), ArrowBack (4342, 5599). `ArrowBackIosNew` left untouched (not deprecated).

2. **`Divider` → `HorizontalDivider`** — renamed the 4 remaining `Divider(` calls (3201, 4609,
   4681, 5157). Signature identical; no argument changes.

3. **`Locale("en", "IN")`** — replaced with
   `Locale.Builder().setLanguage("en").setRegion("IN").build()` in `formatRupees()`. Covered by
   the existing `java.util.*` import; same resulting locale.

4. **`createConfirmDeviceCredentialIntent`** — annotated `triggerDeviceAuthentication()` with
   `@Suppress("DEPRECATION")`. Per the plan, the full migration to
   `androidx.biometric.BiometricPrompt` is deferred to a separate plan; behavior is unchanged.

All changes are warning-only cleanups; the only user-visible effect is correct RTL mirroring of
the auto-mirrored icons.
