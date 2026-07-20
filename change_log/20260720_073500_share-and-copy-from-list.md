# Share a message, and copy from the conversation list

Implements [plans/20260720_072222_share-and-copy-from-list.md](../plans/20260720_072222_share-and-copy-from-list.md),
which picked up the two items left out of the earlier copy/paste plan.

## What changed

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

- **New `shareText(context, text, chooserTitle)` helper** (next to `deliveryStatusLabel`).
  Builds an `ACTION_SEND` / `text/plain` intent, wraps it in a system chooser, and
  starts it. Adds `FLAG_ACTIVITY_NEW_TASK` when the context is not an Activity, and
  shows a `"No app to share with"` toast instead of crashing when nothing can handle
  the intent. All three share buttons below go through this one helper.
  Added the `android.content.ActivityNotFoundException` import.

- **Message detail screen.** The full-width "Copy message" button is now a row of two
  equal-width buttons: **Copy** (same action, same test tag
  `copy_message_button_<id>`) and **Share** (test tag `share_message_button_<id>`),
  which shares the message body. The "Copy OTP" button above is unchanged.

- **Thread selection toolbar.** Added a **Share** icon button between Copy and Delete
  (test tag `thread_share_selected`). It shares the bodies of all selected messages,
  joined with a blank line in on-screen order — the same text the Copy button
  produces — then clears the selection.

- **Conversation list selection menu.** The header row was already full, so **Copy
  message** (`inbox_copy_selected`) and **Share** (`inbox_share_selected`) were added
  at the top of the overflow menu. Both act on the **latest message of each selected
  conversation** — the line shown on the card — newest conversation first, joined with
  a blank line. Copy toasts `"Message copied"` / `"N messages copied"`. Both clear the
  selection. Added a `clipboardManager` and a small `selectedLatestBodies()` helper in
  that screen's scope.

### `app/src/test/java/in/sreerajp/sms_sentry/CopyMessageTest.kt`

Two new tests:

- `share button is shown on the detail screen` — asserts the new share button renders.
- `shareText sends the body through a chooser` — calls the helper directly and checks
  the started intent is a chooser wrapping an `ACTION_SEND` / `text/plain` intent with
  the message body in `EXTRA_TEXT`.

## Verification

`./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, whole suite green, including
the three tests in `CopyMessageTest`.

The share sheet itself and the conversation-list menu still want a quick manual check
on a real device, since Robolectric does not open a real chooser.

## Not done

- Copying or sharing a whole conversation's history.
- Sharing MMS attachments (text only).
