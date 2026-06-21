# Change log: Fix default-SMS-app status never reflecting in Settings

Implements [plans/20260617_112513_default-sms-app-status-detection.md](../plans/20260617_112513_default-sms-app-status-detection.md)
(approved by the user).

## Issue

After setting SMS Sentry as the device default SMS app (confirmed in system
Settings → Default apps → SMS app), the **Settings → System integration** card
still showed the *not-default* state (the "Set as default SMS app" button, the
`Sms` icon, the "Set SMS Sentry as default…" subtitle) with no indication that
the app was actually the default.

Root cause: `DefaultSmsAppManager.isDefault()` used the legacy
`Telephony.Sms.getDefaultSmsPackage()` comparison, which can disagree with the
actual role holder on API 29+ devices/OEMs — even though the role *request* side
already used `RoleManager.ROLE_SMS`. The card refresh path (Activity.onResume →
`refreshDefaultStatus()`) was working; the check method was returning the wrong
answer.

## Changes

- **[DefaultSmsAppManager.kt](../app/src/main/java/in/sreerajp/sms_sentry/util/DefaultSmsAppManager.kt)**
  - `isDefault()` is now authoritative on API 29+: it checks
    `RoleManager.isRoleAvailable(ROLE_SMS) && isRoleHeld(ROLE_SMS)`, symmetric
    with the existing `buildRequestIntent` path. Below API 29 it keeps the legacy
    `getDefaultSmsPackage()` comparison.
  - Added `currentDefaultPackage(context)` returning the system's reported default
    SMS package (nullable), used by the UI status line below.

- **[SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt)**
  (`DefaultSmsAppCard`)
  - Added a `DisposableEffect` + `LifecycleEventObserver` that calls
    `viewModel.refreshDefaultStatus()` on `ON_RESUME`, so the card re-checks
    whenever the Settings screen resumes (belt-and-suspenders on top of
    `MainActivity.onResume`).
  - Added a small "Current system default: <package>" status line so the state is
    always unambiguous.

No data-layer, manifest, or send-gating changes. `canSendSms`,
`refreshDefaultStatus`, the role-request flow, and import all read `isDefault()`
and become correct automatically.

## Verification

- Not built/run here (no Gradle/Android SDK in this environment, consistent with
  prior change logs). Needs an on-device pass:
  - With SMS Sentry default: card shows green `CheckCircle`, "SMS Sentry is your
    default SMS app…", button hidden, status line shows the SMS Sentry package.
  - After switching the default to another app and returning: the button reappears
    and the status line updates.
  - Compose/send gating (`canSendSms`) reflects the corrected status.
