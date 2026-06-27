# Inbox: long-press a card to select it + contextual action menu

**Status:** completed

## Issue / feature request

On the inbox list, long-pressing a conversation card should **select** it (with a visible
selected state) and bring up a **contextual action bar** with actions that operate on the
selected card(s) — supporting both single and multi-select. Tapping more cards while in
selection mode toggles them; the back button exits selection.

The app already implements this exact pattern in `ThreadScreen` (chat bubbles via
`ThreadBubble` + a contextual selection header). We mirror it on the inbox
`ConversationCard` list in `InboxScreen`.

Scope confirmed with the user:
- Actions to include: **Mark as read**, **Mute**, **Block**, **Delete**.
- **Pin and Archive are out of scope** (no data model / ViewModel support exists; not adding now).

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
  - `InboxScreen` — add selection state, contextual action bar, overflow menu, confirm dialog.
  - `ConversationCard` — add `selected` / `selectionMode` / `onLongClick` params + selected
    visual + `combinedClickable` gesture.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
  - Add `markConversationRead(sender)` (mark all of a sender's messages read).

## Plan for the change

### 1. ViewModel: mark a whole conversation read

Add next to the existing conversation helpers (`deleteConversation`, `blockConversation`):

```kotlin
/** Mark every message from a sender as read. */
fun markConversationRead(sender: String) {
    viewModelScope.launch {
        allMessages.value
            .filter { it.sender == sender && !it.isRead }
            .forEach { repository.markAsRead(it.id) }
    }
}
```

(Delete / block / mute already exist: `deleteConversation`, `blockConversation`,
`toggleMute` / `isMuted`.)

### 2. `ConversationCard` — selection support

Signature gains three params (mirroring `ThreadBubble`):

```kotlin
fun ConversationCard(
    conversation: Conversation,
    entityText: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,        // replaces onOpen; caller decides open-vs-toggle
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit
)
```

- Annotate with `@OptIn(ExperimentalFoundationApi::class)` (already used by `ThreadBubble`).
- Replace the foreground `Card(onClick = …)` with the no-onClick `Card { }` overload and add a
  `.combinedClickable(onClick = …, onLongClick = onLongClick)` modifier. Keep the existing
  swipe `pointerInput` drag detector — it consumes horizontal drags, so tap/long-press and
  swipe coexist (the tap still resets an open swipe before invoking `onClick`).
- Selected visual: when `selected`, override the card border to a solid accent
  (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) and tint the container with a
  faint primary overlay; when `selectionMode` is on, show a leading
  `CheckCircle` / `RadioButtonUnchecked` icon in the card's top row (same icon set as
  `ThreadBubble`).

### 3. `InboxScreen` — selection state + contextual bar

Add state near the top of `InboxScreen`:

```kotlin
var selectionMode by remember { mutableStateOf(false) }
val selectedSenders = remember { mutableStateListOf<String>() }
var menuOpen by remember { mutableStateOf(false) }
var showDeleteSelectedDialog by remember { mutableStateOf(false) }
```

Helpers `clearSelection()` and `toggle(sender)` (identical shape to `ThreadScreen`), plus a
`LaunchedEffect(conversations)` that drops selected senders that no longer exist and exits
selection when empty. `BackHandler(enabled = selectionMode) { clearSelection() }`.

Header: wrap the existing filter-pills / swipe-hint area so that **when `selectionMode` is
true** it is replaced by a contextual action bar:
- leading ✕ (`clearSelection`),
- `"${selectedSenders.size} selected"`,
- **Mark as read** icon (`DoneAll`),
- **Delete** icon (`Delete`, opens confirm dialog),
- **⋮ overflow** `DropdownMenu` with **Mute / Unmute**, **Block**, **Mark as read**.

All actions iterate over `selectedSenders` and call the matching ViewModel method, then
`clearSelection()`. Block/Delete also remove the cards (already handled by their ViewModel
methods updating the reactive flows). Mute label reflects `isMuted` of the selection.

Card wiring in the `items(conversations)` loop:

```kotlin
ConversationCard(
    conversation = conv,
    entityText = entityText,
    selected = conv.sender in selectedSenders,
    selectionMode = selectionMode,
    onClick = {
        if (selectionMode) toggle(conv.sender) else viewModel.openThread(conv.sender)
    },
    onLongClick = {
        if (!selectionMode) selectionMode = true
        toggle(conv.sender)
    },
    onDelete = { viewModel.deleteConversation(conv.sender) },
    onBlock = { viewModel.blockConversation(conv.sender) }
)
```

### 4. Confirm dialog for multi-delete

Mirror `ThreadScreen`'s `showDeleteSelectedDialog` AlertDialog: "Delete N conversation(s)?",
confirm → `selectedSenders.forEach { viewModel.deleteConversation(it) }` + `clearSelection()`.

### Imports

`combinedClickable` / `ExperimentalFoundationApi` already imported (used by `ThreadBubble`).
Add icon imports if missing (`Icons.Default.DoneAll`, `CheckCircle`,
`RadioButtonUnchecked` — the latter two already used by `ThreadBubble`).

## Verification

- Build the app.
- Inbox: long-press a card → it shows selected + the header becomes the contextual bar with a
  count of 1.
- Long-press / tap more cards → count grows; tapping a selected card deselects it.
- ⋮ → Mute/Block/Mark as read act on all selected senders; Delete shows a confirm dialog.
- Back button exits selection (does not leave the screen); ✕ clears selection.
- With nothing selected, tapping a card still opens the thread and swipe-left still reveals
  Block/Delete as before.
