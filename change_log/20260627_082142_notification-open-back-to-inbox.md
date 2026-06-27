# Change log: Back from a notification-opened message now returns to Inbox

Implements plan `plans/20260627_082017_notification-open-back-to-inbox.md`.

## Problem

Tapping a message notification opened the message detail correctly, but pressing Back landed
on the **Dashboard** tab instead of the **Inbox**. Cause: the message detail is an overlay on
top of `activeTab`, and the notification path never changed `activeTab` from its default
`"Dashboard"`.

## Changes

1. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
   - Added `openMessageFromNotification(messageId: Long)`, which sets `activeTab.value = "Inbox"`
     and then delegates to `openMessageById(messageId)`. `openMessageById` is left unchanged so
     finance/ledger transaction rows still return to the Accounts tab on Back (no regression).

2. `app/src/main/java/in/sreerajp/sms_sentry/MainActivity.kt`
   - `handleOpenMessageIntent` now calls `viewModel.openMessageFromNotification(messageId)`
     instead of `viewModel.openMessageById(messageId)`.

## Scope / notes

- No DB/schema/manifest changes.
- Verification: tap a notification → message opens → Back returns to Inbox; finance/ledger row
  tap → Back returns to Accounts (unchanged).
