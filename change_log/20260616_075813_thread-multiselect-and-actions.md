# Conversation thread: multi-select delete, delete-all, and sender actions menu

Implements `plans/20260616_075000_thread-multiselect-and-actions.md`
(which supersedes `plans/20260616_074132_sender-actions-to-thread.md`).

## Why

- Sender-scoped actions (Mute / Block / Report as spam / Delete conversation) were shown on the
  **single-message** `MessageDetailScreen` — the wrong altitude. They belong at the **sender**
  (conversation/thread) level.
- New requested features: select one or multiple messages in a conversation and delete only
  those; plus an option to delete the entire chat.

## Changes

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`

- Added **`deleteMessages(msgs: List<SMSMessage>)`** — deletes an arbitrary selected subset,
  removing each from the system provider when default (mirrors `deleteConversation`).
- Added **`reportSpamSender(sender: String)`** — conversation-wide "report as spam" (moves every
  message from the sender to Spam + adds the `CONTACT -> Spam` rule once); sender-level
  counterpart of the existing message-scoped `reportSpam(msg)`.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

- **`ThreadScreen`**
  - Added multi-select state (`selectionMode`, `selectedIds`, `menuOpen`) and two confirm-dialog
    flags, plus `clearSelection()` / `toggle(id)` helpers and a `context` for Toasts.
  - Header is now two-mode:
    - **Normal:** back + avatar + sender + count, plus a new overflow **`⋮` menu**
      (`thread_menu_button`) with: Mute notifications (On/Off), **Select messages**, Block sender,
      Report as spam, and **Delete entire chat** (red).
    - **Selection:** close `X` (`thread_selection_close`), "{n} selected", and a Delete action
      (`thread_delete_selected`, disabled when nothing selected).
  - `BackHandler(enabled = selectionMode)` exits selection before the thread closes.
  - Two `AlertDialog` confirmations: "Delete N message(s)?" and "Delete entire chat?".
  - Stale selections are pruned in a `LaunchedEffect` (no snapshot writes during composition).
- **`ThreadBubble`**
  - New params `selected`, `selectionMode`, `onClick`, `onLongClick` (replacing `onOpen`).
  - `.clickable` → `.combinedClickable` (`@OptIn(ExperimentalFoundationApi::class)`): tap opens
    the message (or toggles in selection mode); long-press enters selection and toggles.
  - Selected rows show a leading check (`CheckCircle` / `RadioButtonUnchecked`) and a tinted
    background.
- **`MessageDetailScreen`**
  - Removed the "SENDER" action block (Mute / Block / Report / Delete) — now a message-only view.
  - Removed the now-unused `isMuted` local.
- Removed the now-unused private `DetailActionRow` composable.

## Notes

- No DB/model changes. No new dependencies (all icons covered by `material-icons-extended`,
  already a dependency).
- Not compiled here (no Gradle wrapper / SDK on this machine); changes verified by inspection.
- No existing tests referenced the removed SENDER actions or `DetailActionRow`.
