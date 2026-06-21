# Change log: Inbox long-press select + contextual action menu

Implements plan
[plans/20260617_221034_inbox-longpress-select-contextual-menu.md](../plans/20260617_221034_inbox-longpress-select-contextual-menu.md).

## What changed

Long-pressing a conversation card in the inbox now selects it and shows a contextual action
bar, supporting single and multi-select — mirroring the pattern already used in `ThreadScreen`.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
- Added `markConversationRead(sender)`: marks every unread message from a sender as read.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- **`ConversationCard`**
  - Annotated `@OptIn(ExperimentalFoundationApi::class)`.
  - Signature: replaced `onOpen` with `onClick`/`onLongClick` and added `selected` /
    `selectionMode` params.
  - Swapped the foreground `Card(onClick = …)` for the no-onClick overload plus a
    `.combinedClickable(onClick, onLongClick)` modifier; the swipe `pointerInput` drag detector
    is unchanged and still coexists. The tap still resets an open swipe before invoking
    `onClick`.
  - Selected visual: solid primary `BorderStroke` and a faint primary container overlay
    (composited over the existing base/blocked container color) when selected; a leading
    `CheckCircle` / `RadioButtonUnchecked` icon in the top row while in selection mode.
- **`InboxScreen`**
  - Added selection state (`selectionMode`, `selectedSenders`, `menuOpen`,
    `showDeleteSelectedDialog`), `clearSelection()` / `toggle()` helpers, a
    `LaunchedEffect(filteredMessages)` that drops stale selections (after delete/block or a
    folder switch), and `BackHandler(enabled = selectionMode)` to exit selection.
  - Header now swaps to a contextual action bar when selecting: ✕ (cancel), "N selected",
    Mark-as-read (`DoneAll`), Delete (opens confirm dialog), and a ⋮ overflow menu with
    Mute/Unmute, Mark as read, Block sender, and Delete. All act on every selected sender.
  - Added a multi-delete confirmation `AlertDialog`.
  - Updated the `ConversationCard` call site to pass selection state and wire
    `onClick`/`onLongClick` (tap toggles in selection mode, otherwise opens the thread;
    long-press enters selection mode and selects).
  - Updated the swipe-hint text to mention "long-press to select".

Scope (per user): actions limited to Mark as read, Mute, Block, Delete. Pin and Archive were
explicitly left out (no data-model/ViewModel support exists).

## Verification

- `gradle :app:compileDebugKotlin` (via `gradlew.bat`) — **BUILD SUCCESSFUL**; only
  pre-existing deprecation warnings, none from these changes.
- Manual on-device verification of the gestures/menu still recommended.
