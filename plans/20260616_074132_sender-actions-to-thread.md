# Move sender-scoped actions from the message detail to the sender (thread) level

**Status:** completed

## The issue

Tapping a message bubble opens **`MessageDetailScreen`** (a single-message view,
`message_detail_${msg.id}`). At the bottom of that screen is a **"SENDER"** action list with
four items:

- Mute notifications (toggle)
- Block sender
- Report as spam
- Delete conversation

All four operate on the **sender / whole conversation**, not on the one tapped message. So they
are presented at the wrong altitude: a per-message screen offering whole-conversation actions.
(See the user's screenshot.)

The natural place for sender-scoped actions is the **sender level** — the chat-style
`ThreadScreen` (`thread_${sender}`), which is the per-sender view reached from the inbox
`ConversationCard`. Its header currently has only a back button + sender name, with no actions.

## The fix (high level)

1. Add a **kebab / overflow menu** (`MoreVert` `IconButton` + `DropdownMenu`) to the
   `ThreadScreen` header, containing the four sender-scoped actions.
2. **Remove** the "SENDER" action block from `MessageDetailScreen`, leaving it a pure
   single-message view (message body, smart card, Copy-OTP).
3. Wire each menu item to the sender-scoped ViewModel methods. Add one small sender-scoped
   method for "Report as spam" (currently only a message-scoped `reportSpam(msg)` exists).

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
  - **`ThreadScreen`** (~1248–1409): add overflow menu to the header row.
    - New `var menuOpen by remember { mutableStateOf(false) }`.
    - In the header `Row`, after the sender name `Column`, add an `IconButton` with
      `Icons.Default.MoreVert` (testTag `thread_menu_button`) that sets `menuOpen = true`, with a
      `DropdownMenu` anchored to it. Items:
      - **Mute notifications** — trailing "On/Off" text via `viewModel.isMuted(sender)`;
        `onClick` → `viewModel.toggleMute(sender)` + existing muted/unmuted Toast; keep menu open
        or close (close is fine).
      - **Block sender** — `viewModel.blockConversation(sender)` then `viewModel.closeThread()`.
      - **Report as spam** — `viewModel.reportSpamSender(sender)` + "Reported as spam" Toast then
        `viewModel.closeThread()`.
      - **Delete conversation** (red / `spamColor()`) — `viewModel.deleteConversation(sender)`
        then `viewModel.closeThread()`.
    - Reuse a small dropdown item composable (or inline `DropdownMenuItem`s). The existing
      `DetailActionRow` is built for full-width rows, so plain `DropdownMenuItem`s are cleaner here.
  - **`MessageDetailScreen`** (~2087–2159): delete the `"SENDER"` label `Text` and the
    following `Column { DetailActionRow ... }` block (Mute / Block / Report / Delete). Keep the
    trailing `Spacer`. If `DetailActionRow` becomes unused after this, leave it (harmless) or
    remove it — will confirm during implementation; default is to leave it to keep the diff small.

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
  - Add **`fun reportSpamSender(sender: String)`**: for every message from `sender` in
    `allMessages`, call `repository.reportSpam(m)`, and add the `CONTACT -> Spam` rule once.
    (Mirrors the existing `reportSpam(msg)` but conversation-wide, consistent with
    `deleteConversation` / `blockConversation`.)

## Behavior after the change

- **ThreadScreen** (sender level): overflow menu offers Mute / Block / Report spam / Delete
  conversation. Block / Report / Delete close the thread and return to the inbox.
- **MessageDetailScreen** (message level): shows only the single message and its message-scoped
  affordances (smart card, Copy OTP). No sender actions.
- Inbox `ConversationCard` swipe Delete/Block is unchanged.

## Notes / risks

- No data-model or DB changes; purely UI + one ViewModel helper.
- Need imports for `MoreVert`, `DropdownMenu`, `DropdownMenuItem` in `SmsOrganizerUi.kt`
  (verify; `MoreVert` may not yet be imported).
- Any UI test asserting the SENDER actions exist inside `message_detail_…` would need updating;
  will grep for such tests during implementation and adjust/report.
