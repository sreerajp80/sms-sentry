# Remove demo seeding entirely

Implements plan `plans/20260620_211440_remove-demo-seeding.md`.

## What changed

1. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
   - Removed the `else` branch in `init {}` that called `repository.seedDemoData()`
     (and the `inboxMessages.first()` empty-check) when `READ_SMS` was not granted.
   - When SMS permission is granted, `importSystemSms()` still runs as before; with no
     permission, the inbox now simply stays empty.

2. `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
   - Deleted the entire `seedDemoData()` function (13 fake demo messages + 5 demo
     filter rules).

3. `docs/architecture.md`
   - Removed the bullet documenting the first-launch auto-seeding behavior.

## Notes

- No tests referenced `seedDemoData()`; only the call site, the definition, the doc
  bullet, and historical `plans/` files mentioned it. Historical plan files were left
  untouched.
- `.first()` remains imported/used elsewhere in the ViewModel, so no import cleanup was
  needed.
- Existing installs that already seeded demo data still hold those rows in Room; they
  are deleted from within the SMS Sentry Inbox (or by clearing app data). They were
  never written to the system SMS provider, so they never appeared in the stock SMS app.
