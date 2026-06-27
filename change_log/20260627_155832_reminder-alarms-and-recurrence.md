# Change log: Reminder due-alerts (AlarmManager) + recurring reminders

Implements [plans/20260627_154520_reminder-alarms-and-recurrence.md](../plans/20260627_154520_reminder-alarms-and-recurrence.md).

## What changed

### 1. Schema — recurrence + alert flag (DB v5 → v6)
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt`: added `recurrence: String = "NONE"`
  and `alertEnabled: Boolean = true` to `ReminderSms`.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt`: bumped `version` 5 → 6, added
  `MIGRATION_5_6` (`ALTER TABLE reminders ADD COLUMN recurrence … / alertEnabled …`) and registered it.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt`: added `getReminderById`,
  `getAllRemindersOnce`, `updateReminderAlert`, `updateReminderRecurrence`, `updateReminderDueDate`;
  changed `deleteExpiredReminders` to `… AND recurrence = 'NONE'` so recurring reminders are never purged.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`: wrappers `getReminderById`,
  `getAllRemindersOnce`, `setReminderAlert`, `setReminderRecurrence`, `advanceReminderDueDate`.

### 2. Alarm + notification plumbing (new)
- NEW `app/src/main/java/in/sreerajp/sms_sentry/util/RecurrenceUtil.kt`: cadence constants/labels,
  `nextOccurrence`, and `nextFutureOccurrence` (skips missed occurrences via `Calendar.add`).
- NEW `app/src/main/java/in/sreerajp/sms_sentry/util/ReminderAlarmScheduler.kt`: mirrors
  `ScheduledSmsScheduler`; `triggerTimeFor` bumps date-only due dates to 09:00 local; `reconcile()`
  is the single arming funnel (arm future alert-enabled reminders, cancel the rest), honoring the
  global toggle + exact-alarm permission.
- NEW `app/src/main/java/in/sreerajp/sms_sentry/receiver/ReminderAlarmReceiver.kt`: on `ACTION_FIRE`
  posts the notification and, for recurring reminders, advances the due date and re-arms; `ACTION_DONE`
  deletes the reminder, cancels the alarm, and dismisses the notification.
- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`: added
  `showReminderNotification` on a new `sms_sentry_reminders` channel (tap → Reminders tab via
  `EXTRA_OPEN_REMINDERS`; "Done" action → `ReminderAlarmReceiver`).
- `app/src/main/AndroidManifest.xml`: registered `ReminderAlarmReceiver` (exported=false). No new
  permissions (SCHEDULE_EXACT_ALARM / RECEIVE_BOOT_COMPLETED / POST_NOTIFICATIONS already present).

### 3. Wiring
- `app/src/main/java/in/sreerajp/sms_sentry/receiver/BootReceiver.kt`: after re-arming scheduled SMS,
  also `ReminderAlarmScheduler.reconcile(...)` from a one-shot reminders snapshot.
- `app/src/main/java/in/sreerajp/sms_sentry/MainActivity.kt`: handle `EXTRA_OPEN_REMINDERS` to open
  the Reminders tab on notification tap.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`: `reminderAlertsEnabled`
  persisted toggle (re-reconciles on change); `init` collects `reminders` and reconciles alarms;
  `openRemindersFromNotification`, `setReminderAlert`, `setReminderRecurrence`; `deleteReminder` now
  cancels the alarm.

### 4. UI
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`: `ReminderRowItem` gained an
  "Alert on/off" chip and a recurrence dropdown; `RemindersScreen` shows an exact-alarm permission
  banner when alerts are on but the OS withholds the permission; Settings gained a "Reminders →
  Reminder due alerts" global switch (with an "Allow exact alarms" shortcut).

### 5. Tests / docs
- NEW `app/src/test/java/in/sreerajp/sms_sentry/RecurrenceUtilTest.kt`: covers each cadence,
  month-end roll-over, and missed-occurrence skipping.
- `docs/architecture.md`: documented the reminder due-alert path and recurrence model.

## Verification
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL (incl. new `RecurrenceUtilTest`).
