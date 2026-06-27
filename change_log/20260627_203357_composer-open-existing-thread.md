# Composer folds into existing conversation on recipient match

Implements [plans/20260627_203144_composer-open-existing-thread.md](../plans/20260627_203144_composer-open-existing-thread.md).

## What changed

The "New message" composer now switches into an existing conversation thread the moment its
recipient resolves to one, instead of staying a separate full-screen composer. Behavior is
*live, on exact match* (confirmed with the user).

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
- Added `existingThreadFor(recipient: String): String?` — returns the canonical sender key of an
  existing conversation matching `recipient`, or null. Phone-number-like recipients match on
  their trailing digits (last 10) so `9496135390` / `+91 94961 35390` resolve to the same
  thread; alphanumeric sender IDs (e.g. `HDFCBK`) match case-insensitively. Returns the first
  matching distinct sender from `allMessages`.
- Added private helper `digitsTail(s)` (digits only, trailing 10) used by the matcher.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (`ComposeSmsDialog`)
- Added a `LaunchedEffect(senderInput, allMessages)` that, when
  `viewModel.existingThreadFor(senderInput.trim())` returns a match:
  - carries the typed body into the thread via `viewModel.saveDraft(match, body)` (only when the
    body is non-blank, so an empty composer never clobbers an in-progress reply draft);
  - sets `handledExit = true` and calls `viewModel.clearComposeDraft()` to avoid leaving a
    phantom new-message draft;
  - calls `viewModel.openThread(match)` and `onDismiss()` to reveal the thread overlay.
- Collects `viewModel.allMessages` in the composer for the match check.

## Notes / scope
- Exact-match-only avoids jumping while a longer number is still being typed.
- Name-typed-as-recipient (a contact name with a numeric thread) is intentionally not matched;
  matching is number-normalized or exact-string, mirroring how threads are keyed today.
- No changes to `sendSms` or thread plumbing — `openThread` + the existing draft mechanism cover
  the rest.

## Verification
- `./gradlew :app:compileDebugKotlin` succeeds (no Kotlin errors; only JVM native-access
  warnings).
- Manual check recommended: composer → type/select an existing conversation's number (with body
  text) switches into that thread with the text in the reply box; a brand-new number stays in the
  composer.
