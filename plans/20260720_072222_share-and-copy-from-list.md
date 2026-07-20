# Share a message, and copy from the conversation list

**Status:** completed

> Implemented — see
> [change_log/20260720_073500_share-and-copy-from-list.md](../change_log/20260720_073500_share-and-copy-from-list.md).

Picks up the two items left "Out of scope" in
[plans/20260720_053255_copy-and-paste-support.md](20260720_053255_copy-and-paste-support.md).

## The issue

1. **No way to share a message to another app.** The app can put a message on the
   clipboard (`Copy message` on the detail screen, `Copy` in the thread selection
   toolbar), but there is no Android share sheet anywhere. To send a message to
   WhatsApp or email, the user has to copy, leave the app, and paste.
2. **No way to copy from the conversation list.** Long-pressing a card on the
   Inbox screen starts conversation multi-select
   (`SmsOrganizerUi.kt:1075` header, `:1764` long press). That toolbar offers
   mark-read, move, delete, mute, mark-unread and block — no copy, no share.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (only source file)
- `app/src/test/java/in/sreerajp/sms_sentry/CopyMessageTest.kt` (extend)

## The plan

### 1. One small helper for sharing

Add a private helper near the other UI helpers in `SmsOrganizerUi.kt`:

```kotlin
private fun shareText(context: Context, text: String, chooserTitle: String)
```

It builds an `Intent.ACTION_SEND` with type `text/plain` and `EXTRA_TEXT`, wraps
it in `Intent.createChooser(...)`, and starts it. If no app can handle it
(`ActivityNotFoundException`), it shows a `"No app to share with"` toast instead
of crashing.

All three share entry points below call this one helper, so the behaviour stays
the same everywhere.

### 2. Share from the message detail screen

`MessageDetailScreen` currently ends with a full-width **Copy message** button
(`SmsOrganizerUi.kt:3327`). Replace that single button with a `Row` holding two
equal-width buttons:

- **Copy message** — unchanged behaviour, keeps test tag `copy_message_button_<id>`
  so the existing test still passes.
- **Share** (`Icons.Default.Share`) — shares `msg.body`.
  Test tag: `share_message_button_<id>`.

The full-width **Copy OTP** button above it is not touched.

### 3. Share from the thread selection toolbar

In the thread selection header, next to the new **Copy** icon
(`SmsOrganizerUi.kt:2050`), add a **Share** icon button.

- Shares the bodies of every selected message, joined with a blank line, in the
  order they appear on screen — exactly the same text the Copy button produces.
- Clears the selection afterwards, like the other actions.
- Test tag: `thread_share_selected`.

### 4. Copy and Share from the conversation list

The Inbox selection header (`SmsOrganizerUi.kt:1078`) already has five icons and
is full. So both new actions go into its **overflow menu**
(`SmsOrganizerUi.kt:1147`), at the top, above "Mute notifications":

- **Copy message** — copies the latest message body of each selected
  conversation, joined with a blank line, newest conversation first (the order
  the cards are listed in). Toast: `"Message copied"` for one,
  `"N messages copied"` for many. Clears the selection.
- **Share** — shares the same text through the helper. Clears the selection.

What gets copied is the **latest message of each selected conversation** — the
same text shown on the card. Copying a whole conversation's history is a
different, larger feature and is not part of this plan.

Test tags: `inbox_copy_selected`, `inbox_share_selected`.

## Verification

- Extend `CopyMessageTest` with a test that the `share_message_button_<id>`
  button is shown and, when clicked, starts an `ACTION_SEND` intent carrying the
  message body (read back with Robolectric's shadow of the test activity).
- Manual check on device: the real share sheet opens from the detail screen, from
  a thread selection, and from the conversation-list overflow menu; copy from the
  conversation-list menu lands the right text on the clipboard.
- Run the full test suite to confirm nothing regressed.

## Out of scope

- Copying or sharing an entire conversation's full history.
- Sharing attachments (MMS images) — text only.
