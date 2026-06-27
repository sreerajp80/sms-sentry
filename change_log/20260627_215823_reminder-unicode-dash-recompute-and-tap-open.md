# Change log — reminder Unicode-dash dates, re-date on re-classify, tap card to open

Implements [plans/20260627_214542_reminder-unicode-dash-recompute-and-tap-open.md](../plans/20260627_214542_reminder-unicode-dash-recompute-and-tap-open.md).

## Context

A PUCC reminder card showed "Scheduled For: 30 Jun 2026" while the SMS body said
the certificate "will expire on 16–Jun–2026" (arrived 10 Jun 2026). Root cause:
the separators in the body are **en dashes (U+2013)**, not ASCII hyphens, so the
prior date-parse fix (which only handled ASCII `-`) still failed and the code fell
back to "message time + 3 days" — and a stale, already-stored reminder was never
re-dated on re-categorize.

## Changes

### `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
- Added a private `const val DATE_SEP` character-class body covering ASCII
  space/dot/slash/hyphen **plus** Unicode dashes: non-breaking hyphen (U+2011),
  figure dash (U+2012), en dash (U+2013), em dash (U+2014), minus sign (U+2212).
- Rebuilt `isoDateRegex`, `dateRegex1`, and `dateRegex2` from `DATE_SEP`, and used
  it in the `parseAlphaDate` separator-normalization regex. `16–Jun–2026` (and em
  dash / minus variants) now match and normalize to `16 Jun 2026`.

### `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
- `recategorizeAllMessages` now loads existing reminders into a
  `messageId -> ReminderSms` map and **upserts**: it inserts a reminder when the
  message has none, and **updates the existing reminder's `dueDate`** (via
  `updateReminderDueDate`) when the recomputed date differs. Previously it skipped
  any message that already had a reminder, so historic mis-dated cards never
  corrected. (Replaces the prior `reminderMessageIds` set.)

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- `ReminderRowItem` gained an `onOpen: () -> Unit` parameter; the card is now
  `clickable { onOpen() }`.
- `RemindersScreen` passes `onOpen = { viewModel.openMessageById(reminder.messageId) }`,
  so tapping a reminder opens its source SMS (Back returns to Reminders).

### `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`
- Added `unicode dash separated alpha dates parse identically` — asserts en dash
  (`16–Jun–2026`), em dash (`16—Jun—2026`), and minus (`16−Jun−2026`) all extract
  to the 16 Jun 2026 due date.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.SmsClassifierTest"`
  → BUILD SUCCESSFUL (main + test compilation passes; all classifier tests green,
  including the new Unicode-dash case).

## Notes / follow-ups

- The code fix corrects newly processed messages. The already-stored 30-Jun card
  corrects itself when the user triggers a **re-categorize** (which now re-dates
  existing reminders).
- Re-dating on re-categorize also recomputes the `dueDate` of manually added
  reminders. If that's undesirable, gate the update on `reminder.body == message.body`.
- Exact-alarm re-scheduling after a `dueDate` change on re-categorize was left out
  of scope; the displayed card date is corrected and alarms re-arm on the normal
  scheduling paths.
