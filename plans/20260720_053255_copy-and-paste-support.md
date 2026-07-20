# Copy a message, and paste into Reply/Compose

**Status:** partial_completion

> Steps 1-3 (copy a message) were implemented. Steps 4-5 (the app's own paste
> button and long-press paste on the composers) were **dropped** mid-implementation:
> the user confirmed system-level copy/paste in the compose and reply windows is
> already handled. See
> [change_log/20260720_054500_copy-a-message.md](../change_log/20260720_054500_copy-a-message.md).

## The issue

The user reports two things:

1. There is no way to copy a message.
2. Long-pressing the Reply / Compose box does not paste.

### What the code shows

**Copy — this is simply not built.**

- `LocalClipboardManager` is used in only two places, and both copy the **OTP** only:
  `CopyOtpChip` (`SmsOrganizerUi.kt:2994`) and the full-width copy button in
  `MessageDetailScreen` (`SmsOrganizerUi.kt:3271`).
- Long-pressing a bubble inside a thread starts **selection mode**
  (`SmsOrganizerUi.kt:2275`). That contextual toolbar offers only mark-read,
  mark-unread and delete (`SmsOrganizerUi.kt:2012-2060`). No copy.
- Message body text is a plain `Text` / `AnnotatedString`. It is not wrapped in a
  `SelectionContainer`, so the user cannot even drag-select the words by hand.

So there is no code path that puts a message body on the clipboard. This is a
missing feature, not a broken one.

**Paste — no hard blocker found, so add our own affordance.**

Both composers are bare `BasicTextField`s with a hand-written `decorationBox`:

- Reply composer in the thread: `SmsOrganizerUi.kt:2311`
- Body field in the new-message composer: `SmsOrganizerUi.kt:6949`

I checked the usual causes of a dead paste popup and found none:

- No `FLAG_SECURE` anywhere.
- No parent `pointerInput` / `detectTapGestures` stealing the long press near
  either field (the only `pointerInput` is on a conversation row, line 1750).
- `MainActivity` is a plain `ComponentActivity`, theme is
  `android:Theme.DeviceDefault.NoActionBar` — nothing that disables an
  `ActionMode`.

Compose's paste popup is a *floating action mode* owned by the OS, and on some
OEM builds it does not appear reliably. Rather than chase that, the fix is to
give the app its own paste button that we control. That works on every device.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (only file)
- `app/src/test/java/in/sreerajp/sms_sentry/...` — one new Robolectric test file
  for the copy action (name to follow the existing test naming).

## The plan

### 1. Copy from the thread selection toolbar

In the selection-mode header (`SmsOrganizerUi.kt:2012`), add a **Copy** icon
button (`Icons.Default.ContentCopy`) before Delete.

- Copies the bodies of every selected message, joined with a blank line, in
  timestamp order.
- Shows a toast: `"Message copied"` for one, `"N messages copied"` for many.
- Clears the selection afterwards, like the other actions do.
- Test tag: `thread_copy_selected`.

### 2. Copy the whole message from the detail screen

In `MessageDetailScreen`, next to the existing "Copy OTP" button
(`SmsOrganizerUi.kt:3266`), add a **Copy message** button that puts the full
`msg.body` on the clipboard and toasts `"Message copied"`. When there is no OTP,
this button takes the full width on its own.

Test tag: `copy_message_button_<id>`.

### 3. Let the user select text by hand

Wrap the message body in `MessageDetailScreen` in a `SelectionContainer` so
partial text (a link, a reference number) can be dragged out normally.

Only the detail screen — not the thread list. Putting a `SelectionContainer`
around the bubbles would fight with the existing long-press selection mode.

### 4. An explicit Paste button on both composers

For the reply composer (`:2311`) and the compose-dialog body (`:6949`):

- Read the clipboard with `LocalClipboardManager`.
- When the clipboard holds text **and** the field is empty, show a small
  `ContentPaste` icon button inside the field row.
- Tapping it appends the clipboard text at the cursor.
- Test tags: `thread_paste_button`, `composer_paste_button`.

This is additive — the system long-press popup keeps working wherever it already
does, and this gives a guaranteed path where it does not.

### 5. Keep the long press working too

Also attach a `combinedClickable` `onLongClick` on the composer's rounded
background `Box` that pastes directly. This restores the gesture the user
expected, without depending on the OS floating toolbar.

## Out of scope

- Sharing a message to other apps (Share sheet). Say the word and I will add it.
- Copy from the conversation list (long press there selects whole conversations,
  which is a different thing).

## Verification

- New Robolectric test: select a message in a thread, tap copy, assert the
  clipboard holds the body.
- Manual check on device for the paste button and the long-press paste, since
  clipboard gestures are hard to test in Robolectric.
- Run the existing test suite to confirm nothing regressed.
