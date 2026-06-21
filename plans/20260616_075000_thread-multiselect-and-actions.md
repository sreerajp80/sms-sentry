# Conversation thread: multi-select delete, delete-all, and sender actions menu

> **Supersedes** `plans/20260616_074132_sender-actions-to-thread.md`. That plan (move the
> sender actions from the per-message detail to the thread level) is folded into this one,
> because the new "delete entire chat" request is the same action and touches the same header.

## What the user asked for

On the **Conversation screen** (`ThreadScreen`, the chat-style per-sender history):

1. **Select one or multiple messages** and delete only the selected ones.
2. An option to **delete the entire chat / all messages**.

Plus the previously-diagnosed issue (carried over): the **sender-scoped actions**
(Mute / Block / Report as spam / Delete conversation) currently live on the *single-message*
`MessageDetailScreen`, which is the wrong altitude — they belong at the **sender level**
(the thread).

## Files to change

### 1. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

**`ThreadScreen`** (~1248–1409) — add a selection mode and an overflow menu.

- New local state:
  - `var selectionMode by remember { mutableStateOf(false) }`
  - `val selectedIds = remember { mutableStateListOf<Long>() }` (message ids)
  - `var menuOpen by remember { mutableStateOf(false) }`
  - Helper lambdas: `enterSelection(id)`, `toggle(id)`, `clearSelection()` (clears + exits mode).
- **Header has two states:**
  - **Normal header** (current layout) + a trailing **overflow `⋮` (`Icons.Default.MoreVert`)**
    `IconButton` (testTag `thread_menu_button`) opening a `DropdownMenu` with:
    - **Mute notifications** — trailing "On/Off" from `viewModel.isMuted(sender)`;
      `onClick` → `viewModel.toggleMute(sender)` + existing muted/unmuted Toast.
    - **Select messages** — `onClick` → `selectionMode = true` (lets the user enter selection
      without needing to discover long-press).
    - **Block sender** — `viewModel.blockConversation(sender)` then `viewModel.closeThread()`.
    - **Report as spam** — `viewModel.reportSpamSender(sender)` + "Reported as spam" Toast,
      then `viewModel.closeThread()`.
    - **Delete entire chat** (red `spamColor()`) — confirm dialog, then
      `viewModel.deleteConversation(sender)` + `viewModel.closeThread()`.
  - **Selection header** (shown when `selectionMode`): a **close `X`** `IconButton`
    (`clearSelection()`), a **"{n} selected"** title, and a **Delete** `IconButton`
    (testTag `thread_delete_selected`) → confirm dialog → delete the selected messages, then
    `clearSelection()`. Delete is disabled/greyed when `selectedIds` is empty.
- **`BackHandler(enabled = selectionMode) { clearSelection() }`** inside `ThreadScreen` so the
  system back button exits selection first (the outer `BackHandler` at the overlay call site
  still closes the thread when not selecting).
- **Confirmation dialogs** (`AlertDialog`): one for "Delete N selected message(s)?" and one for
  "Delete entire conversation with {sender}?" — both destructive, so guard with a confirm.

**`ThreadBubble`** (~1411–1493) — make it selection-aware.

- New params: `selected: Boolean`, `selectionMode: Boolean`, `onClick: () -> Unit`,
  `onLongClick: () -> Unit` (replace the single `onOpen`).
- Replace `.clickable(onClick = onOpen)` on the bubble `Box` with
  `.combinedClickable(onClick = onClick, onLongClick = onLongClick)`
  (needs `@OptIn(ExperimentalFoundationApi::class)` on the composable + import
  `androidx.compose.foundation.combinedClickable`).
- Behavior wired from `ThreadScreen` items:
  - **Not** in selection mode: `onClick` → `viewModel.openMessage(msg)` (unchanged);
    `onLongClick` → `selectionMode = true` + select this id.
  - **In** selection mode: `onClick` → `toggle(id)`; `onLongClick` → `toggle(id)`.
- **Selected visual:** a leading check (`Icons.Default.CheckCircle`) and/or a tinted row
  background so selected bubbles are obvious. Keep it light to fit the existing style.

### 2. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`

- Add **`fun deleteMessages(msgs: List<SMSMessage>)`** — for each message:
  `deleteFromSystemIfDefault(m)` then `repository.deleteMessage(m)` (mirrors
  `deleteConversation`, but for an arbitrary selected subset). Wrapped in one
  `viewModelScope.launch`.
- Add **`fun reportSpamSender(sender: String)`** — for every message from `sender` in
  `allMessages.value`: `repository.reportSpam(m)`; add the `CONTACT -> Spam` rule once.
  (Conversation-wide version of the existing message-scoped `reportSpam(msg)`.)
- Reuse existing `deleteConversation(sender)` for "Delete entire chat".

### 3. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — `MessageDetailScreen`

- Remove the `"SENDER"` label + the `Column { DetailActionRow … }` block (~2087–2159), leaving
  the single-message view (body, smart card, Copy OTP) and the trailing `Spacer`. The sender
  actions now live on the thread.
- If `DetailActionRow` becomes unused, leave it in place (harmless) to keep the diff small; will
  confirm during implementation.

## Imports to add (SmsOrganizerUi.kt — verify first)

`Icons.Default.MoreVert`, `Icons.Default.CheckCircle`, `Icons.Default.Close`,
`DropdownMenu`, `DropdownMenuItem`, `AlertDialog`, `combinedClickable`,
`androidx.compose.foundation.ExperimentalFoundationApi`. (Several may already be imported.)

## Behavior after the change

- **Thread normal mode:** tap a bubble → message detail (unchanged). Overflow `⋮` → Mute /
  Select messages / Block / Report spam / Delete entire chat.
- **Thread selection mode:** long-press any bubble (or `⋮ → Select messages`) enters selection;
  tap toggles; header shows count + Delete; deleting removes only the selected messages (with a
  confirm). System back exits selection.
- **Delete entire chat:** removes the whole conversation (confirm) and returns to the inbox.
- **MessageDetailScreen:** message-only; no sender actions.
- Inbox `ConversationCard` swipe Delete/Block unchanged.

## Notes / risks

- No DB/model changes; UI + two small ViewModel helpers.
- `combinedClickable` is an experimental foundation API — needs the opt-in annotation.
- Deletes are destructive and (when default SMS app) also remove rows from the system provider
  via `deleteFromSystemIfDefault`; hence the confirm dialogs.
- Will grep for UI tests asserting the SENDER actions inside `message_detail_…` and update/report.
