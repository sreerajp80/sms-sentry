# Change log: Reminders list sort order

Implements plan
[plans/20260627_211500_reminders-sort-order-desc.md](../plans/20260627_211500_reminders-sort-order-desc.md).

## Problem

The Active Reminders list showed the earliest due date at the top (2026 above 2027). The
desired order is latest due date first (2027 above 2026).

## Change

- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt`
  - `getAllReminders()` query changed from `ORDER BY dueDate ASC` to `ORDER BY dueDate DESC`.
    This flow backs the Reminders UI list, so reminders now sort latest-due first.
  - `getAllRemindersOnce()` (boot-time alarm re-arming, order-irrelevant) left unchanged.

## Verification

Manual: open the Reminders tab with reminders due in 2026 and 2027; confirm 2027 sorts above
2026.
