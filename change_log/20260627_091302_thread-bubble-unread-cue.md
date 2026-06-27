# Change log: Unread cue for messages inside a conversation thread

Implements [plans/20260627_091302_thread-bubble-unread-cue.md](../plans/20260627_091302_thread-bubble-unread-cue.md).

## What changed

Inside a conversation, incoming messages now carry a visual unread cue. Previously
`ThreadBubble` rendered every message identically and never read `msg.isRead`, so there
was no way to tell read from unread once inside a thread.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — `ThreadBubble`

- Added `isUnreadIncoming = !msg.isRead && msg.type == SMSMessage.TYPE_INBOX` and
  `accentColor = categoryColor(msg.category)` (the same helper/color the inbox card uses).
- Wrapped the bubble `Box` in a `Row(Modifier.height(IntrinsicSize.Min))` and, when
  `isUnreadIncoming`, rendered a 3.dp-wide rounded **vertical accent bar** (`fillMaxHeight`,
  `accentColor`) on the leading edge of the bubble, followed by a 6.dp spacer.
- In the metadata row (timestamp/status), prepended an 8.dp `CircleShape` **unread dot** in
  `accentColor` plus a 5.dp spacer, shown only when `isUnreadIncoming`.
- Read incoming bubbles and all sent bubbles are unchanged (no bar, no dot).

No data-layer, DAO, repository, or ViewModel changes — read/unread plumbing already
existed. Because the thread reads the reactive `allMessages` flow, the bar and dot clear
automatically when a message is marked read (tap, auto-read delay, or the selection
"mark read" action).

## Verification

- `./gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL; only pre-existing deprecation
  warnings, none introduced by this change.
