# Change log: Read-state controls + selection-menu dedup

Implements plan
[plans/20260627_083628_read-state-controls-and-menu-dedup.md](../plans/20260627_083628_read-state-controls-and-menu-dedup.md).

Confirmed choices: auto-mark-read default 3 s with options Off / 2 / 3 / 5 / 10 / 15 s;
per-message read/unread without a confirmation dialog.

## Data layer

- **`data/SmsDao.kt`** — added `markMessageUnread(id)` (`UPDATE messages SET isRead = 0 WHERE id = :id`)
  beside the existing `markMessageRead`.
- **`data/SmsRepository.kt`** — added `suspend fun markAsUnread(id)` delegating to the new DAO query.

## ViewModel (`ui/SmsOrganizerViewModel.kt`)

- Extended `persistedState(...)` to support `Int` (`getInt`/`putInt`), keeping Boolean/String
  callers unchanged.
- Added persisted `autoMarkReadDelaySeconds` (key `auto_mark_read_secs`, default `3`, `0` = Off).
- Added `markConversationUnread(sender)` — marks every inbound (`TYPE_INBOX`) message from a
  sender unread (mirror of `markConversationRead`).
- Added `markMessagesRead(ids)` / `markMessagesUnread(ids)` for per-message selection actions.

## UI (`ui/SmsOrganizerUi.kt`)

- **Auto-mark-read on open** — `ThreadScreen` now has a `LaunchedEffect` keyed on
  `(sender, autoReadDelay, unreadInboxCount)` that waits `autoReadDelay` seconds then calls
  `markConversationRead(sender)` when enabled and there are unread inbound messages. Leaving the
  thread cancels the pending mark (preview without marking read); new unread messages re-arm it.
- **Advanced Settings → new "Reading" card** — "Auto-mark conversations read" radio list with
  Off / 2 / 3 / 5 / 10 / 15 seconds, bound to `autoMarkReadDelaySeconds`, with helper text.
- **Per-message read/unread** — the thread selection action bar gained two icon buttons before
  Delete: Mark as read (`DoneAll`, tag `thread_mark_read_selected`) and Mark as unread
  (`MarkChatUnread`, tag `thread_mark_unread_selected`); both act immediately and clear selection.
- **Per-conversation unread + menu de-duplication (inbox selection bar)**:
  - The Mark-as-read toolbar icon now opens a confirmation dialog (`showMarkReadDialog`) instead
    of acting immediately.
  - Removed the duplicate overflow-menu entries that already exist as toolbar icons:
    **Mark as read**, **Move to folder**, **Delete**.
  - Added **Mark as unread** (`MarkChatUnread`) to the overflow menu → `markConversationUnread`.
    Overflow menu is now: Mute/Unmute · Mark as unread · Not spam (Spam folder only) · Block sender.
  - Added the Mark-as-read confirmation `AlertDialog` alongside the existing delete dialog.

## Verification

- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- No existing tests reference the affected selection test tags.
