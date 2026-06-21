# Change log: conversation/detail screens no longer draw behind the status bar

Implements plan `plans/20260617_214643_thread-status-bar-inset.md`.

## Problem

With `enableEdgeToEdge()` active, only the `Scaffold` in `SmsOrganizerApp` consumed
window insets (for the tab screens). The `ThreadScreen` and `MessageDetailScreen`
full-screen overlays are rendered in the outer `Box` outside the `Scaffold`, so their
headers drew behind the system status bar (overlapping the clock/battery/signal icons),
and the thread's reply composer sat under the gesture nav bar / keyboard.

## Changes

File: `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

1. **`ThreadScreen` root `Column`** — added `.statusBarsPadding()`,
   `.navigationBarsPadding()`, and `.imePadding()` (before `.background(...)`).
   Fixes the header overlapping the status bar and lifts the reply box above the
   nav bar / keyboard.

2. **`MessageDetailScreen` root `Column`** — added `.statusBarsPadding()` and
   `.navigationBarsPadding()` (before `.background(...)`). Fixes the header
   overlapping the status bar.

No imports added — `statusBarsPadding`, `navigationBarsPadding`, and `imePadding` are
covered by the existing wildcard import `androidx.compose.foundation.layout.*`
(`SmsOrganizerUi.kt:15`). Tab screens are unchanged (still handled by the `Scaffold`).
