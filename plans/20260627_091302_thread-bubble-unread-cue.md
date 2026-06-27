# Plan: Unread cue for messages inside a conversation thread

**Status:** completed

## The issue

The app already tracks read/unread state end-to-end (data model, DAO, repository,
auto-mark-on-open with delay, manual mark read/unread via selection) and the **inbox
cards** show an unread cue (`categoryColor` dot + 2.dp border, bold sender). But once you
open a conversation, the [`ThreadBubble`](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2273)
composable renders every incoming message identically — it **never reads `msg.isRead`**.
So inside a thread there is no way to tell which messages are unread.

Only inbound messages (`TYPE_INBOX`) carry an unread state; sent messages are always read
(`isRead = true`). So the cue applies to incoming bubbles only.

## Chosen treatment (confirmed with user)

**Accent bar + dot** on unread incoming bubbles, using the message's `categoryColor` to
match the inbox cue:

- A thin colored **vertical accent bar** on the leading (start) edge of an unread incoming
  bubble.
- A small colored **unread dot** in the bubble's metadata row (next to the timestamp).
- Read incoming bubbles and all sent bubbles keep their current look (no bar, no dot).

Because the thread reads from the reactive `allMessages` flow, the bar/dot for a message
disappear automatically the moment it is marked read (on tap, auto-read delay, or the
selection "mark read" action) — no extra wiring needed.

## Files to change

1. **[app/.../ui/SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt)** — `ThreadBubble` only:
   - Derive `val isUnreadIncoming = !msg.isRead && msg.type == SMSMessage.TYPE_INBOX`.
   - Derive `val accentColor = categoryColor(msg.category)` (same helper the inbox card uses).
   - Wrap the bubble `Box` in a `Row(Modifier.height(IntrinsicSize.Min))` so a leading
     accent `Box` (≈3.dp wide, `fillMaxHeight`, rounded, `accentColor`) spans exactly the
     bubble height. Render the bar only when `isUnreadIncoming`; otherwise add no bar (a
     tiny equivalent spacer if needed to keep alignment identical to today).
   - In the existing metadata `Row` (timestamp / status, lines ~2357–2375), prepend a small
     `8.dp` `CircleShape` dot in `accentColor` plus a `Spacer`, shown only when
     `isUnreadIncoming`.
   - No change to selection-mode behavior, click/long-click, sent-bubble layout, or the
     time/status text.

No data-layer, DAO, repository, or ViewModel changes — read/unread plumbing already exists.

## Scope notes / non-goals

- No change to the auto-mark-read delay, the selection mark read/unread actions, or the
  inbox cards.
- No new strings, colors, or DB migration.

## Verification

- Build the app.
- Open a thread with at least one unread incoming message (e.g. set the auto-read delay to
  Off in Settings, or mark a conversation unread): unread incoming bubbles show the leading
  accent bar + dot; read and sent bubbles do not.
- Tap an unread bubble (or wait for the auto-read delay): its bar/dot clear while the rest
  of the thread is unaffected.
