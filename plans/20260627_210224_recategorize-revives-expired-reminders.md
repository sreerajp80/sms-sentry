# Re-categorization revives expired reminders

**Status:** completed

## The issue

When the user runs **Re-categorize all messages** (Settings), the **Active Reminders**
section starts showing old, already-expired reminders again (e.g. bills due July 2023).

### Root cause

Two pieces of logic disagree about expired reminders:

1. On launch, `SmsOrganizerViewModel` calls `repository.purgeExpiredReminders()`
   ([SmsOrganizerViewModel.kt:161-164](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt#L161-L164)),
   which deletes one-shot (`recurrence = 'NONE'`) reminders whose `dueDate` is before the
   start of today. The July 2023 reminders get removed here.

2. `recategorizeAllMessages()`
   ([SmsRepository.kt:351-431](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt#L351-L431))
   re-classifies every stored message and, for any message flagged as a reminder whose
   `messageId` is **not already** in the `reminders` table, inserts a fresh reminder row
   ([SmsRepository.kt:414-428](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt#L414-L428)).

Because step 1 already deleted the expired reminders, their message IDs are no longer in
`reminderMessageIds`, so step 2 re-creates them — including the expired ones. The purge only
runs at launch, so the revived expired reminders sit in the list until the next app start.

## The fix

Re-apply the same expired-reminder purge at the end of `recategorizeAllMessages()`, so
re-categorization has the same end state as launch (no stale one-shot reminders). This reuses
the existing `deleteExpiredReminders(cutoff)` semantics — recurring reminders are untouched,
reminders due today are kept.

Implementation: after the re-classification loop in `recategorizeAllMessages()` (just before
`return messages.size`), call `purgeExpiredReminders()`. This avoids duplicating the cutoff
logic and keeps a single source of truth for "what counts as expired".

## Files to be changed

- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
  - In `recategorizeAllMessages()`, call `purgeExpiredReminders()` after the loop, before
    `return messages.size`.

## Notes / alternatives considered

- **Guarding the insert** (skip inserting reminders whose `dueDate` is in the past) was
  considered. It avoids an insert-then-delete churn, but it duplicates the cutoff logic and
  would not clean up any expired one-shot reminders that already existed before the run.
  Calling `purgeExpiredReminders()` at the end is simpler and exactly matches launch behavior.
- No test currently covers `recategorizeAllMessages()` reminder behavior; the change is small
  and reuses existing, already-exercised purge logic. Manual verification: load messages with
  a past-due reminder SMS, re-categorize, confirm the expired reminder does not appear.
