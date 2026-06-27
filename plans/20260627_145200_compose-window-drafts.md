# Extend drafts to the compose window (new-message composer)

**Status:** completed

## What the issue is

Per-thread reply drafts already work (saved on leaving a thread, shown as "Draft:" on the inbox
card, restored in the reply box). But the **standalone compose window** (`ComposeDialog`, the
"new message" dialog reached from the FAB / `sms:` intents / "message this sender") does **not**
remember unsent text — close it and the recipient + body are lost. The user wants the compose
window to keep a draft too.

## Design decision

The compose window has a **free-form recipient with no thread to key on** (and a brand-new number
has no inbox card to surface a "Draft:" label). So rather than force it into the per-sender
draft map, keep a single **most-recent compose draft** (recipient + body):

- Restored into the composer the next time it is opened **blank** (no initial recipient/body).
- Saved when the composer is dismissed **without sending/scheduling**.
- Cleared on a successful send or schedule.

This is distinct from, and complementary to, the per-thread reply drafts. (Where you "see" it:
re-opening the new-message composer brings it back, the same way most SMS apps' composers do.)

## Plan for the fix

**`ui/SmsOrganizerViewModel.kt`** — add a compose-draft slot persisted in the existing
`theme_prefs` (keys `compose_draft_recipient` / `compose_draft_body`), kept separate from the
per-sender `drafts` map so it never produces a phantom inbox card:
- `composeDraft(): Pair<String, String>`
- `saveComposeDraft(recipient: String, body: String)` (clears when both blank)
- `clearComposeDraft()`

**`ui/SmsOrganizerUi.kt`** (`ComposeDialog`):
- Initialize `senderInput` / `smsBodyInput` from the saved compose draft **only when opened blank**
  (`initialRecipient` and `initialBody` both empty); otherwise honor the explicit prefill.
- Add a `handledExit` flag + a `DisposableEffect(Unit) { onDispose { … } }` that saves the current
  recipient/body as the compose draft when the dialog closes and the message was **not** sent or
  scheduled.
- On the **Send** path and on a **successful Schedule**: set `handledExit = true` and call
  `viewModel.clearComposeDraft()` before/at dismissal.

## Scope / out of scope

- One most-recent compose draft (not per-recipient). No new inbox card for it (a draft to a number
  with no conversation has nowhere to render). A FAB/compose "draft pending" indicator is **not**
  included — say if you want one.
- No DB migration (SharedPreferences only).

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

## Verification

- Type a recipient + body in the new-message composer, dismiss without sending, reopen it →
  fields are restored. Send (or schedule) → draft cleared (reopening is blank). Opening the
  composer from a contact/`sms:` link still shows that recipient, not the saved draft.
- `./gradlew :app:assembleDebug` succeeds.
