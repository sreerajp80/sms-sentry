# Change log: Open the composer from sms:/smsto: launch intents

Follow-on to [plans/20260614_223309_default-sms-app.md](../plans/20260614_223309_default-sms-app.md)
(the `ACTION_SENDTO` intent-filter was declared there but the handoff wasn't wired). Approved by
the user as a direct follow-up.

## What changed

When SMS Sentry is launched via an `sms:`/`smsto:`/`mms:`/`mmsto:` link (e.g. tapping "Message"
in Contacts or the dialer, especially while it is the default SMS app), it now opens the composer
pre-filled with the recipient (and body, if provided).

- **[MainActivity.kt](../app/src/main/java/in/sreerajp/sms_sentry/MainActivity.kt)** — added
  `handleSendIntent()` called from `onCreate` and `onNewIntent` (with `setIntent`). It parses the
  `sms`/`smsto`/`mms`/`mmsto` scheme: recipient from `schemeSpecificPart` (query stripped), body
  from the `sms_body` / `EXTRA_TEXT` extras, then calls `viewModel.requestCompose(...)`.
- **[SmsOrganizerViewModel.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt)** —
  added `ComposePrefill` data class, `composePrefill` state, `requestCompose()` and
  `clearComposePrefill()`.
- **[SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt)** — a
  `LaunchedEffect` on `composePrefill` opens the composer when a prefill arrives; `ComposeSmsDialog`
  gained `initialRecipient`/`initialBody` params used as the initial input state; the prefill is
  cleared on dismiss.

## Notes

- Sending still requires being the default SMS app (the Send button stays disabled with the
  existing banner otherwise) — this change only routes the intent to the composer.
- Not compiled in this environment (no Gradle/SDK); needs a build + on-device check.
