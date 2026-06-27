# Change log: Compose-window drafts

Implements [plans/20260627_145200_compose-window-drafts.md](../plans/20260627_145200_compose-window-drafts.md).

## What changed

The new-message composer (`ComposeSmsDialog`) now remembers unsent text as a single most-recent
compose draft (recipient + body), complementing the existing per-thread reply drafts.

- **`ui/SmsOrganizerViewModel.kt`** — added `composeDraft()`, `saveComposeDraft(recipient, body)`,
  and `clearComposeDraft()`, backed by `theme_prefs` keys `compose_draft_recipient` /
  `compose_draft_body`. Kept separate from the per-sender `drafts` map so a free-form recipient
  never produces a phantom inbox card.
- **`ui/SmsOrganizerUi.kt`** (`ComposeSmsDialog`):
  - Restores the saved compose draft into the recipient/body fields only when the composer is
    opened blank (no `initialRecipient`/`initialBody`); explicit prefills still win.
  - A `DisposableEffect` saves the current recipient/body as the compose draft when the dialog
    closes without sending/scheduling (guarded by a `handledExit` flag).
  - The Send path and a successful Schedule set `handledExit` and call `clearComposeDraft()`.

## Where it's visible

Re-opening the new-message composer restores the draft. (A draft to a number with no conversation
has no inbox card to render on; that, and a FAB "draft pending" indicator, were out of scope.)

## Verification

- Type recipient + body, dismiss without sending, reopen → restored. Send/schedule → cleared.
  Opening from a contact/`sms:` link shows that recipient, not the saved draft.
- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` — BUILD SUCCESSFUL.
