# Fix: conversation/detail screens draw behind the system status bar

## The issue

On a real phone the conversation (thread) screen's header — back arrow, avatar,
sender/title ("Protected"), and the overflow menu — renders **behind** the device
status bar, so the app header overlaps the system clock, battery, and signal icons.

## Root cause

`MainActivity` calls `enableEdgeToEdge()`, which makes the app draw edge-to-edge
(content under the system bars). Window insets are only consumed by the **`Scaffold`**
in `SmsOrganizerApp` (via its `innerPadding`), which wraps the *tab* screens
(Dashboard, Inbox, Finance, …).

The conversation thread and the single-message detail screens are rendered as
**full-screen overlays in the outer `Box`, outside the `Scaffold`**:

- `ThreadScreen` — `SmsOrganizerUi.kt:301`, root `Column` at `SmsOrganizerUi.kt:1285`
- `MessageDetailScreen` — `SmsOrganizerUi.kt:313`, root `Column` at `SmsOrganizerUi.kt:2074`

Their root `Column`s use `.fillMaxSize().background(...)` with **no status-bar inset
padding**, so their headers slide under the status bar. (The "New message" screen at
`SmsOrganizerUi.kt:4519` already does this correctly with
`.imePadding().navigationBarsPadding().statusBarsPadding()`.)

The same overlays also have no bottom inset, so the thread's reply composer sits under
the gesture navigation bar and does not rise with the keyboard.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

## Plan for the fix

1. **`ThreadScreen` root `Column` (line ~1285)** — add window-inset padding so the
   header clears the status bar and the reply box clears the nav bar / keyboard:
   ```
   .fillMaxSize()
   .statusBarsPadding()
   .navigationBarsPadding()
   .imePadding()
   .background(MaterialTheme.colorScheme.background)
   .testTag("thread_${sender}")
   ```

2. **`MessageDetailScreen` root `Column` (line ~2074)** — add the top/bottom system-bar
   insets (no IME field here, so `imePadding()` is optional/omitted):
   ```
   .fillMaxSize()
   .statusBarsPadding()
   .navigationBarsPadding()
   .background(MaterialTheme.colorScheme.background)
   .testTag("message_detail_${msg.id}")
   ```

No new imports needed — `statusBarsPadding`, `navigationBarsPadding`, and `imePadding`
are already imported and used at `SmsOrganizerUi.kt:4522-4524`.

## Verification

- Rebuild and open a conversation on the device: header should sit fully below the
  status bar; reply box should sit above the gesture nav bar and rise with the keyboard.
- Open a single message detail: header should sit below the status bar.
- Tab screens are unchanged (still handled by the `Scaffold`).
