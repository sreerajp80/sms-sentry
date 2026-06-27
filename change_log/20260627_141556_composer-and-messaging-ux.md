# Change log: Composer & messaging UX

Implements [plans/20260627_131311_composer-and-messaging-ux.md](../plans/20260627_131311_composer-and-messaging-ux.md).

## Summary

Four composer/thread UX improvements: SMS segment counter, per-thread drafts, inline
notification quick-reply, and a retry affordance on failed sends.

## Files changed

- **NEW `util/SmsSegment.kt`** — wraps `SmsMessage.calculateLength` to report parts /
  remaining-in-current / unicode, with `shouldShow()` and a compact `label()` ("2 SMS · 18 left").
- **`util/SmsNotificationHelper.kt`** — non-OTP notifications now add an inline `RemoteInput`
  "Reply" action (with a MUTABLE PendingIntent), gated on being the default SMS app. Added
  `ACTION_REPLY` / `KEY_REPLY_TEXT` constants.
- **`receiver/NotificationActionReceiver.kt`** — handles `ACTION_REPLY`: reads the RemoteInput
  text and sends via the shared path (system Sent mirror + `processAndInsertMessage` as
  `TYPE_SENT`/`STATUS_SENDING` + `SmsSender.dispatch` with the SIM-resolved subId). The trailing
  block dismisses the notification (resolving the inline-reply spinner).
- **`app/src/main/AndroidManifest.xml`** — added `ACTION_REPLY` to the `NotificationActionReceiver`
  intent-filter.
- **`ui/SmsOrganizerViewModel.kt`** — `drafts` StateFlow backed by a new `drafts` SharedPreferences
  (`draftFor` / `saveDraft` / `clearDraft`); `resendMessage()` for failed-send retry.
- **`ui/SmsOrganizerUi.kt`**:
  - Segment counter in the composer action row and above the in-thread reply row.
  - `ThreadScreen` restores the saved draft on open, persists it on dispose, and clears it on send.
  - `ConversationCard` shows a "Draft: …" preview (new `draft` param; `InboxScreen` passes
    `viewModel.drafts`).
  - `ThreadBubble` shows a "Tap to retry" affordance on `STATUS_FAILED` calling
    `viewModel.resendMessage`.

## Notes / scope

- Delivery status per bubble already existed (`deliveryStatusLabel`); only the retry affordance
  was added. ✓/✓✓ icons and true SMS read receipts remain out of scope.
- Drafts use SharedPreferences (no DB migration); quick-reply sends on the SIM-resolved subId for
  the message's slot (default SIM when unresolved).

## Verification

- `./gradlew :app:compileDebugKotlin` and `:app:assembleDebug` — BUILD SUCCESSFUL (only
  pre-existing deprecation warnings).
- Unit tests not run: pre-existing compile error in `ExampleRobolectricTest.kt` (unrelated, no
  test files touched here).
