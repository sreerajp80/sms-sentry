# Fix gaps in docs/features.md

Implements plan: `plans/20260802_222215_features_doc_review.md`

## What changed

Reviewed `docs/features.md` against the real code and `docs/architecture.md`. The doc was
already accurate and thorough; found and fixed three small gaps:

1. **Added a new "Testing Sandbox" bullet** under section 10 (Customization & System
   Controls) documenting the Settings ▸ Testing page (`TestingSandboxPage`), which lets a
   user simulate an incoming SMS (typed or via preset scenario chips: Bank Transaction,
   Due Bill, Spam, Friend Message, Malayalam OTP, Google OTP) through the real
   classification pipeline (`viewModel.simulateSmsReceived`) for QA/demo purposes. This
   feature was previously undocumented.

2. **Updated the reminders "Exact AlarmManager Due Alerts" bullet** (section 5) to note
   that reminder alarms are also re-armed after a device reboot via `BootReceiver`,
   matching what was already documented for scheduled SMS.

3. **Expanded the "About & Build Metadata" bullet** (section 10) to name the actual
   fields shown on the About page (Author, Last Build Date, IDE Used, AI Used) instead of
   the vaguer "runtime environment information."

No app code was changed — documentation only.
