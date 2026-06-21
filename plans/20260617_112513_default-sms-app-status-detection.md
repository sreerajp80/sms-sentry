# Plan: Fix "default SMS app" status never reflecting in Settings

## The issue

The user set SMS Sentry as the device default SMS app (confirmed in system
Settings → Default apps → SMS app), but the **Settings → System integration**
card still shows the *not-default* state: the `Sms` icon, the "Set SMS Sentry as
default…" subtitle, and the **Set as default SMS app** button. There is no
indication that the app is actually the default.

### Root cause

`DefaultSmsAppManager.isDefault()` decides the whole card state, and it uses the
**legacy** check:

```kotlin
fun isDefault(context: Context): Boolean {
    val current = Telephony.Sms.getDefaultSmsPackage(context)
    return current != null && current == context.packageName
}
```

The card is refreshed correctly (`MainActivity.onResume` → `refreshDefaultStatus()`,
plus the role-launcher callback), and the ViewModel state/observation is wired
correctly. Because the user left the app to check system Settings and came back,
`onResume` definitely re-ran the check — yet it still reported *not default*.
That isolates the fault to the **check method itself**:
`Telephony.Sms.getDefaultSmsPackage()` does not reliably agree with the role
holder on all devices/OEMs on Android 10+ (API 29+).

The request side already uses the modern `RoleManager.ROLE_SMS` path
(`buildRequestIntent`), but the *check* side still uses the legacy package
comparison. This asymmetry is the bug: we grant via RoleManager but verify via
the legacy setting.

## Files to change

1. **`app/src/main/java/in/sreerajp/sms_sentry/util/DefaultSmsAppManager.kt`**
   - Make `isDefault()` authoritative: on API 29+ use
     `RoleManager.isRoleHeld(RoleManager.ROLE_SMS)`; on older devices keep the
     existing `Telephony.Sms.getDefaultSmsPackage()` comparison.
   - Add a small helper `currentDefaultPackage(context)` returning the legacy
     `getDefaultSmsPackage()` value (used only for the diagnostic status line in
     the UI below; nullable).

2. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
   (`DefaultSmsAppCard`)
   - Add a `LifecycleEventObserver` (via `LocalLifecycleOwner` +
     `DisposableEffect`) that calls `viewModel.refreshDefaultStatus()` on
     `ON_RESUME`, so the card re-checks whenever the Settings screen becomes
     visible again — belt-and-suspenders on top of `MainActivity.onResume`, and
     correct even if the app stays resumed.
   - (Optional, low-risk) show a small secondary status line of *which* app is
     currently the system default, so the state is always unambiguous even in
     edge cases.

No data-layer, manifest, or gating-logic changes. `canSendSms`,
`refreshDefaultStatus`, the role-request flow, and import all stay as-is — they
already read `isDefault()`, so they automatically become correct once the check
is fixed.

## Plan for the fix

1. Rewrite `DefaultSmsAppManager.isDefault()`:
   ```kotlin
   fun isDefault(context: Context): Boolean {
       return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
           val rm = context.getSystemService(RoleManager::class.java)
           rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) &&
               rm.isRoleHeld(RoleManager.ROLE_SMS)
       } else {
           val current = Telephony.Sms.getDefaultSmsPackage(context)
           current != null && current == context.packageName
       }
   }
   ```
   (`RoleManager` is already imported.)

2. In `DefaultSmsAppCard`, register an `ON_RESUME` lifecycle observer that calls
   `viewModel.refreshDefaultStatus()` and dispose it cleanly.

3. Verify nothing else relied on the legacy semantics (the launcher callback's
   `DefaultSmsAppManager.isDefault(context)` continues to work — now via the
   role check).

## Verification

- Cannot build here (no Gradle/Android SDK in this environment — consistent with
  prior change logs). Needs an on-device pass:
  - With SMS Sentry set as default: the card shows the green `CheckCircle`, the
    "SMS Sentry is your default SMS app…" subtitle, and the button is hidden.
  - After removing it as default (set another app): returning to Settings shows
    the button again.
  - Sending/compose gating (`canSendSms`) reflects the corrected status.

## Notes / risk

- `RoleManager.isRoleHeld(ROLE_SMS)` is the Google-recommended check for the SMS
  role on API 29+ and is symmetric with the existing request path.
- Below API 29 behavior is unchanged.
