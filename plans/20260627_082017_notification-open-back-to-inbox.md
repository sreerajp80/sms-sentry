# Fix: Back from a notification-opened message goes to Dashboard instead of Inbox

**Status:** completed

## The issue

Tapping a message notification correctly opens the message (the `MessageDetailScreen`
overlay). But pressing **Back** lands on the **Dashboard** tab instead of the **Inbox**, which
is where the message actually lives.

### Root cause

`openedMessage` is an overlay rendered on top of whatever `activeTab` currently is:
- `SmsOrganizerUi.kt:341-348` — when `openedMessage != null`, it shows `MessageDetailScreen`
  with `BackHandler { viewModel.closeMessage() }`. Closing the overlay simply reveals the
  underlying `activeTab`.
- `SmsOrganizerViewModel.kt:46` — `activeTab` defaults to `"Dashboard"`.
- The notification path (`MainActivity.handleOpenMessageIntent` → `viewModel.openMessageById`)
  sets `openedMessage` but never changes `activeTab`. So when launched cold from a
  notification, the tab behind the message detail is still "Dashboard", and Back reveals it.

### Constraint (must not regress finance rows)

`openMessageById` is shared: it is also called from the finance/ledger transaction rows
(`SmsOrganizerUi.kt:3324` and `:3464`), where pressing Back should return to the **Accounts**
tab. Therefore the "go to Inbox" behavior must be applied **only** on the notification path,
not inside `openMessageById`/`openMessage`.

## Files to change

1. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
   - Add a dedicated entry point for notification opens, e.g.
     `fun openMessageFromNotification(messageId: Long)` that:
     - sets `activeTab.value = "Inbox"` (so the message detail sits on top of the Inbox and
       Back returns there), then
     - reuses the existing open logic (`getMessageById(messageId)?.let { openMessage(it) }`).

2. `app/src/main/java/in/sreerajp/sms_sentry/MainActivity.kt`
   - In `handleOpenMessageIntent` (line 96), call `viewModel.openMessageFromNotification(messageId)`
     instead of `viewModel.openMessageById(messageId)`.

(`openMessageById` is left unchanged so finance/ledger rows keep returning to Accounts.)

## Plan for the fix

1. Add `openMessageFromNotification(messageId)` to the ViewModel:
   ```kotlin
   /** Open a message from a notification tap: surfaces it over the Inbox so Back returns there. */
   fun openMessageFromNotification(messageId: Long) {
       activeTab.value = "Inbox"
       openMessageById(messageId)
   }
   ```
   - `activeTab.value` is set synchronously on the main thread; `openMessageById` already
     launches its own coroutine to load + open the message.
2. Point `MainActivity.handleOpenMessageIntent` at the new method.
3. Build/install and verify: tap a notification → message opens → press Back → lands on Inbox.
   Also re-verify a finance/ledger row tap → Back → returns to Accounts (no regression).

## Notes / scope

- "Inbox" is the exact tab id used by `activeTab` (per the comment at
  `SmsOrganizerViewModel.kt:46`: Dashboard, Inbox, Accounts, Reminders, Sync, Settings).
- No DB/schema/manifest changes.
- This is independent of, and can ship alongside, the pending
  `20260627_081453_notification-actions-not-working.md` plan (Open/Delete action buttons).
