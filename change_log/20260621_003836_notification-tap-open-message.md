# Change log — Notification tap now opens the tapped message

Implements [plans/20260621_003529_notification-tap-open-message.md](../plans/20260621_003529_notification-tap-open-message.md).

## Problem

Tapping an incoming-SMS notification (or its "Open" action) just launched SMS
Sentry's default screen instead of opening the message the notification was
about. The tap intents launched `MainActivity` with no message identifier, and
`MainActivity` never read one.

## Changes

### `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`
- Added public constant `EXTRA_OPEN_MESSAGE_ID`
  (`"in.sreerajp.sms_sentry.OPEN_MESSAGE_ID"`).
- `showNotification()`: content (tap) intent now carries
  `EXTRA_OPEN_MESSAGE_ID = messageId` when `messageId > 0`.
- `updateNotificationWithCopiedState()`: its content intent carries the same
  extra, so tapping the OTP "Copied" notification also opens the message.
- The non-OTP **"Open"** action broadcast now includes `message_id`.

### `app/src/main/java/in/sreerajp/sms_sentry/receiver/NotificationActionReceiver.kt`
- `ACTION_OPEN_APP` branch reads `message_id` and forwards it as
  `SmsNotificationHelper.EXTRA_OPEN_MESSAGE_ID` on the `MainActivity` launch
  intent (when `> 0`).

### `app/src/main/java/in/sreerajp/sms_sentry/MainActivity.kt`
- Added import for `SmsNotificationHelper`.
- Added `handleOpenMessageIntent(intent)` that, when the intent carries a valid
  `EXTRA_OPEN_MESSAGE_ID`, calls `viewModel.openMessageById(id)` and removes the
  extra so it is not re-handled on config changes / re-delivery.
- Invoked from both `onCreate()` and `onNewIntent()`.

## Behavior

Tapping a message notification (body, OTP "Copied" body, or "Open" action) now
opens that specific message's detail screen and marks it read, reusing the
existing `SmsOrganizerViewModel.openMessageById(...)`. If the underlying message
was deleted, it safely no-ops (app simply opens — the previous behavior).

No schema, manifest, or permission changes.

## Verification

- `./gradlew.bat :app:compileDebugKotlin` succeeds (no Kotlin errors).
