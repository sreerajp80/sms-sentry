# Change log: reminder advance-notice (lead time) + "due soon" highlight

Implements plan
[plans/20260628_090942_reminder-lead-time-and-due-soon-highlight.md](../plans/20260628_090942_reminder-lead-time-and-due-soon-highlight.md).

## Summary

Reminder due-alerts previously fired **once**, on the due date. Added a configurable global
**advance-notice (lead) window** so alerts begin up to N days early and **repeat daily at 09:00**
until the reminder is dismissed, its per-reminder alert is turned off, or the due date passes; plus
a configurable **"due soon" highlight** for reminders nearing their due date in the Reminders list.
No database/schema change — both settings are global SharedPreferences (`theme_prefs`).

## Changes by file

### `app/src/main/java/in/sreerajp/sms_sentry/util/ReminderAlarmScheduler.kt`
- Added `PREF_LEAD_DAYS` key, `DEFAULT_LEAD_DAYS = 7`, and `leadDays(context)` (reads `theme_prefs`,
  coerced `>= 0`).
- Added `nextTriggerAfter(dueDate, leadDays, now): Long?` — the unified "next alert time" math:
  each day at 09:00 from `dueDate − leadDays` to the day before the due date, plus the due-date
  trigger itself; returns the earliest candidate strictly after `now`, or null when the whole
  window is past. `leadDays = 0` ⇒ behaves exactly like the old single due-date alarm. A window
  that opened in the past yields the next daily tick (so alerts "start that day").
- Added `scheduleAt(context, id, triggerAtMillis)`; refactored `schedule()` to compute the next
  trigger via `nextTriggerAfter` and delegate to it.
- Rewrote `reconcile()` to arm each reminder at `nextTriggerAfter(...)` (or cancel) instead of the
  one-shot `triggerTimeFor(dueDate) > now` check.

### `app/src/main/java/in/sreerajp/sms_sentry/receiver/ReminderAlarmReceiver.kt`
- `ACTION_FIRE` now re-arms the **next daily tick** within the lead window after showing the
  notification. When the window is exhausted (due-date alert fired), a recurring reminder advances
  to its next occurrence and arms that occurrence's first alert; a one-shot goes quiet (left for the
  expiry purge). Uses `nextTriggerAfter` + `scheduleAt`/`cancel`, so the receiver and the funnel
  share one computation.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
- Added persisted Int states `reminderLeadDays` (default 7; setter re-runs
  `ReminderAlarmScheduler.reconcile(...)` so a change re-arms all reminders immediately) and
  `reminderNearDays` (default 3; UI-only).

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Added `KeyboardType` import.
- Added private `ReminderDaysStepperRow` composable: label + description with a −/typeable
  numeric field/＋ control (clamped to a minimum), so the user can set **any** number of days.
- Settings → Reminders "DUE-DATE ALERTS" card: added an **"Advance notice (days)"** stepper bound
  to `reminderLeadDays` and a **"Highlight when due within (days)"** stepper bound to
  `reminderNearDays`, separated by a divider; updated the description text to mention daily advance
  alerts.
- Reminders list: passes `nearDays` into `ReminderRowItem`.
- `ReminderRowItem`: new `nearDays` parameter; computes whole days-to-due from start-of-day;
  highlights the card (error-tinted container + 1.5dp error border) when due within the threshold,
  and shows a bold **"Due today" / "Due tomorrow" / "Due in N days" / "Overdue"** chip (alongside
  the existing "Calendar Synced" chip).

## Notes / behavior
- **Default lead = 7 days is "on"**: after this update existing reminders begin daily advance
  alerts up to a week before their due date. Setting Advance notice to `0` restores the old
  single-due-date alert behavior.
- Advance ticks fire at 09:00; the final due-date alert keeps the due date's own time-of-day when
  it has one.
- Dismiss / notification "Done" / per-reminder alert-off / global alert-off all still cancel the
  alarm (existing wiring + the rewritten `reconcile`). `BootReceiver` inherits the new behavior
  unchanged because it already calls `reconcile()`.

## Verification
- `./gradlew.bat :app:assembleDebug` — **BUILD SUCCESSFUL**.
- No schema change, so no Room migration was added.
