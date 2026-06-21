# Change log: "Move conversation to folder/category" from Inbox selection

Implements [plans/20260618_083357_move-conversation-to-category.md](../plans/20260618_083357_move-conversation-to-category.md).

## Summary

Added a "Move to folder" action to the Inbox conversation-card selection mode, letting the
user move the selected conversation(s) into **Personal / Finance / Reminder / Spam** — a manual
override of the offline classifier. Moving to Spam also blocks the sender's future messages
(per the approved decision) without deleting the existing conversation.

## Files changed

### `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
- Added `suspend fun moveMessageToCategory(message, targetCategory)`:
  - `Spam` → delegates to existing `reportSpam()` (category=Spam, blocked=true) and clears any
    stale Finance/Reminder supplemental rows.
  - Other categories → force-classifies into the target (via `customContacts = mapOf(sender to
    targetCategory)`, the same trick `restoreFromSpam` uses) to extract amount/balance/due-date,
    updates the category, unblocks, and rebuilds the `FinanceTx` / `ReminderSms` supplemental
    rows (mirrors step 4 of `processAndInsertMessage`).

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
- Added `fun moveConversationToCategory(sender, targetCategory)`: moves every message from the
  sender via the new repository method. When target is Spam, also adds a `CONTACT→Spam` rule so
  future messages auto-spam (does NOT delete the conversation, unlike `blockConversation`).

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (`InboxScreen`)
- New state `showMoveSheet`.
- New `DriveFileMove` icon button in the selection action bar (testTag `inbox_move_selected`)
  plus a "Move to folder" item in the overflow menu.
- New chooser `AlertDialog` ("Move N conversation(s) to") listing Personal / Finance / Reminder
  / Spam, each with its `categoryColor` dot. Selecting a row calls
  `viewModelScope`-backed `moveConversationToCategory` for each selected sender, toasts
  "Moved to <label>", clears selection, and dismisses. Rows tagged `inbox_move_to_<category>`.

## Notes / verification
- No DB schema/version change, no new permissions, no classifier changes (category is an
  existing string column).
- `material-icons-extended` is already a dependency, so `Icons.Default.DriveFileMove` resolves.
- Not compiled locally: no `gradle` on PATH and no Gradle wrapper checked in (build from
  Android Studio or an installed `gradle`). Suggested manual check: long-press a conversation →
  tap the move icon → pick each category and confirm the card moves to the matching pill/folder;
  a Finance move with an amount present creates a ledger entry; a Spam move files it under Spam
  and blocks the sender.
