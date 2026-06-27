# Reminders list shown in wrong order (sort by due date)

**Status:** completed

## The issue

In the **Active Reminders** list, reminders are ordered with the earliest due date at the top
(2026 above 2027). The desired order is the reverse — latest due date first (2027 above 2026).

### Root cause

The UI list (`items(reminders)` at
[SmsOrganizerUi.kt:3922](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3922))
renders `viewModel.reminders` directly, which is the repository flow backed by the DAO query
[SmsDao.kt:105-106](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt#L105-L106):

```sql
SELECT * FROM reminders ORDER BY dueDate ASC
```

`ASC` puts the soonest due date first, so 2026 appears above 2027.

## The fix

Change the UI-facing query `getAllReminders()` to `ORDER BY dueDate DESC` so the latest due
date appears first.

`getAllRemindersOnce()` ([SmsDao.kt:115-116](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt#L115-L116))
is only used for boot-time alarm re-arming, where order is irrelevant; it will be left as-is.

## Files to be changed

- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt`
  - `getAllReminders()` query: `ORDER BY dueDate ASC` → `ORDER BY dueDate DESC`.

## Verification

Manual: open the Reminders tab with reminders due in 2026 and 2027; confirm 2027 now sorts
above 2026.
