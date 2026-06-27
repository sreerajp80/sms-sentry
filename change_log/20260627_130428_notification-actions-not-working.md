# Change log: Notification "Open" / "Delete" actions now work

Implements plan `plans/20260627_081453_notification-actions-not-working.md`.

## Problem

On the non-OTP SMS notification, the **Open** action did nothing and **Delete** appeared to do
nothing. Root cause for Open: `targetSdk = 36`, so the Android 12+ notification-trampoline
restriction silently blocked the `startActivity()` call made from `NotificationActionReceiver`
in response to the broadcast. Delete's DB logic was sound but the path was hardened for clarity.

## Changes

1. `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`
   - "Open" action now uses `PendingIntent.getActivity()` launching `MainActivity` directly
     (with `EXTRA_OPEN_MESSAGE_ID`), instead of a broadcast to `NotificationActionReceiver`
     that called `startActivity()`. This removes the notification trampoline.
   - Added `EXTRA_CANCEL_NOTIFICATION_ID` constant; the "Open" action intent carries the
     notification id so the app can dismiss the notification (action buttons are not covered by
     `setAutoCancel`, which only fires for the body/content tap).

2. `app/src/main/java/in/sreerajp/sms_sentry/MainActivity.kt`
   - `handleOpenMessageIntent` now also reads `EXTRA_CANCEL_NOTIFICATION_ID` and cancels that
     notification after opening the message, so pressing "Open" dismisses the notification.

3. `app/src/main/java/in/sreerajp/sms_sentry/receiver/NotificationActionReceiver.kt`
   - Removed the now-unused `ACTION_OPEN_APP` branch and the `MainActivity` import.
   - Hardened `ACTION_DELETE_MESSAGE`: uses `applicationContext`, tracks a `deleted` flag, and
     posts the toast on the main thread inside `finally` only after the delete runs — showing
     "SMS Deleted" on success or "Couldn't delete SMS" on failure. The delete itself
     (`deleteMessageById` + tx/reminder cleanup on the singleton DB) is unchanged.

4. `app/src/main/AndroidManifest.xml`
   - Removed the `in.sreerajp.sms_sentry.ACTION_OPEN_APP` action from the
     `NotificationActionReceiver` intent-filter (no longer used).

## Verification

- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- On-device (recommended): tap "Open" → app opens to the message and the notification is
  dismissed; tap "Delete" → message removed from the inbox list, notification dismissed, and a
  "SMS Deleted" toast appears. OTP notifications (Copy/Delete custom layout) are unaffected.

## Notes

- When the app is the default SMS app, Delete still only removes the message from the app's Room
  DB, not the system SMS provider (left out of scope per the plan).
