# Change log: Re-categorization revives expired reminders

Implements plan
[plans/20260627_210224_recategorize-revives-expired-reminders.md](../plans/20260627_210224_recategorize-revives-expired-reminders.md).

## Problem

Running **Re-categorize all messages** (Settings) made already-expired reminders reappear in
the **Active Reminders** list. The launch-time `purgeExpiredReminders()` deletes past-due
one-shot reminders, but `recategorizeAllMessages()` re-inserted a reminder for any
reminder-flagged message lacking a reminder row — recreating the just-purged expired ones.

## Change

- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
  - In `recategorizeAllMessages()`, added a call to `purgeExpiredReminders()` after the
    re-classification loop (before `return messages.size`). Re-categorization now ends in the
    same clean state as app launch: expired one-shot reminders are removed, while recurring
    reminders and reminders due today are kept. Reuses the existing `deleteExpiredReminders`
    cutoff logic — no duplicated date handling.

## Verification

Manual: load messages containing a past-due reminder SMS, run re-categorize, confirm the
expired reminder does not appear in Active Reminders.
