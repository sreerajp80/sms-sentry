# Change Log: Real Scheduled SMS Delivery

Implements [plans/20260618_083830_scheduled-sms-delivery.md](../plans/20260618_083830_scheduled-sms-delivery.md).

## Summary

Replaced the composer's "Schedule delivery setup simulated" Toast stub with a real
future-delivery feature: pick a date + time, the message is persisted and an exact alarm is
armed; at fire time the SMS is sent through the same real `SmsManager` path as immediate sends.
Alarms are re-armed after reboot. Exact-only behavior (per the approved decision): if the OS
hasn't granted exact-alarm permission, scheduling is refused and the user is deep-linked to the
exact-alarm settings screen — no inexact fallback.

## Files added

- `app/src/main/java/in/sreerajp/sms_sentry/data/ScheduledSms.kt` — `@Entity("scheduled_messages")`
  (`id`, `recipient`, `body`, `simId`, `scheduledTime`, `createdAt`).
- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsSender.kt` — shared low-level send
  (multipart split + sent/delivery `PendingIntent`s), extracted from the ViewModel so the
  receiver reuses the identical path.
- `app/src/main/java/in/sreerajp/sms_sentry/util/ScheduledSmsScheduler.kt` — AlarmManager
  wrapper: `canScheduleExact`, `schedule` (`setExactAndAllowWhileIdle`), `cancel`.
- `app/src/main/java/in/sreerajp/sms_sentry/receiver/ScheduledSmsReceiver.kt` — on alarm:
  mirror to system Sent box (if default) → insert `messages` row (STATUS_SENDING, or FAILED if
  not default) → `SmsSender.dispatch` → delete the scheduled row.
- `app/src/main/java/in/sreerajp/sms_sentry/receiver/BootReceiver.kt` — on `BOOT_COMPLETED`,
  re-arm all pending scheduled rows (overdue ones fire immediately).

## Files changed

- `data/SmsDao.kt` — added `getAllScheduled` (Flow), `getAllScheduledOnce`, `getScheduledById`,
  `insertScheduled`, `deleteScheduledById`.
- `data/SmsDatabase.kt` — registered `ScheduledSms`, bumped version `3 → 4`, added
  `MIGRATION_3_4` creating the `scheduled_messages` table.
- `data/SmsRepository.kt` — added `scheduledMessages` stream + scheduled CRUD pass-throughs.
- `ui/SmsOrganizerViewModel.kt` — `dispatchSms` now delegates to `SmsSender` (removed the inline
  `SmsManager`/`PendingIntent` plumbing + now-unused imports); added `canScheduleExactAlarms`,
  `scheduleSms` (rejects past times, requires exact-alarm permission), `cancelScheduled`,
  `buildExactAlarmSettingsIntent`, and the `scheduledMessages` StateFlow.
- `ui/SmsOrganizerUi.kt` — `ComposeSmsDialog`: clock button now opens a Material3 date picker →
  time picker (or the exact-alarm prompt); a "Scheduled N" badge opens a list dialog to view /
  cancel pending messages. Added `@OptIn(ExperimentalMaterial3Api::class)` and a `testTag`.
- `AndroidManifest.xml` — added `SCHEDULE_EXACT_ALARM` + `RECEIVE_BOOT_COMPLETED` permissions;
  registered `ScheduledSmsReceiver` (exported=false, custom action) and `BootReceiver`
  (exported=true, BOOT_COMPLETED).

## Verification

- `gradlew.bat :app:assembleDebug` → **BUILD SUCCESSFUL**. Room/KSP compiled the new entity and
  DAO; no errors. Only pre-existing deprecation warnings (unrelated to this change).
- Manual on-device testing (send actually firing at the scheduled time, reboot re-arming) not
  yet performed — requires a device with the app set as default SMS app.

## Notes / scope

- Text SMS only (matches the existing composer send). No MMS/attachments, no recurring or
  editing of scheduled messages (cancel + reschedule instead).
- Scheduling does not require being the default SMS app, but the actual send at fire time does;
  if the app lost the default role by then, the message is recorded as FAILED.
- Doc nit (fixed separately): `docs/build-and-test.md` claimed there was no Gradle wrapper, but
  a full wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`, Gradle 9.3.1) is checked in and
  was used to build. Corrected the Build/Test sections to use `./gradlew`.
