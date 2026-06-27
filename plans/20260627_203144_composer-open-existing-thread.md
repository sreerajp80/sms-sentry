# Composer → open existing conversation on recipient match

**Status:** completed

## What the issue is

The "New message" composer (`ComposeSmsDialog`, [SmsOrganizerUi.kt:6025](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L6025))
is a standalone full-screen overlay. When the user composes a message to a recipient that
*already* has a conversation thread, the composer stays separate — it does not fold into the
existing thread. The user wants: while composing, once the recipient resolves to an existing
conversation, switch into that conversation (which has its own history + reply box) instead of
continuing in the isolated "New message" screen.

Conversation threads (`ThreadScreen`, [SmsOrganizerUi.kt:1910](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L1910))
are keyed by the message `sender` string and gathered from `allMessages`' distinct senders.
The thread reply box is pre-filled from `viewModel.draftFor(sender)`.

## Chosen behavior (confirmed with user)

**Live, on match.** As soon as the typed or selected recipient resolves to an existing
conversation, switch into that thread and carry whatever was typed in the composer body over as
the thread's reply draft. To avoid jarring mid-typing jumps, the switch fires only on an **exact**
match against a complete existing thread key (not a prefix).

## Matching rules

A typed recipient matches an existing conversation when, against any distinct `sender` in
`allMessages`:

- **Phone-number-like** (per `ContactNameResolver.isPhoneNumberLike`): compare digits-only,
  matching on the trailing 10 digits (or full digit string when shorter than 10) so that
  `9496135390`, `+91 94961 35390`, etc. all resolve to the same thread.
- **Alphanumeric sender IDs / names** (e.g. `HDFCBK`): case-insensitive trimmed exact match.

The first matching distinct sender key is returned; that key (the stored canonical sender) is the
thread that gets opened.

## Plan for the fix

### 1. `SmsOrganizerViewModel.kt`
- Add `fun existingThreadFor(recipient: String): String?` that:
  - Returns `null` for a blank recipient.
  - Iterates the distinct senders of `allMessages.value` and returns the first one matching
    `recipient` per the rules above (number normalization helper inline / private).
- Add a small private `digitsTail(s: String): String` helper (digits only, last 10) used by the
  matcher. No public API beyond `existingThreadFor`.

### 2. `SmsOrganizerUi.kt` — inside `ComposeSmsDialog`
- Add a `LaunchedEffect(senderInput, allMessages)` (allMessages already collected via
  `inboxMessages`; will collect `viewModel.allMessages` here, or reuse a new
  `collectAsState`) that, when `viewModel.existingThreadFor(senderInput.trim())` returns a
  non-null `match`:
  - If `smsBodyInput` is non-blank, persist it as the thread draft via
    `viewModel.saveDraft(match, smsBodyInput.trim())` so the typed text lands in the thread's
    reply box. (If a thread draft already exists and body is blank, leave it untouched.)
  - Set `handledExit = true` and call `viewModel.clearComposeDraft()` so the composer's
    `onDispose` does not re-save a phantom new-message draft.
  - Call `viewModel.openThread(match)` then `onDismiss()` to close the composer and reveal the
    thread overlay.
- Because the effect is keyed on `senderInput`, tapping a suggestion row (which sets
  `senderInput` to a full existing sender) and typing a full matching number both trigger it.

No changes to `sendSms`/thread plumbing are needed — `openThread` + the existing draft mechanism
already cover the rest.

## Files to be changed
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt` — add `existingThreadFor`
  + private number-tail helper.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — add the live match→switch
  `LaunchedEffect` in `ComposeSmsDialog`.

## Edge cases / notes
- Exact-match-only avoids jumping while a longer number is still being typed (a partial prefix
  never equals a full thread key). The rare case of typing a longer number whose 10-digit tail
  collides with an existing thread is accepted.
- Name→number matching (typing a contact *name* that has a numeric thread) is **out of scope**;
  matching is number-normalized or exact-string only, matching how threads are keyed today.
- The carried-over body overwrites any pre-existing thread draft only when the composer body is
  non-blank, so an empty composer won't clobber an in-progress reply draft.

## Test / verification
- Manual: open composer, type the number of an existing conversation (with body text) → app
  switches to that thread with the text in the reply box. Type/select a brand-new number → stays
  in composer. Tap a suggestion that is an existing conversation → switches to its thread.
- Build: `./gradlew :app:assembleDebug` (per docs/build-and-test.md — no wrapper; use configured
  Gradle). Existing Robolectric test untouched.
