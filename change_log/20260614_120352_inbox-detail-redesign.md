# Change log — Inbox & Message-Detail redesign (Direction A "Secure Cards")

Implements plan: `plans/20260614_111725_inbox-detail-redesign.md`.

Applies the Claude Design "SMS Sentry Inbox" pack (`SMSSentry.zip`) — **Direction A · Secure Cards**
plus its companion **Message detail** screen — to the live app. The 5 colour schemes + light/dark were
already implemented in `ui/theme/`, so this change is layout/interaction + supporting plumbing.

## User decisions baked in
- Detail "Sender" menu: **all four** actions (Mute / Block / Report as spam / Delete).
- Smart card: shown with **both** buttons (Add reminder + Mark as paid) functional.
- Copy OTP: **kept** on the inbox card and in the detail screen.
- Verified/Unknown badges + unread dots: **omitted** (no DB migration).

## Files changed

### `data/SmsRepository.kt`
- Added `reportSpam(message)` (sets category=Spam + isBlocked=true via existing DAO updates).
- Added `addReminderForMessage(message, title, dueDate)` to create a `ReminderSms` for any message.

### `ui/SmsOrganizerViewModel.kt`
- Added `openedMessage: MutableState<SMSMessage?>` + `openMessage()` / `closeMessage()` (detail nav,
  consistent with the existing `activeTab` string-switch model — no Navigation-Compose).
- Added persisted `mutedSenders` (Set<String>) and `paidMessageIds` (Set<Long>) in `theme_prefs`,
  with `toggleMute`/`isMuted` and `togglePaid`/`isPaid`.
- Added `blockSender(msg)` (rule + delete), `reportSpam(msg)` (repo + rule),
  `addReminderForMessage(msg, title, dueDate)`.

### `util/SmsNotificationHelper.kt`
- `showNotification(...)` now reads the muted-senders set from `theme_prefs` and returns early
  (suppresses the notification) when the sender is muted.

### `ui/SmsOrganizerUi.kt`
- **Imports:** added `BackHandler`, `pointerInput`, `detectHorizontalDragGestures`, `Dp`, `TextUnit`,
  `em`, `kotlinx.coroutines.launch`.
- **`SmsOrganizerApp`:** renders `MessageDetailScreen` as a full-screen `Surface` overlay when
  `viewModel.openedMessage != null`, with a `BackHandler` that closes it.
- **`InboxScreen`:** restyled filter tabs as pill chips with count badges (`InboxFilterPill`); added a
  swipe/tap hint row; collects `transactions` + `reminders` to compute each card's parsed-entity chip.
- **`MessageCard` (rewritten):** compact "Secure Card" — avatar tile, sender + time, SIM + category
  pills, 2-line preview, parsed amount/due-date chip, and a Copy-OTP chip when an OTP is present.
  **Swipe left** reveals Block + Delete behind the card (drag via `Animatable` + `detectHorizontalDragGestures`,
  snaps open/closed); **tap** opens the detail screen.
- **New `MessageDetailScreen`:** header (back / avatar / sender / "Category · SIM n"), encryption banner,
  date divider, message bubble (with OTP highlight), the **"Detected in this message"** smart card
  (`MessageSmartCard`: amount/due-date/balance tiles + Add reminder + Mark as paid), a full-width
  Copy-OTP button when present, and the **SENDER** action list (Mute toggle / Block / Report as spam /
  Delete). Block/Report/Delete close the detail and return to the Inbox.
- **New helpers:** `detectOtp`, `formatRupees`, `messageEntityText`, `AvatarTile`, `CategoryPill`,
  `SimPill`, `EntityChip`, `CopyOtpChip`, `SwipeAction`, `DetailActionRow`, `InboxFilterPill`.

### `app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt`
- Updated both `MessageCard(...)` call sites to the new signature
  (`entityText`, `onOpen`, `onDelete`, `onBlock`). OTP-detection assertions unchanged and still pass.

## Verification
- `gradle :app:compileDebugKotlin` — success (only pre-existing deprecation warnings).
- `gradle :app:assembleDebug` — success.
- `gradle :app:testDebugUnitTest -Proborazzi.test.verify` — **all tests pass**, including the OTP card
  tests on the new signature and the `greeting.png` screenshot (Dashboard tab unchanged, so the baseline
  still matches).

## Notes / follow-ups
- No DB schema change; no new colour schemes.
- Roborazzi has **no** Inbox/detail screenshot baseline today (only `greeting.png` of the Dashboard), so
  nothing needed re-recording. If Inbox/detail golden screenshots are desired, add a Roborazzi test and
  record with `-Proborazzi.test.record`.
- Mute suppression is best-effort: the notification helper reads SharedPreferences synchronously from the
  same `theme_prefs` file the ViewModel writes.
