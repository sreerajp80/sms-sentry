# Fix: Notification "Open" / "Delete" actions do nothing

**Status:** completed

## The issue

When a non-OTP SMS notification is shown (e.g. "💬 50350300 (SIM 1)"), tapping the
**Open** or **Delete** action buttons does nothing.

### Root cause — "Open" (confirmed)

`targetSdk = 36`, so the **Android 12 (API 31) notification-trampoline restriction**
applies. The "Open" action sends a broadcast to `NotificationActionReceiver`, which then
calls `context.startActivity(...)`:

- `SmsNotificationHelper.kt:289-300` — builds an "Open" action whose `PendingIntent` is a
  **broadcast** to `NotificationActionReceiver` with action `ACTION_OPEN_APP`.
- `NotificationActionReceiver.kt:70-79` — on `ACTION_OPEN_APP` it calls
  `context.startActivity(openIntent)`.

Since Android 12, an app **cannot start an Activity from a BroadcastReceiver (or Service)
that is used as a notification trampoline**. The platform silently blocks the activity start
(logcat: `Indirect notification activity start (trampoline) blocked`). Result: tapping
"Open" does nothing.

### "Delete" — requirement: Delete must delete the message

The Delete DB path is sound:
- `SmsNotificationHelper.kt:276-287` builds the Delete broadcast `PendingIntent`.
- `NotificationActionReceiver.kt:51-69` deletes the message (and its tx/reminder rows) on an
  IO coroutine via `goAsync()`, shows a "SMS Deleted" toast, and the notification is
  cancelled at lines 82-85. `messageId` is a valid Room rowId (`insertMessage` return), so
  the `messageId != -1L` guard passes.
- `SmsDao.deleteMessageById` is `DELETE FROM messages WHERE id = :id` (correct), and
  `SmsDatabase.getDatabase` is a proper `@Volatile` singleton, so Room's invalidation tracker
  refreshes the on-screen list immediately after the delete.

So **when the broadcast reaches the receiver, the message IS deleted from the app database
and the list updates.** The reported "Delete does nothing" is the same broadcast-delivery
context as "Open" (likely a stale notification or an OEM restriction), not a defect in the
delete logic. This plan keeps the delete logic intact and only hardens the toast to fire on
success, so the outcome is unambiguous and verifiable on-device.

Scope note: when the app is the default SMS app, the message also exists in the **system SMS
provider** (`systemStore.writeInbox`). The current Delete only removes it from the app's Room
DB (which is what the app's own list shows). Deleting from the system provider too is **out of
scope** for this fix unless requested.

## Files to change

1. `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`
   - Change the **"Open"** action (non-OTP branch, lines ~289-300) from
     `PendingIntent.getBroadcast(... NotificationActionReceiver ... ACTION_OPEN_APP)` to a
     direct `PendingIntent.getActivity(...)` that launches `MainActivity` with the
     `EXTRA_OPEN_MESSAGE_ID` extra (mirroring the existing content intent at lines 185-194).
     This removes the trampoline entirely.

2. `app/src/main/java/in/sreerajp/sms_sentry/receiver/NotificationActionReceiver.kt`
   - Remove the now-unused `ACTION_OPEN_APP` branch (lines 70-79) and the `MainActivity`
     import, since "Open" no longer routes through the receiver. (Keep `ACTION_COPY_OTP` and
     `ACTION_DELETE_MESSAGE`.)
   - Harden `ACTION_DELETE_MESSAGE`: move the "SMS Deleted" toast to fire only after the
     delete completes (inside the coroutine, posted to the main thread), so the toast
     truthfully reflects success, and add a `Log`/`e.printStackTrace` already present stays.
     (Low-risk; keeps `goAsync()`.)

3. `app/src/main/AndroidManifest.xml`
   - Remove the `<action android:name="in.sreerajp.sms_sentry.ACTION_OPEN_APP" />` line from
     the `NotificationActionReceiver` intent-filter (lines 129-137), since that action is no
     longer used.

> Note: the OTP branch already uses a custom RemoteViews layout with only Copy/Delete (no
> Open), so it is unaffected. The `updateNotificationWithCopiedState` path is also unaffected.

## Plan for the fix

1. In `SmsNotificationHelper.showNotification`, replace the "Open" broadcast PendingIntent
   with `PendingIntent.getActivity(context, notificationId + 2, openActivityIntent,
   FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`, where `openActivityIntent` targets `MainActivity`
   with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` and the `EXTRA_OPEN_MESSAGE_ID`
   extra. Keep `builder.addAction(0, "Open", pendingOpenActivityIntent)`.
   - Because the action is now a `getActivity` PendingIntent and the builder has
     `setAutoCancel(true)`, the notification auto-dismisses when "Open" is tapped.
2. Delete the `ACTION_OPEN_APP` branch from `NotificationActionReceiver` and its now-unused
   import; verify the trailing `cancel(notificationId)` block still behaves correctly for the
   remaining Delete action (it does — Delete still passes `notification_id`).
3. Harden the Delete toast to post on success.
4. Remove the `ACTION_OPEN_APP` action from the manifest intent-filter.
5. Build (`docs/build-and-test.md`), install on the device, send/seed a non-OTP SMS, and
   verify: "Open" launches the app to the message; "Delete" removes the message + dismisses
   the notification + shows the toast.

## Risk / notes

- No DB schema or data changes.
- The trampoline fix is the standard, documented approach (Open = direct activity
  PendingIntent).
- If on-device testing shows Delete still fails, the next suspect is an OEM autostart/battery
  restriction on the manifest receiver; we would then capture logcat to confirm whether
  `onReceive` is invoked.
