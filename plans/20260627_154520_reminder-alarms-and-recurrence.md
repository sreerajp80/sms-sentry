# Reminder due-alerts (AlarmManager) + recurring reminders

**Status:** completed

## The issue

Reminders (`ReminderSms`) are detected and surfaced in the Reminders tab, but the **only**
alerting path is the manual "Add to calendar" button
([`addEventToCalendar()`](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt#L757-L776)),
which hands the event to an external calendar app. There is:

1. **No in-app due alert.** Nothing notifies the user at the due date from within the app, even
   though the app already owns a complete exact-alarm stack (`ScheduledSmsScheduler` /
   `ScheduledSmsReceiver` / `BootReceiver`) and already declares `SCHEDULE_EXACT_ALARM`,
   `RECEIVE_BOOT_COMPLETED`, and `POST_NOTIFICATIONS`.
2. **No recurrence.** `ReminderSms` carries a single `dueDate: Long` with no way to repeat
   (e.g. a monthly rent or bill reminder).

This plan adds both, reusing the proven scheduled-SMS patterns.

## Design decisions

- **Opt-in alerts, default ON, per-reminder silence.** A new `alertEnabled` flag drives whether
  an alarm is armed. Auto-classified reminders default to `alertEnabled = true` so the feature
  works out of the box, but each row gets a bell toggle to silence it, plus a global
  Settings switch ("Reminder due alerts") to disable the whole feature.
- **Fire at 9:00 AM local on the due date, not at the parsed millis.** Parsed due dates are
  usually midnight; alerting at 00:00 is bad UX. The scheduler normalizes the trigger to 09:00
  local on the due date. (If the due timestamp already has a non-midnight time-of-day, it is
  used as-is.)
- **Arming funnel = the ViewModel, not the repository.** `insertReminder` is called from many
  auto-classification paths and the repository holds no `Context`. Rather than thread a Context
  through all of them, the **ViewModel observes the `reminders` Flow and reconciles alarms**
  (arm every future `alertEnabled` reminder — idempotent via `FLAG_UPDATE_CURRENT`; the platform
  replaces a same-id alarm harmlessly). `BootReceiver` does the same reconcile from a one-shot
  DB read. Deletes cancel explicitly.
- **Recurrence advances on fire.** When a recurring reminder's alarm fires, the receiver posts
  the notification, advances `dueDate` to the next occurrence, persists it, and re-arms. The
  expiry purge is changed to **never** delete recurring reminders.
- **Targeted-by-class receiver** (no intent filter), exactly like `ScheduledSmsReceiver`, so it
  works as a manifest receiver.

## Files to change

### Data / schema
1. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt`** — add to `ReminderSms`:
   - `val recurrence: String = "NONE"` (one of `NONE` / `DAILY` / `WEEKLY` / `MONTHLY` / `YEARLY`)
   - `val alertEnabled: Boolean = true`
2. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt`** — bump `version = 5` → `6`;
   add `MIGRATION_5_6`:
   ```sql
   ALTER TABLE reminders ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE';
   ALTER TABLE reminders ADD COLUMN alertEnabled INTEGER NOT NULL DEFAULT 1;
   ```
   and register it in `addMigrations(...)`.
3. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt`** — add:
   - `suspend fun getReminderById(id: Long): ReminderSms?`
   - `suspend fun getAllRemindersOnce(): List<ReminderSms>` (for boot reconcile)
   - `suspend fun updateReminderAlert(id: Long, enabled: Boolean)`
   - `suspend fun updateReminderRecurrence(id: Long, recurrence: String)`
   - `suspend fun updateReminderDueDate(id: Long, dueDate: Long)`
   - change `deleteExpiredReminders` to **exclude recurring**:
     `DELETE FROM reminders WHERE dueDate < :cutoff AND recurrence = 'NONE'`
4. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`** — thin wrappers for the
   new DAO methods (`getReminderById`, `getAllRemindersOnce`, `setReminderAlert`,
   `setReminderRecurrence`, `advanceReminderDueDate`). `purgeExpiredReminders` is unchanged in
   body (the SQL now self-excludes recurring rows).

### Alarm + notification plumbing (new)
5. **NEW `app/src/main/java/in/sreerajp/sms_sentry/util/ReminderAlarmScheduler.kt`** — mirror of
   `ScheduledSmsScheduler`, targeting `ReminderAlarmReceiver`. Provides:
   - `canScheduleExact(context)` (same exact-alarm gate)
   - `triggerTimeFor(dueDate): Long` — normalize to 09:00 local on the due date (or keep
     non-midnight times)
   - `schedule(context, reminderId, dueDate)` / `cancel(context, reminderId)`
   - `reconcile(context, reminders)` — arm every reminder with `alertEnabled` and a future
     trigger time; respects the global Settings toggle (no-op when disabled, and cancels).
   - Request code = `reminderId` (distinct PendingIntent namespace from scheduled SMS because the
     target component differs).
6. **NEW `app/src/main/java/in/sreerajp/sms_sentry/util/RecurrenceUtil.kt`** — `nextOccurrence(dueDate, recurrence): Long`
   using `Calendar.add` (DAY / WEEK_OF_YEAR / MONTH / YEAR); `NONE` returns the input unchanged.
7. **NEW `app/src/main/java/in/sreerajp/sms_sentry/receiver/ReminderAlarmReceiver.kt`** — on fire:
   load the reminder by id; if missing or `!alertEnabled`, return; post the reminder notification;
   if `recurrence != NONE`, advance `dueDate` to `nextOccurrence`, persist, and re-arm. Uses
   `goAsync()` + IO coroutine like `ScheduledSmsReceiver`. Also handles an `ACTION_DONE`
   (notification "Done" button) → delete the reminder + cancel its alarm.
8. **`app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`** — add
   `showReminderNotification(context, reminder)` on a **new channel** `sms_sentry_reminders`
   ("Reminder Alerts", IMPORTANCE_HIGH). Title = reminder title, body = due-date line + message
   snippet; tap opens MainActivity on the Reminders tab; a "Done" action routes to
   `ReminderAlarmReceiver` `ACTION_DONE`; an "Add to calendar" action is optional (deferred).
9. **`app/src/main/java/in/sreerajp/sms_sentry/receiver/BootReceiver.kt`** — after re-arming
   scheduled SMS, also `ReminderAlarmScheduler.reconcile(appContext, repository.getAllRemindersOnce())`.

### UI / VM
10. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`**:
    - In `init`, collect the `reminders` Flow and call `ReminderAlarmScheduler.reconcile(...)` on
      each emission (the single arming funnel).
    - `fun setReminderAlert(id, enabled)` → update DB + arm/cancel.
    - `fun setReminderRecurrence(id, recurrence)` → update DB (reconcile re-arms via the Flow).
    - `deleteReminder` → also `ReminderAlarmScheduler.cancel(...)`.
    - Persist the global "Reminder due alerts" toggle in `theme_prefs` (reuse `persistedState`),
      and re-reconcile when it changes.
11. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`** — in `RemindersScreen` /
    `ReminderRowItem`: add a bell toggle (alert on/off), a small recurrence picker
    (None/Daily/Weekly/Monthly/Yearly), and show the recurrence label. If exact-alarm permission
    is missing, show a one-line prompt to grant it (intent to
    `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). Add the global toggle to the Settings screen.

### Manifest / docs
12. **`app/src/main/AndroidManifest.xml`** — register `ReminderAlarmReceiver`
    (`android:exported="false"`, no intent filter), alongside `ScheduledSmsReceiver`. No new
    permissions (all three needed are already declared).
13. **`docs/architecture.md`** — document the new reminder-alert path and recurrence model.

## Out of scope / deferred
- Custom per-reminder lead time and arbitrary alert time-of-day (fixed 09:00 default for now).
- Snooze action on the notification.
- Editing a reminder's due date from the UI (recurrence advances it automatically; manual edit
  can be a follow-up).

## Verification
- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` green.
- Manual: create a reminder due in ~2 min (temporarily), confirm the notification fires; toggle a
  reminder to Daily and confirm it re-arms for the next day after firing; toggle alert off and
  confirm the alarm is cancelled; reboot (or re-trigger BootReceiver) and confirm re-arm.
- `RecurrenceUtil` unit tests for each cadence (incl. month-end roll-over).
