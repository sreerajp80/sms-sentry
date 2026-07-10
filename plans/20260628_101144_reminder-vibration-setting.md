# Reminder alerts: vibration off by default + user toggle in Settings

**Status:** completed

## What the user wants

- Reminder due-alerts must **not vibrate by default**.
- Add a **Settings toggle** so the user can turn vibration on/off for reminder alerts.

## The constraint that shapes the design

On Android 8+ (API 26+, our `minSdk` is 24) a `NotificationChannel`'s vibration is **immutable
once the channel is created**. Calling `enableVibration(false)` later — or deleting and recreating
the channel with the same id — does **not** change it: Android deliberately restores a recreated
channel's *previous* user/app settings (abuse prevention). So we cannot toggle vibration on the
existing single `sms_sentry_reminders` channel.

The reliable pattern is **two channels that differ only in vibration**, and post to whichever one
matches the current setting. For pre-O (API 24/25) channels are ignored and vibration is set on the
notification builder directly.

Today the reminder channel is created with `enableVibration(true)`
([SmsNotificationHelper.kt:364-375](../app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt#L364-L375)),
and no `setSound`/`setVibrate` is set on the builder.

## Plan

### 1. `SmsNotificationHelper.kt`
- Add a shared pref key constant `PREF_REMINDER_VIBRATION = "reminder_vibration_enabled"`
  (read from `theme_prefs`, default **false**).
- Replace the single `REMINDER_CHANNEL_ID` with two ids that differ only by vibration:
  - `REMINDER_CHANNEL_NOVIB_ID = "sms_sentry_reminders_novib"` — `enableVibration(false)`, name
    "Reminder Alerts".
  - `REMINDER_CHANNEL_VIB_ID = "sms_sentry_reminders_vibrate"` — `enableVibration(true)`, name
    "Reminder Alerts (Vibration)".
  (Both keep `IMPORTANCE_HIGH`, lights, and the existing default sound — sound is unchanged.)
- In `showReminderNotification(...)`:
  - Read `vibrationEnabled` from prefs.
  - On O+: create **only** the matching channel (no-op if it exists) and build the notification on
    that channel id. Also `deleteNotificationChannel("sms_sentry_reminders")` once to retire the
    legacy always-vibrating channel from upgraded installs (safe no-op on fresh installs).
  - On pre-O: set vibration on the builder — `setVibrate(longArrayOf(0L))` when disabled (no
    vibration), or a short pattern when enabled.

### 2. `SmsOrganizerViewModel.kt`
- Add a persisted boolean state `reminderVibrationEnabled`
  (key `SmsNotificationHelper.PREF_REMINDER_VIBRATION`, default **false**). The setter just
  persists — no reconcile needed, since the channel is chosen at post time.

### 3. `SmsOrganizerUi.kt`
- In Settings → Reminders "DUE-DATE ALERTS" card, add a **"Vibrate on reminder alerts"** Switch
  (default off), bound to `reminderVibrationEnabled`, with a short description noting that the
  device's per-channel notification settings still apply.

## Files to change

| File | Change |
|---|---|
| `app/.../util/SmsNotificationHelper.kt` | Two reminder channels (novib/vib), read vibration pref, pick channel, pre-O builder vibration, retire legacy channel. |
| `app/.../ui/SmsOrganizerViewModel.kt` | Add `reminderVibrationEnabled` persisted boolean (default false). |
| `app/.../ui/SmsOrganizerUi.kt` | Add the "Vibrate on reminder alerts" Switch in the DUE-DATE ALERTS card. |

No database/schema change. No change to alarm scheduling.

## Design decisions (defaults chosen — tell me to change any)

1. **Default vibration = off** (as requested). Sound is unchanged (still the device's default
   notification tone).
2. **Two-channel approach** rather than delete/recreate, because recreation can't reliably change
   vibration. Worst case the user sees two "Reminder Alerts" channels in system settings after
   toggling; named distinctly to avoid looking like a duplicate.
3. The legacy `sms_sentry_reminders` channel is deleted so upgraded devices stop vibrating; its
   prior user tweaks (if any) are discarded.

## Testing / verification

- Fresh state: a reminder alert posts with **no vibration**; toggling the setting on makes the next
  alert vibrate; toggling off stops it.
- Upgraded install (legacy vibrating channel present): after this build, alerts no longer vibrate
  by default.
- Build via `./gradlew.bat :app:assembleDebug`.
