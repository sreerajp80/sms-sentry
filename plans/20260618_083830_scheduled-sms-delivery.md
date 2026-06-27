# Plan: Implement real Scheduled SMS Delivery

**Status:** completed

## The issue

The composer's clock icon ([SmsOrganizerUi.kt:5006-5019](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L5006-L5019))
is a stub. Its `onClick` only shows a Toast:

```kotlin
Toast.makeText(context, "Schedule delivery setup simulated", Toast.LENGTH_SHORT).show()
```

There is no time picker, no persistence, and no mechanism to actually send the message
later. The user wants scheduled delivery to really work: pick a future time, the app holds
the message, and at that time it sends the SMS through the same real `SmsManager` path that
`sendSms` already uses — surviving app close and device reboot.

## Design overview

1. **Persist** scheduled messages in a new Room table (`scheduled_messages`) so they survive
   process death / reboot. They are NOT inserted into the `messages` table until actually
   sent (keeps threads clean; the real `sendSms` path runs at fire time).
2. **Schedule** with `AlarmManager.setExactAndAllowWhileIdle` (guarded by
   `canScheduleExactAlarms()` on API 31+), targeting a new `ScheduledSmsReceiver` via a
   `PendingIntent` carrying the scheduled-row id.
3. **Fire**: at due time the receiver loads the row, sends via a shared `SmsSender` (the send
   logic currently inline in the ViewModel, extracted so both ViewModel and receiver use it),
   then deletes the scheduled row.
4. **Reboot**: alarms are cleared on reboot, so a `BootReceiver` (RECEIVE_BOOT_COMPLETED)
   re-arms all pending alarms; overdue ones fire immediately.
5. **UI**: replace the Toast with a Material3 date + time picker; show/allow-cancel of pending
   scheduled messages in the composer sheet.

Sending still requires being the default SMS app (`canSendSms`), consistent with `sendSms`.

## Files to change / add

### New files
- `app/src/main/java/in/sreerajp/sms_sentry/data/ScheduledSms.kt` — `@Entity("scheduled_messages")`:
  `id`, `recipient`, `body`, `simId`, `scheduledTime` (epoch ms), `createdAt`.
- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsSender.kt` — extracted send logic:
  `dispatch(context, recipient, body, msgId)` building `SmsManager`, multipart split, and the
  sent/delivery `PendingIntent`s (moved out of the ViewModel so the receiver can reuse it).
- `app/src/main/java/in/sreerajp/sms_sentry/receiver/ScheduledSmsReceiver.kt` — on alarm:
  load row → mirror to system Sent box → `processAndInsertMessage(STATUS_SENDING)` →
  `SmsSender.dispatch` → delete the scheduled row. Uses `goAsync()` like
  `SmsSendStatusReceiver`.
- `app/src/main/java/in/sreerajp/sms_sentry/receiver/BootReceiver.kt` — on
  `BOOT_COMPLETED`: re-arm alarms for all pending scheduled rows (immediate fire if overdue).
- `app/src/main/java/in/sreerajp/sms_sentry/util/ScheduledSmsScheduler.kt` — small helper
  wrapping AlarmManager set/cancel + the exact-alarm capability check.

### Changed files
- `data/SmsEntities.kt` — add the `ScheduledSms` entity (or place in its own file; will keep
  it in `ScheduledSms.kt` for clarity).
- `data/SmsDao.kt` — add `insertScheduled`, `getScheduledById`, `getAllScheduledFlow()`,
  `getAllScheduledOnce()`, `deleteScheduledById`.
- `data/SmsDatabase.kt` — register `ScheduledSms::class`, bump version `3 → 4`, add
  `MIGRATION_3_4` creating the `scheduled_messages` table.
- `data/SmsRepository.kt` — thin pass-through methods for the scheduled DAO calls.
- `ui/SmsOrganizerViewModel.kt`:
  - Refactor `dispatchSms` to delegate to `util/SmsSender`.
  - Add `scheduleSms(recipient, body, simId, scheduledTime)` → persist row + arm alarm.
  - Add `cancelScheduled(id)` → cancel alarm + delete row.
  - Expose `scheduledMessages` (StateFlow from the DAO) for the UI.
- `ui/SmsOrganizerUi.kt`:
  - Replace the Toast `onClick` with state that opens a `DatePickerDialog` then a time picker;
    validate the chosen instant is in the future; call `viewModel.scheduleSms(...)`.
  - Add a compact "Scheduled (N)" affordance in the composer controls row that lists pending
    scheduled messages with a cancel (X) action.
- `AndroidManifest.xml`:
  - Add `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />` and
    `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`.
  - Register `ScheduledSmsReceiver` (exported=false, custom action) and `BootReceiver`
    (exported=true, BOOT_COMPLETED filter).

## Edge cases / decisions
- **Exact-alarm permission (API 31+) — EXACT ONLY, no fallback:** check
  `canScheduleExactAlarms()`. If granted, schedule with `setExactAndAllowWhileIdle`. If NOT
  granted, refuse to schedule: show a clear message ("Enable exact alarms for SMS Sentry to
  schedule delivery") and offer a button that opens
  `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` settings. No inexact fallback — the message is not
  scheduled until the permission is granted. (On API < 31 the permission is implicitly held,
  so exact alarms are always used.)
- **Past time:** picker confirm is rejected if `scheduledTime <= now`.
- **Not default SMS app at fire time:** receiver checks default status; if not default it
  marks the inserted message `STATUS_FAILED` (mirrors `sendSms`' guard) and still clears the
  row. (We still allow scheduling while default; this just guards the rare lost-default case.)
- **Reboot:** `BootReceiver` re-arms; overdue rows fire ASAP.
- **Multipart bodies:** handled by `SmsSender` (same `divideMessage` logic as today).

## Testing
- Manual: schedule a message ~1 min out; confirm it sends, appears in the thread as
  Sending→Sent, and the scheduled row disappears. Schedule + cancel; confirm no send.
- If feasible, a Robolectric test for `ScheduledSmsScheduler`/DAO round-trip and the
  `MIGRATION_3_4`. (Screenshot/Roborazzi only if the picker UI warrants it.)

## Out of scope
- Editing an already-scheduled message (cancel + reschedule instead).
- Recurring schedules.
- Scheduling MMS/attachments (text SMS only, matching the current composer send).
