# Change log: Show SIM identifier in conversation message bubble

Implements [plans/20260901_051800_show_sim_in_message_bubble.md](../plans/20260901_051800_show_sim_in_message_bubble.md).

## What changed

- In the conversation thread view (`ThreadBubble`), added the SIM slot indicator (e.g. `SIM 1` / `SIM 2`) to the metadata footer beneath message bubbles:
  - For **sent** messages: appears after delivery status (e.g., `13 Jul · 01:35 PM · Delivered · SIM 1` or `19 May · 07:36 PM · Sent · SIM 1`).
  - For **received** messages: appears after the timestamp (e.g., `20 Jul · 06:58 PM · SIM 1`).
  - For **failed** messages: appears alongside the status and retry prompt (e.g., `Failed · SIM 1 · Tap to retry`).
- In the message detail view (`DetailView`), mirrored the SIM slot indicator in the timestamp/status footer row.

## Files changed

- **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
  - Updated `ThreadBubble` metadata row to render ` · SIM $simSlot` after status / timestamp.
  - Updated `DetailView` metadata row to render ` · SIM $simSlot` after status / timestamp.

## Verification

- `./gradlew testDebugUnitTest` — all unit tests pass.
- `./gradlew assembleDebug` — debug APK built successfully.
