# Fix gaps in docs/features.md

**Status:** completed

## Files to be changed

- `docs/features.md`

## The issue

`docs/features.md` was compared against the real code (`docs/architecture.md`,
`SmsOrganizerUi.kt`, `SmsRepository.kt`, `ReminderAlarmScheduler.kt`, `BootReceiver.kt`).
The document is already very detailed and mostly accurate, but a few real, user-facing
things are missing or under-described:

1. **Testing Sandbox is not documented at all.** Settings has a whole page
   (`TestingSandboxPage`, Settings ▸ Testing) that lets a user type a fake sender/body
   (or pick a preset: Bank Trx, Due Bill, Spam Box, Friend Msg, Malayalam OTP, Google OTP)
   and feed it through the real classification pipeline via
   `viewModel.simulateSmsReceived(...)` to see how it gets categorized — useful for
   demoing/QA without needing a real SMS. This is a real, reachable feature and should be
   listed.

2. **Reminder alarms are not re-armed after reboot in the doc.** `docs/architecture.md`
   and `BootReceiver.kt` show that `ReminderAlarmScheduler.reconcile()` is also called
   from `BootReceiver` after a device reboot (not just `ScheduledSmsScheduler`). Section 5
   ("Intelligent Task & Obligation Reminders") only says alarms are armed via
   `ReminderAlarmScheduler`, without mentioning reboot survival — section 6 mentions
   `BootReceiver` only for scheduled SMS. This should be called out for reminders too.

3. **About page fields are under-described.** The About page (`AboutPage`) shows Author,
   Last Build Date, IDE Used, and AI Used — the doc currently just says "version, build
   date, and runtime environment information," which is vague. Minor polish.

4. **App Description paragraph doesn't mention the in-app QA/testing sandbox** — not
   required for the description (it's a minor/hidden dev feature), so no change planned
   there. The description is otherwise inclusive of the major pillars (offline
   classification, default SMS handler, finance/reminders, P2P sync, search) and needs no
   structural rewrite.

No other gaps were found: categories, spam engine, finance extraction, reminders,
default-SMS-app behavior, notifications, P2P sync, backup/export, and customization
sections all match the actual code and `docs/architecture.md`.

## Plan for the fix

- Add a new bullet (or short subsection) under "Customization & System Controls" (or as
  its own item, e.g. "11. Testing Sandbox") describing the Settings ▸ Testing page:
  fake-SMS simulation through the real classifier, preset scenario chips, SIM slot
  selection, for QA/demo purposes.
- Update the reminders bullet about `ReminderAlarmScheduler` to note that alarms are also
  reconciled/re-armed after device reboot via `BootReceiver`, matching the scheduled-SMS
  wording already in section 6.
- Expand the About & Build Metadata bullet to name the actual fields shown (Author, Last
  Build Date, IDE Used, AI Used) instead of the vaguer "runtime environment information."

This is a documentation-only change; no app code is touched.
