# Notification tap does not open the tapped message

**Status:** completed

## Issue

When a new-SMS notification arrives and the user taps it (or taps the "Open"
action button), SMS Sentry just launches to its default/home screen instead of
opening the specific message that the notification was about.

### Root cause

In [`SmsNotificationHelper`](../app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt)
the tap (content) `PendingIntent` is built as a bare launch of `MainActivity`
with **no extra identifying the message**:

```kotlin
val contentIntent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
}
```

The same is true for:
- `updateNotificationWithCopiedState()` content intent (OTP "Copied" state).
- The non-OTP **"Open"** action in
  [`NotificationActionReceiver`](../app/src/main/java/in/sreerajp/sms_sentry/receiver/NotificationActionReceiver.kt)
  (`ACTION_OPEN_APP`), which also just launches `MainActivity`.

Meanwhile `MainActivity` only inspects the intent for `sms:/smsto:` compose
links — it never reads a "open this message" extra. So the `messageId` that is
already passed into `showNotification(...)` is never used for navigation.

The ViewModel already has everything needed: `openMessageById(messageId: Long)`
loads the message and sets `openedMessage`, which drives the detail screen (and
marks it read). It also no-ops safely if the message was deleted.

## Plan for the fix

Thread the `messageId` from the notification through to
`viewModel.openMessageById(...)`.

1. **`SmsNotificationHelper.kt`**
   - Define a shared extra key constant, e.g.
     `const val EXTRA_OPEN_MESSAGE_ID = "in.sreerajp.sms_sentry.OPEN_MESSAGE_ID"`.
   - In `showNotification()`, add `putExtra(EXTRA_OPEN_MESSAGE_ID, messageId)` to
     `contentIntent` (only when `messageId > 0`).
   - In `updateNotificationWithCopiedState()`, add the same extra to its
     `contentIntent` so tapping the "Copied" OTP notification also opens the
     message.

2. **`NotificationActionReceiver.kt`** (`ACTION_OPEN_APP` branch)
   - Read `message_id` from the incoming broadcast intent and forward it as
     `EXTRA_OPEN_MESSAGE_ID` on the `MainActivity` launch intent (when valid).
   - Update the `ACTION_OPEN_APP` `PendingIntent` builder in
     `SmsNotificationHelper` to include `message_id` so the receiver has it.

3. **`MainActivity.kt`**
   - Add `handleOpenMessageIntent(intent)` that reads
     `EXTRA_OPEN_MESSAGE_ID` and, when valid (`> 0`), calls
     `viewModel.openMessageById(id)`.
   - Call it from both `onCreate()` (after `handleSendIntent`) and
     `onNewIntent()`.

### Files to be changed

- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`
- `app/src/main/java/in/sreerajp/sms_sentry/receiver/NotificationActionReceiver.kt`
- `app/src/main/java/in/sreerajp/sms_sentry/MainActivity.kt`

### Notes / risk

- The content `PendingIntent` request code is the unique per-notification
  `notificationId`, so adding extras won't collide with other notifications;
  `FLAG_UPDATE_CURRENT` will refresh extras correctly.
- `openMessageById` is coroutine-based and safely no-ops if the message no
  longer exists, so a stale tap degrades to "app just opens" (current behavior),
  never a crash.
- No DB/schema or manifest changes required. No new permissions.
