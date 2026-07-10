# Reminder advance-notice (lead time) + "due soon" highlighting

**Status:** completed

## What the user wants

1. **Advance-notice / lead time (global setting).** A single number of days, configurable in
   Settings, applied to *every* reminder. Instead of the alarm firing only once on the due date,
   it should start alerting `leadDays` before the due date and **repeat (daily) until** the
   reminder is **dismissed**, **expires** (due date passes), or its **per-reminder alert is
   turned off**.
   - Clamping rule (user's example): if `leadDays = 30` but the SMS only arrived 20 days before
     the due date, the alerts should start *from that day* — i.e. the window opens at
     `max(dueDate − leadDays, now)`, never in the past.
2. **"Due soon" highlight (global setting).** Reminders whose due date is within a configurable
   **near-days** threshold are visually highlighted in the Reminders list. The threshold ("how
   many days count as near") is also configurable in Settings.

## How reminders work today (context)

- `ReminderSms.dueDate` is the deadline; `alertEnabled` is the per-reminder toggle; `recurrence`
  is NONE/DAILY/WEEKLY/MONTHLY/YEARLY.
- [ReminderAlarmScheduler.kt](../app/src/main/java/in/sreerajp/sms_sentry/util/ReminderAlarmScheduler.kt)
  is the single arming funnel. `reconcile()` runs on every reminders-Flow emission (ViewModel
  `init`) and at boot (`BootReceiver`). It arms **one** exact alarm per reminder at
  `triggerTimeFor(dueDate)` (date-only due dates bump to 09:00) when global + per-reminder alerts
  are on and the trigger is in the future; otherwise it cancels.
- [ReminderAlarmReceiver.kt](../app/src/main/java/in/sreerajp/sms_sentry/receiver/ReminderAlarmReceiver.kt)
  `ACTION_FIRE` posts the notification, and for recurring reminders advances `dueDate` to the next
  occurrence and re-arms. One-shot reminders are left for the expiry purge.
- The global toggle + per-reminder toggle + recurrence + dismissal are all already wired; the
  alarm just fires **once** per occurrence.

## The issue

The alarm fires only on the due date (at 09:00). There is no advance warning, so deadline-style
reminders (PUC, policy renewal) alert too late to act on, and nothing visually flags reminders
that are about to expire.

## Design

### A. Lead-time is a global preference (no DB change)

Both new settings are global and live in the existing `theme_prefs` SharedPreferences — **no Room
schema change, no migration, no per-reminder column.** Keys:
- `reminder_lead_days` (Int, default **7**). Note: with a non-zero default, existing reminders
  begin daily advance alerts up to 7 days before their due date as soon as this ships; `0` still
  reproduces the old single-due-date behavior if the user later sets it.
- `reminder_near_days` (Int, default **3**) — UI highlight only.

### B. One unified "next trigger" computation

Add a pure function to `ReminderAlarmScheduler`:

```
nextTriggerAfter(dueDate: Long, leadDays: Int, now: Long): Long?
```

It returns the next alert wall-clock time **strictly after `now`** for the occurrence whose
deadline is `dueDate`, considering the lead window; or `null` if the whole window (including the
due date itself) is already past.

- Candidate alert times = each day at the alert hour (09:00) from `dueDate − leadDays` up to the
  day before the due date, **plus** `triggerTimeFor(dueDate)` on the due date itself (which keeps
  a specific time-of-day if the due date carries one).
- Return the earliest candidate `> now`. Because past candidates are skipped, a window that opened
  in the past (the 20-days-before example) yields the next daily tick (today's 09:00 if still
  ahead, else tomorrow's) — i.e. it "starts that day."
- `leadDays = 0` ⇒ the only candidate is the due-date trigger ⇒ **identical to today's behavior.**

This single function becomes the source of truth for both the funnel and the receiver.

### C. Scheduler changes (`ReminderAlarmScheduler.kt`)

- Add `const val PREF_LEAD_DAYS = "reminder_lead_days"` and `fun leadDays(context): Int` (reads
  `theme_prefs`, coerced `>= 0`).
- Add `nextTriggerAfter(...)` (above) and `scheduleAt(context, id, triggerAtMillis)` (arms an exact
  alarm at an explicit time; the existing `schedule(...)`'s body is refactored to call it).
- Rewrite `reconcile()`: for each reminder, `t = nextTriggerAfter(r.dueDate, leadDays, now)`; arm
  at `t` via `scheduleAt` when `globallyOn && r.alertEnabled && t != null`, else `cancel`.

### D. Receiver changes (`ReminderAlarmReceiver.kt`, `ACTION_FIRE`)

After showing the notification, re-arm using the same unified logic so daily ticks continue and
recurrence still rolls forward:

```
val now = now()
var due = reminder.dueDate
var t = nextTriggerAfter(due, leadDays, now)          // next daily tick within this window
if (t == null && isRecurring(recurrence)) {           // we just fired the due-date alert
    due = nextFutureOccurrence(due, recurrence, now)
    advanceReminderDueDate(id, due)
    t = nextTriggerAfter(due, leadDays, now)          // first alert of the next occurrence
}
if (t != null) scheduleAt(context, id, t) else cancel(context, id)
```

This makes the alarm repeat daily through the lead window, stop when the per-reminder alert is off
(guarded by the existing `if (!reminder.alertEnabled) return`), advance for recurring reminders,
and go quiet for one-shot reminders after the due date (then removed by the existing expiry purge).
"Dismiss"/notification-"Done" already cancel + delete, so dismissal still stops everything.

### E. ViewModel changes (`SmsOrganizerViewModel.kt`)

Add two persisted Int states (mirroring `reminderAlertsEnabled` / `autoMarkReadDelaySeconds`):
- `reminderLeadDays` (default 7) — its setter re-runs `ReminderAlarmScheduler.reconcile(...)` so a
  change re-arms every reminder immediately.
- `reminderNearDays` (default 3) — UI only; no reconcile needed.

### F. UI changes (`SmsOrganizerUi.kt`)

- **Settings → Reminders → "DUE-DATE ALERTS" card** (around line 5630): add
  - an **"Advance notice (days)"** numeric stepper (−/＋ with a typed value, clamped `>= 0`,
    `0 = only on the due date`), bound to `reminderLeadDays`; and
  - a **"Highlight reminders due within (days)"** numeric stepper bound to `reminderNearDays`.
  - Update the existing description text to mention advance alerts repeat daily through the window.
- **Reminders screen** (`ReminderRowItem`, ~line 3922/3937): pass `nearDays` (from
  `viewModel.reminderNearDays`) and highlight when the reminder is due within the threshold —
  tinted card container + error-colored border + a small **"Due in N days" / "Due today"** chip
  next to the title. Days remaining computed from start-of-day(dueDate) − start-of-day(now).

## Files to change

| File | Change |
|---|---|
| `app/.../util/ReminderAlarmScheduler.kt` | `PREF_LEAD_DAYS`, `leadDays()`, `nextTriggerAfter()`, `scheduleAt()`; rewrite `schedule()`/`reconcile()`. |
| `app/.../receiver/ReminderAlarmReceiver.kt` | Re-arm daily ticks + recurrence advance via `nextTriggerAfter`. |
| `app/.../ui/SmsOrganizerViewModel.kt` | Add `reminderLeadDays` (reconciles on change) + `reminderNearDays` persisted states. |
| `app/.../ui/SmsOrganizerUi.kt` | Settings steppers; pass `nearDays` to `ReminderRowItem`; due-soon highlight + chip. |

No changes to `SmsDatabase.kt` / `SmsDao.kt` / `SmsEntities.kt` / `SmsRepository.kt` (no schema
change). `BootReceiver.kt` already calls `reconcile()`, so it inherits the new behavior unchanged.

## Design decisions (defaults chosen — tell me to change any)

1. **Advance ticks fire daily at 09:00**; the final due-date alert keeps the due date's own
   time-of-day if it has one. (Reuses the existing 09:00 default-alert-hour.)
2. **Default lead = 7 days** (existing reminders start daily advance alerts up to a week early as
   soon as this ships). **Default near = 3 days.** `leadDays = 0` reproduces the old single-alert
   behavior.
3. **Lead time is global**, applied to all reminders (matches the request) — not per-reminder.
4. Settings use a **numeric stepper that also accepts typed input** so "any number of days" works,
   rather than fixed preset chips.
5. "Starts that day" = the first daily tick on/after the window opens at the 09:00 alert hour (or
   the next day's 09:00 if 09:00 has already passed today). No off-hour immediate fire.

## Testing / verification

- Manual: set lead = 3 on a reminder due in 5 days → alarm arms for 2 days out; simulate fires
  re-arm next day; on due date a recurring reminder rolls to the next occurrence, a one-shot goes
  quiet and is purged. Toggling the per-reminder alert off cancels; "Dismiss" cancels + deletes.
- Lead = 0 must behave exactly as today (single due-date alert); lead = 7 (default) arms the first
  alert 7 days before and re-arms daily.
- Near-days highlight appears/clears as the threshold changes.
- Build via the project's no-wrapper Gradle (`docs/build-and-test.md`).
