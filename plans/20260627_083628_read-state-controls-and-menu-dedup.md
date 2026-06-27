# Plan: Read-state controls (auto-mark-read, per-message/per-conversation unread) + selection-menu dedup

**Status:** completed

## Issues / goals

1. **Auto-mark-read on conversation open.** When a conversation with unread messages is
   opened, after a delay (default 3 s) mark all of its messages read. Today nothing marks a
   thread read on open — only tapping an individual bubble (`openMessage`) marks that one read.

2. **Configurable delay in Advanced Settings.** Add a setting letting the user pick the delay
   (including "Off") after which an opened conversation is auto-marked read.

3. **Per-message mark read/unread.** Today a message can only become read (by tapping it).
   Add the ability to mark selected message(s) read *or* unread.

4. **Per-conversation mark unread.** Today the inbox selection bar only offers "Mark as read".
   Add "Mark as unread" for conversations.

5. **Selection menu de-duplication + confirmations (the screenshot).** The inbox selection
   overflow (⋮) menu repeats actions that already have toolbar icons (Mark as read, Move to
   folder, Delete). Remove those duplicate menu entries. Add a confirmation dialog for the
   **Mark as read** icon and (already present) keep the **Delete** confirmation.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt` — add `markMessageUnread`.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt` — add `markAsUnread`.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt` — read/unread ops +
  persisted `autoMarkReadDelaySeconds` + `Int` support in `persistedState`.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — auto-read effect, Advanced
  Settings entry, thread per-message read/unread, inbox conversation unread, menu dedup +
  mark-read confirmation.

## Plan

### Data layer
- **SmsDao.kt**: add `@Query("UPDATE messages SET isRead = 0 WHERE id = :id") suspend fun
  markMessageUnread(id: Long)` next to the existing `markMessageRead`.
- **SmsRepository.kt**: add `suspend fun markAsUnread(id: Long) = smsDao.markMessageUnread(id)`
  beside `markAsRead`.

### ViewModel (`SmsOrganizerViewModel.kt`)
- Extend `persistedState(...)` `when` block to handle `Int` (`prefs.getInt(key, default)` /
  saver uses `putInt`). Saver lambda for the new state writes the int.
- Add persisted state: `var autoMarkReadDelaySeconds = persistedState("auto_mark_read_secs", 3) { prefs.edit().putInt("auto_mark_read_secs", it).apply() }`
  (0 = Off).
- Add functions:
  - `fun markMessagesRead(ids: Collection<Long>)` → loop `repository.markAsRead(id)`.
  - `fun markMessagesUnread(ids: Collection<Long>)` → loop `repository.markAsUnread(id)`.
  - `fun markConversationUnread(sender: String)` → mark every inbox (`TYPE_INBOX`) message
    from the sender unread (mirror of `markConversationRead`).
- Keep `markConversationRead` as-is.

### Auto-mark-read on open (`ThreadScreen`, ~line 1839)
- Read `val autoDelay by viewModel.autoMarkReadDelaySeconds`.
- Add a `LaunchedEffect(sender, autoDelay, threadMessages.count { !it.isRead && it.type == SMSMessage.TYPE_INBOX })`
  that, when `autoDelay > 0` and there are unread inbox messages, `delay(autoDelay * 1000L)`
  then calls `viewModel.markConversationRead(sender)`.
- Leaving the thread (or new messages arriving) cancels/re-arms the timer naturally because
  the effect is keyed on the unread count and is scoped to the thread composition. This gives
  the intended "preview without marking read if you leave quickly" behavior.

### Advanced Settings entry (`AdvancedSettingsPage`, ~line 4599)
- Add a new card section **"Reading"** → "Auto-mark conversations read", mirroring the
  Multi-SIM radio-list pattern. Options: **Off, 2s, 3s, 5s, 10s, 15s** mapped to ints
  `0,2,3,5,10,15` (default 3). Bind selection to `viewModel.autoMarkReadDelaySeconds`.
  Include a one-line helper text:
  "When you open a conversation, its messages are marked read after this delay."

### Per-message mark read/unread (thread selection action bar, ~line 1892–1930)
- In the thread's selection header (currently just a Delete icon), add two icon buttons before
  Delete:
  - **Mark read** — `Icons.Default.DoneAll`, testTag `thread_mark_read_selected` →
    `viewModel.markMessagesRead(selectedIds); clearSelection()`.
  - **Mark unread** — `Icons.Default.MarkChatUnread`, testTag `thread_mark_unread_selected` →
    `viewModel.markMessagesUnread(selectedIds); clearSelection()`.
  (Per-message read/unread is non-destructive → no confirmation, to avoid friction.)

### Inbox conversation: add "Mark as unread" + menu dedup + confirmation (~line 1023–1166)
- **Keep** the three toolbar icons: Mark as read (`DoneAll`), Move to folder (`DriveFileMove`),
  Delete (`Delete`).
- **Mark as read icon** now opens a confirmation dialog `showMarkReadDialog` instead of acting
  immediately. Dialog: title "Mark N conversation(s) as read?", confirm → mark read + clear.
- **Overflow (⋮) menu** — remove the duplicated entries **Mark as read**, **Move to folder**,
  and **Delete**. Resulting menu: Mute/Unmute notifications · **Mark as unread** (new) ·
  Not spam (only in Spam folder) · Block sender.
  - "Mark as unread" → `selectedSenders.forEach { viewModel.markConversationUnread(it) }; clearSelection()`
    (non-destructive → no confirmation).
- Add `var showMarkReadDialog by remember { mutableStateOf(false) }` to inbox selection state,
  and render its `AlertDialog` next to the existing `showDeleteSelectedDialog` block.

## Notes / risks
- New DB column not needed — `isRead` already exists; we only add an UPDATE-to-0 query.
- `persistedState` Int branch is additive and backward compatible with existing Boolean/String
  callers.
- Test tags preserved/added; I'll grep for affected instrumentation (none currently reference
  these selection tags).
- The thread overflow menu (single open conversation) keeps "Delete entire chat" (it has no
  toolbar-icon duplicate), so the dedup rule there does not apply.

## Confirmed design choices
- Auto-mark-read default = **3 s**, options **Off / 2 / 3 / 5 / 10 / 15 s**.
- Per-message mark-read/unread lives on the **thread selection bar** (long-press a bubble),
  with **no** confirmation; confirmation is applied only to conversation-level Mark-as-read and
  Delete, per your screenshot note.
