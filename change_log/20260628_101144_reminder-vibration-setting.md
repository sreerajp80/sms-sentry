# Change log: reminder alerts — vibration off by default + Settings toggle

Implements plan
[plans/20260628_101144_reminder-vibration-setting.md](../plans/20260628_101144_reminder-vibration-setting.md).

## Summary

Reminder due-alerts previously always vibrated (the reminder channel was created with
`enableVibration(true)`). Vibration is now **off by default** and **user-toggleable** from Settings.
Because a `NotificationChannel`'s vibration is immutable after creation on Android 8+, this is done
with **two channels that differ only in vibration**, posting to whichever matches the setting (and
builder-level vibration on pre-O). Sound is unchanged.

## Changes by file

### `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`
- Replaced the single `REMINDER_CHANNEL_ID` with two ids differing only in vibration:
  `REMINDER_CHANNEL_NOVIB_ID` ("Reminder Alerts", `enableVibration(false)`) and
  `REMINDER_CHANNEL_VIB_ID` ("Reminder Alerts (Vibration)", `enableVibration(true)`). Both keep
  `IMPORTANCE_HIGH`, lights, and the existing default sound.
- Added `PREF_REMINDER_VIBRATION = "reminder_vibration_enabled"` (default **false**) and
  `REMINDER_CHANNEL_LEGACY_ID = "sms_sentry_reminders"`.
- `showReminderNotification(...)`: reads the vibration pref, picks the matching channel, builds the
  notification on it. On O+ it also deletes the legacy `sms_sentry_reminders` channel (so upgraded
  installs stop vibrating). On pre-O it sets vibration on the builder
  (`setVibrate(longArrayOf(0L))` when off, a short pattern when on).

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
- Added persisted boolean `reminderVibrationEnabled` (key
  `SmsNotificationHelper.PREF_REMINDER_VIBRATION`, default false). Setter only persists — the
  channel is chosen at post time, so no reconcile is needed.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Added a **"Vibrate on reminder alerts"** Switch (default off) to the Settings → Reminders
  "DUE-DATE ALERTS" card, below the day steppers, with a note that the device's per-channel
  settings still apply.

## Notes / behavior
- Default is **no vibration**; sound remains the device's default notification tone.
- Trade-off of the two-channel approach: a user who toggles vibration on then off may see two
  "Reminder Alerts" entries in the *system* notification settings (named distinctly). This is the
  cost of a reliable in-app toggle, since channel vibration cannot be changed after creation.

## Verification
- `./gradlew.bat :app:assembleDebug` — **BUILD SUCCESSFUL**.
- No schema change; no Room migration.
