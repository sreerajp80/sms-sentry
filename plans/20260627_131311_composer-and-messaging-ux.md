# Composer & messaging UX: segment counter, drafts, notification quick-reply, send-status polish

**Status:** completed

> Scope note: these four items are the **composer / thread messaging experience** and are
> independent of the dual-SIM routing plan
> ([20260627_124311_real-dual-sim-routing.md](20260627_124311_real-dual-sim-routing.md)).
> Kept as a separate plan so each can be approved / implemented / logged on its own.

## What the issue is

1. **No long-message segmentation feedback.** Neither the compose dialog
   ([SmsOrganizerUi.kt:5716](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L5716))
   nor the in-thread reply row
   ([SmsOrganizerUi.kt:2178](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2178))
   shows a character / SMS-part counter. The radio split via `divideMessage` already works
   ([SmsSender.kt](../app/src/main/java/in/sreerajp/sms_sentry/util/SmsSender.kt)), but the user
   gets no warning that a long message becomes multiple billed SMS, or that a non-GSM character
   (emoji) drops the limit to 70.

2. **No draft messages.** Reply text (`replyText`,
   [SmsOrganizerUi.kt:1828](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L1828))
   is plain local Compose state — leaving a thread discards unsent text, and the inbox
   conversation list shows no "Draft" indicator.

3. **No quick reply from the notification.** Incoming-message notifications
   ([SmsNotificationHelper.kt:277-311](../app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt#L277))
   only offer Delete / Open (and Copy OTP for OTPs). There is no inline `RemoteInput` "Reply"
   action, so replying always requires opening the app.

4. **Send status is mostly surfaced already — minor gaps only.** Per-bubble delivery state
   (Sending… / Sent / Delivered / Failed) is already rendered via `deliveryStatusLabel`
   ([SmsOrganizerUi.kt:1520](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L1520),
   shown at [L2371](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2371)).
   What's missing: a **retry affordance** on a Failed bubble, and optional ✓/✓✓ iconography
   instead of text. (True "read receipts" don't exist for plain SMS, so out of scope.)

## Design decisions

- **Drafts via SharedPreferences, not Room** — a `sender → text` map persisted under a new
  `drafts` prefs file. Avoids a DB migration; mirrors how muted senders are already kept in
  `theme_prefs`. ViewModel exposes a reactive `drafts` map for the inbox indicator.
- **Quick-reply reuses the existing send path** (`SmsSender.dispatch` + `processAndInsertMessage`
  + system Sent mirror) and is only offered when we are the default SMS app (can actually send).
  It sends on the default outgoing SIM for now (stays decoupled from the dual-SIM plan; once that
  lands, it can pass the resolved subId).
- **Segmentation uses `android.telephony.SmsMessage.calculateLength(body, false)`** (returns
  parts / chars-remaining-in-current-part / encoding) — same GSM-7 vs UCS-2 logic the radio uses,
  no hand-rolled counting. The counter is shown only when it matters (parts > 1, or remaining ≤ a
  small threshold) to avoid clutter on short messages.
- No new permissions required (quick-reply send already covered by `SEND_SMS` + default-app role).

## Files to change

1. **NEW `app/src/main/java/in/sreerajp/sms_sentry/util/SmsSegment.kt`** — small helper:
   `segmentInfo(body): SegInfo(parts: Int, remainingInCurrent: Int, isUnicode: Boolean)` wrapping
   `SmsMessage.calculateLength`, plus a `shouldShow(SegInfo)` predicate.

2. **`ui/SmsOrganizerUi.kt`**:
   - **Segment counter:** render a subtle counter (e.g. `2 SMS · 12 left`) in the composer
     (near the send button, ~L5836) and in the reply row (~L2199), driven by `SmsSegment`.
   - **Drafts:** in `ThreadScreen`, initialize `replyText` from `viewModel.draftFor(sender)`;
     persist on change (debounced/simple) via `viewModel.saveDraft`; clear on successful send
     and when the field is emptied. Add a **"Draft:" prefix/indicator** on the inbox
     `ConversationCard` preview when a draft exists for that sender.
   - **Failed-message retry:** make a `STATUS_FAILED` bubble tappable → `viewModel.resendMessage(msg)`
     (small "Tap to retry" hint next to the Failed label at ~L2371).

3. **`ui/SmsOrganizerViewModel.kt`**:
   - `drafts: StateFlow<Map<String,String>>` + `draftFor(sender)`, `saveDraft(sender, text)`,
     `clearDraft(sender)` backed by the new `drafts` SharedPreferences.
   - `resendMessage(msg: SMSMessage)` — re-dispatch via `SmsSender` and flip status back to
     SENDING (reusing `dispatchSms`); used by the Failed-bubble retry and quick-reply failures.

4. **`util/SmsNotificationHelper.kt`** — for non-OTP notifications, when default SMS app, add a
   `NotificationCompat.Action` with a `RemoteInput` ("Reply") targeting
   `NotificationActionReceiver` (`ACTION_REPLY`), carrying `sender` / `sim_id` / `message_id` /
   `notification_id`. Keep `BigTextStyle`; the reply action attaches regardless of style.

5. **`receiver/NotificationActionReceiver.kt`** — handle `ACTION_REPLY`: read the `RemoteInput`
   text, send via the shared send path (mirror to system Sent + `processAndInsertMessage` as
   `TYPE_SENT`/`STATUS_SENDING` + `SmsSender.dispatch`), then update the notification to a
   "You replied" / sent state (or dismiss). No-op safely if not default SMS app.

6. **`app/src/main/AndroidManifest.xml`** — add the
   `in.sreerajp.sms_sentry.ACTION_REPLY` action to the `NotificationActionReceiver` intent-filter.

## Out of scope (note, don't implement now)

- ✓/✓✓ status **icons** (optional polish; text labels already exist — will only add if cheap).
- MMS/attachment quick-reply, multi-recipient drafts, and dual-SIM selection inside quick-reply
  (the latter folds in after the dual-SIM plan lands).
- Draft autosave for the standalone compose dialog's free-form recipient (drafts are keyed by
  an existing thread's sender).

## Verification

- Type a 200-char message / add an emoji: counter shows correct part count and switches to the
  70-char (Unicode) limit; short messages show no counter.
- Enter reply text, leave the thread, return: text is restored; inbox card shows a Draft marker;
  sending clears both.
- As default SMS app: receive a message → notification shows Reply → inline reply sends, appears
  in the thread as a Sent bubble, and the notification updates. Not default: no Reply action.
- Force a send failure (airplane mode): bubble shows Failed and "Tap to retry"; tapping re-sends.
- Existing Robolectric/Roborazzi tests still pass.
