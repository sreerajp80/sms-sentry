# Plan: "Move conversation to folder/category" from Inbox selection

**Status:** completed

## What the user wants

When one or more conversation cards are selected in the Inbox (long-press → selection
mode), add a **"move to folder"** action to the contextual action bar. Tapping it shows a
chooser (like the attached screenshot — "Move 1 conversation to … Personal / Promotions")
that lets the user move the selected conversation(s) into one of the four categories:

- **Personal**
- **Finance**
- **Reminder**
- **Spam**

This is a manual override of the offline classifier's decision.

## The issue / current state

- Inbox selection mode (`InboxScreen`, [SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L949-L1076))
  already has a contextual action bar with: cancel, mark-read, delete, and an overflow menu
  (mute, mark-read, not-spam, block, delete). There is **no "move to category"** action.
- Categories are free-form string literals `"Personal" / "Finance" / "Reminder" / "Spam"`
  (per [docs/architecture.md](../docs/architecture.md)); there is no enum.
- The repository already knows how to change a message's category and rebuild the
  supplemental Finance/Reminder rows: `reportSpam()` (→ Spam + blocked) and
  `restoreFromSpam()` (re-classify, rebuild rows) in
  [SmsRepository.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt#L176-L234).
  `SmsDao` exposes `updateMessageCategory`, `updateMessageBlockedState`,
  `deleteTransactionByMessageId`, `deleteReminderByMessageId`, `insertTransaction`,
  `insertReminder`.
- The classifier can be forced into a chosen category via a CONTACT rule: passing
  `customContacts = mapOf(sender to targetCategory)` makes `SmsClassifier.classify()` return
  `runExtractions(targetCategory, …)`, i.e. it still extracts amount/balance (Finance) or
  due-date (Reminder) but honours the user's chosen category. This is the same trick
  `restoreFromSpam` already uses with `"NotSpam"`.

So the data plumbing exists; what's missing is (1) a repository method to move a message to
an **explicit** category and rebuild its supplemental rows, (2) a ViewModel method to do
this for a whole conversation (all messages of a sender), and (3) the UI action + chooser.

## Files to change

1. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`**
   - Add `suspend fun moveMessageToCategory(message: SMSMessage, targetCategory: String)`:
     - If `targetCategory == "Spam"` → delegate to existing `reportSpam(message)` (sets
       category=Spam, blocked=true) **and** clear any stale Finance/Reminder rows for the
       message id. Return.
     - Otherwise force-classify into the target to extract supplemental fields:
       `SmsClassifier.classify(message.sender, message.body, customKeywords = emptyList(),
       customContacts = mapOf(message.sender to targetCategory))`.
     - `updateMessageCategory(message.id, targetCategory)`,
       `updateMessageBlockedState(message.id, false)`.
     - Clear existing supplemental rows (`deleteTransactionByMessageId` /
       `deleteReminderByMessageId`), then, mirroring `restoreFromSpam`, insert a `FinanceTx`
       when target is Finance and an amount was extracted, or a `ReminderSms` when target is
       Reminder and a due-date was extracted. (If the target is Personal, or Finance/Reminder
       with nothing extractable, no supplemental row is created — the move still happens.)

2. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`**
   - Add `fun moveConversationToCategory(sender: String, targetCategory: String)` that, in
     `viewModelScope`, takes `allMessages.value.filter { it.sender == sender }` and calls
     `repository.moveMessageToCategory(m, targetCategory)` for each. (Operates per-conversation
     like the existing `markConversationRead` / `reportSpamSender` / `markNotSpamSender`.)
   - Note: moving to Spam **files + blocks** — it re-uses `reportSpam` semantics for each
     message (category=Spam, blocked=true, messages stay visible in the Spam folder) **and**
     adds a `CONTACT→Spam` rule (`repository.addRule("CONTACT", sender, "Spam")`) so future
     messages from this sender are auto-spammed. Unlike the existing `blockConversation`, it
     does **not** delete the conversation — the messages remain in the Spam folder.

3. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`** (`InboxScreen`)
   - Add local state `var showMoveSheet by remember { mutableStateOf(false) }`.
   - In the selection action bar (around line 977-1001, next to mark-read / delete), add an
     icon button — `Icons.Default.DriveFileMove` (extended set; already used for
     `MarkEmailRead`/`NotificationsOff` so the extended icons are available — will fall back
     to `Icons.Default.Folder` if `DriveFileMove` is unavailable), `contentDescription =
     "Move to folder"`, `testTag = "inbox_move_selected"` — that sets `showMoveSheet = true`.
     Also add a matching "Move to…" item in the overflow `DropdownMenu` for discoverability.
   - Add a chooser dialog (an `AlertDialog` with a `Column` of four selectable rows, titled
     `"Move ${selectedSenders.size} conversation(s) to"` — matching the screenshot). Rows:
     Personal, Finance, Reminder, Spam, each with its `categoryColor(...)` dot/icon. Picking a
     row calls `selectedSenders.toList().forEach { viewModel.moveConversationToCategory(it,
     category) }`, shows a `Toast` ("Moved to <category>"), then `clearSelection()` and closes
     the sheet. Disable/skip the row matching the current `activeInboxFolder` is optional;
     simplest is to always show all four.

## Out of scope (not changing now)

- Per-message move inside `ThreadScreen` (the request is specifically about the **conversation
  card** selection in the Inbox). Can be a follow-up if wanted.
- No DB schema change → **no `SmsDatabase` version bump** (categories are existing string
  columns). No new permissions.
- No change to auto-classification, rules, or P2P sync.

## Testing / verification

- Build the app (`docs/build-and-test.md`; no Gradle wrapper).
- Manual: long-press a conversation → tap the move icon → pick each category and confirm the
  card moves to the right pill/folder, Finance moves create a ledger entry when an amount is
  present, and moving to Spam files it under the Spam folder.
- Existing selection tests (testTags `inbox_*`) remain; new control adds
  `inbox_move_selected`.
