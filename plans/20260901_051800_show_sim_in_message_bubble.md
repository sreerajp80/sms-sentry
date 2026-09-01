# Plan: Show SIM identifier in conversation message bubble

**Status:** completed

## What the user wants

In the conversation thread view (`ThreadBubble`), show the SIM through which the message was sent or received to easily identify it:
- For **sent** messages: display the SIM after the delivery status (e.g., `13 Jul · 01:35 PM · Delivered · SIM 1` or `19 May · 07:36 PM · Sent · SIM 1`).
- For **received** messages: display the SIM after the timestamp (e.g., `20 Jul · 06:58 PM · SIM 1`).

Also mirror this in the message detail view (`DetailView`) below the message text for consistency.

## The issue / gap

Currently, `ThreadBubble` in `SmsOrganizerUi.kt` displays:
- For incoming messages: `timeText`
- For sent messages: `timeText` + ` · $statusLabel` (where statusLabel is "Sent", "Delivered", "Sending…", or "Failed")

It does not display the SIM slot (`msg.simId`) beneath the bubble, making it hard to see which SIM a message was received on or sent through without opening the full message details.

## Files to be changed

1. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
   - In `ThreadBubble`: add `" · SIM ${if (msg.simId > 0) msg.simId else 1}"` after `statusLabel` (if present) or `timeText` (for incoming messages), formatted in matching subtle secondary text (`MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)`).
   - In `DetailView`: add the same SIM indicator in the timestamp/status footer row.

## Verification plan

- Run unit tests: `./gradlew testDebugUnitTest`
- Assemble debug APK: `./gradlew assembleDebug`
