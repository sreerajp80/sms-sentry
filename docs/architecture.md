# Architecture

The whole app is one Gradle module under namespace/`applicationId` `in.sreerajp.sms_sentry`. Flow:

```
SmsReceiver (BROADCAST) ─┐
Simulate/Compose dialogs ┤
CSV/JSON import          ├─► SmsRepository.processAndInsertMessage()  ◄── the single funnel
P2P sync import          ┤        │
demo seed                ┘        ├─► SmsClassifier.classify()  (rules → heuristics)
                                  └─► Room: messages + finance_transactions + reminders
                                            │ (reactive Flows)
                                            ▼
                          SmsOrganizerViewModel (one AndroidViewModel for the whole app)
                                            │ StateFlow / Compose State
                                            ▼
                          SmsOrganizerApp + per-tab screens (Compose)
```

**Key invariant: `SmsRepository.processAndInsertMessage()` is the one and only ingestion path.**
Every source of a message (live broadcast, simulated SMS, P2P sync, CSV/JSON import, demo seed)
goes through it. It (1) loads `FilterRule`s, (2) classifies, (3) inserts the `SMSMessage`, and
(4) writes a supplemental `FinanceTx` (if the message is flagged `isFinance` + amount found) or
`ReminderSms` (if flagged `isReminder` + due date). These flags are content-derived and independent
of the (collapsed) category. If you add an ingestion source, route it here — don't insert into Room
directly.

## Layers / packages

- `data/` — Room. [SmsEntities.kt](../app/src/main/java/com/example/data/SmsEntities.kt) (4 entities:
  `SMSMessage`, `FilterRule`, `FinanceTx`, `ReminderSms`), [SmsDao.kt](../app/src/main/java/com/example/data/SmsDao.kt),
  [SmsDatabase.kt](../app/src/main/java/com/example/data/SmsDatabase.kt) (singleton, `version = 5`,
  `fallbackToDestructiveMigration` — bump version + migrate if you change entities, or data is wiped),
  [SmsRepository.kt](../app/src/main/java/com/example/data/SmsRepository.kt). Categories are free-form
  **string literals** — the user-facing taxonomy is just four: `"Personal"`, `"Promotions"`,
  `"Others"`, `"Spam"`; rule types are `"KEYWORD"`/`"CONTACT"`; `"Blocked"` maps to Spam+`isBlocked`,
  `"NotSpam"` allowlists a sender. There is no enum — keep the strings consistent across classifier,
  DAO queries, and UI. **Finance and reminders are no longer categories**: a money/bill message
  surfaces under `"Others"` but the classifier flags it (`ClassificationResult.isFinance` /
  `isReminder`), and the repository writes the supplemental `FinanceTx` / `ReminderSms` rows off
  those flags. The money ledger (Accounts tab + balance + device-auth lock) and the Reminders tab
  are **derived views** over those rows, not categories. `SmsClassifier.normalizeCategory()` maps
  legacy values (`"Accounts"`/`"Reminder"`/`"Services"`) onto `"Others"`; existing data is re-sorted
  on demand via Settings ▸ Categorization ▸ "Re-categorize all messages"
  (`repository.recategorizeAllMessages()`) — there is no DB migration for the four-category switch.
- `engine/` — business logic. [SmsClassifier.kt](../app/src/main/java/com/example/engine/SmsClassifier.kt)
  (custom contact rules → custom keyword rules → built-in heuristics → field extraction for bank/
  amount/balance/due-date), [P2PSyncEngine.kt](../app/src/main/java/com/example/engine/P2PSyncEngine.kt),
  [SmsShareUtils.kt](../app/src/main/java/com/example/engine/SmsShareUtils.kt) (CSV/JSON import/export).
- `ui/` — [SmsOrganizerUi.kt](../app/src/main/java/com/example/ui/SmsOrganizerUi.kt) is a single ~3150-line
  file holding `SmsOrganizerApp` plus every screen and dialog (Dashboard, Inbox, Accounts, Reminders,
  Sync, Settings, compose/simulate/import dialogs). [SmsOrganizerViewModel.kt](../app/src/main/java/com/example/ui/SmsOrganizerViewModel.kt)
  is the only ViewModel; tab navigation is just a `mutableStateOf("Dashboard")` string switch,
  not Navigation-Compose. `ui/theme/` holds a custom multi-palette theme (`ThemeStyle` enum:
  LAVENDER/SAGE/etc.) persisted to SharedPreferences `theme_prefs`.
- `receiver/` — [SmsReceiver.kt](../app/src/main/java/com/example/receiver/SmsReceiver.kt) (SMS_RECEIVED
  broadcast) and [NotificationActionReceiver.kt](../app/src/main/java/com/example/receiver/NotificationActionReceiver.kt)
  (Copy OTP / Open / Delete notification actions).
- `util/` — [SmsNotificationHelper.kt](../app/src/main/java/com/example/util/SmsNotificationHelper.kt)
  builds themed custom-RemoteView notifications and extracts OTPs.

## Things worth knowing before changing behavior

- Classification is **100% offline** regex/keyword heuristics. Despite `metadata.json` / `.env`
  advertising a `GEMINI_API_KEY` and a server-side Gemini capability, no network LLM call exists.
  The Gemini/`firebase-ai` path was never implemented, and Firebase has been removed from the
  build entirely. Don't assume a Gemini code path exists.
- The app **can optionally be the default SMS app** (Settings ▸ System integration ▸ "Set as
  default SMS app", via [DefaultSmsAppManager](../app/src/main/java/in/sreerajp/sms_sentry/util/DefaultSmsAppManager.kt)).
  Two ingestion paths exist:
  - **Not default:** passive [SmsReceiver](../app/src/main/java/in/sreerajp/sms_sentry/receiver/SmsReceiver.kt)
    listens to `SMS_RECEIVED` (read-only; the OS owns the system provider). Sending is disabled
    in the UI. Existing phone SMS can still be imported with `READ_SMS`.
  - **Default:** [SmsDeliverReceiver](../app/src/main/java/in/sreerajp/sms_sentry/receiver/SmsDeliverReceiver.kt)
    (`SMS_DELIVER`) owns incoming SMS — it writes them to `content://sms` **and** Room;
    `SmsReceiver` short-circuits when default to avoid double-insert. MMS arrives via
    [MmsDeliverReceiver](../app/src/main/java/in/sreerajp/sms_sentry/receiver/MmsDeliverReceiver.kt)
    (`WAP_PUSH_DELIVER`) → [MmsParser](../app/src/main/java/in/sreerajp/sms_sentry/engine/MmsParser.kt).
    Sending uses `SmsManager` (delivery status tracked via
    [SmsSendStatusReceiver](../app/src/main/java/in/sreerajp/sms_sentry/receiver/SmsSendStatusReceiver.kt)).
  Being default also requires [HeadlessSmsSendService](../app/src/main/java/in/sreerajp/sms_sentry/receiver/HeadlessSmsSendService.kt)
  (`RESPOND_VIA_MESSAGE`) and the `sms:`/`smsto:` compose intent-filter on `MainActivity`.
  `simId = 1` is still hardcoded at the receivers (dual-SIM not implemented).
- **Two-way system sync:** `SMSMessage` carries a `systemId` (the `content://sms`/`content://mms`
  row id), `threadId`, `type` (inbox/sent), `isMms`/`attachmentUri`, and `status` (delivery
  state). [SystemSmsStore](../app/src/main/java/in/sreerajp/sms_sentry/data/SystemSmsStore.kt) /
  [SystemMmsStore](../app/src/main/java/in/sreerajp/sms_sentry/data/SystemMmsStore.kt) read/write/
  delete the system providers. Deleting a message in the app also deletes the matching system row
  when default; `SmsRepository.importMessages()` dedupes imports on `systemId`. The DB is now
  `version = 3` (`MIGRATION_2_3` adds these columns).
- The Inbox is **grouped into per-sender conversations** (`ConversationCard` → `ThreadScreen`
  chat view); the still-present `MessageCard` is used by the detail/test path.
- **P2P sync payload encryption (high-entropy pairing code):** the sync payload is protected
  with `AES/GCM/NoPadding`, the key derived via `PBKDF2WithHmacSHA256` over a per-session random
  salt (`deriveKey` in
  [P2PSyncEngine.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/P2PSyncEngine.kt)).
  The shared secret is **not** a 4–6 digit PIN but a **high-entropy pairing code**:
  `generatePairingCode()` makes a fresh 64-char code over a 31-symbol unambiguous alphabet
  (no `0/O/1/I/L`) → ~320 bits, generated per hosting session, shown on the host (grouped 8×8,
  with a copy button) and **typed into the client out-of-band — it never travels over the
  socket**. Both sides `normalizeCode()` (uppercase, strip separators) so a copied code matches.
  That high entropy is what makes the scheme secure without a PAKE: an eavesdropper who captures
  the whole handshake cannot brute-force the code offline, an active MITM cannot establish a
  session without it, and because the code is per-session there is no long-term key to compromise
  (effective forward secrecy). Handshake: host sends the salt in the clear, then the client
  sends a GCM-encrypted `HELLO_SYNC` — a wrong code yields a wrong key, so GCM tag verification
  fails and the connection is rejected (no plaintext comparison, **no** fallback cipher). Each
  message is `Base64.NO_WRAP(IV ‖ ciphertext+tag)`. Robustness: line reads are bounded
  (`readBoundedLine`, 4 KB handshake / 64 MB payload) and the socket has a 30 s read timeout to
  resist OOM/DoS; imported JSON is validated (message-count cap, per-field size cap, positive
  timestamp) before the untrusted peer's data reaches `processAndInsertMessage`. The transport
  is still a plaintext `ServerSocket` on **port 8243** (no TLS) — the *payload*, not the socket,
  is authenticated-encrypted, which is sufficient given the code never crosses the wire. The host
  serializes its messages to JSON and sends them; the client re-runs them through
  `processAndInsertMessage` (re-classifying on import). This is a breaking wire-protocol change:
  both devices must run this build to sync.
- Accounts tab (the money ledger; internal names still `isFinance*`/`FinanceScreen`) is gated behind a
  device-auth lock (`isFinanceLocked` / `triggerDeviceAuthentication`). This lock is the "finance
  security": it survived the four-category consolidation because the ledger is a derived view over
  `FinanceTx` rows (written off the `isFinance` flag), not the `"Accounts"` category that no longer
  exists.
- **Reminder due-alerts + recurrence (AlarmManager):** each `ReminderSms` carries `alertEnabled`
  (default on) and a `recurrence` (`NONE`/`DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY`, see
  [RecurrenceUtil](../app/src/main/java/in/sreerajp/sms_sentry/util/RecurrenceUtil.kt); DB
  `version = 6`, `MIGRATION_5_6`). [ReminderAlarmScheduler](../app/src/main/java/in/sreerajp/sms_sentry/util/ReminderAlarmScheduler.kt)
  arms an exact alarm (mirroring `ScheduledSmsScheduler`) at **09:00 local on the due date** for
  date-only due times. The **single arming funnel** is `ReminderAlarmScheduler.reconcile()`, which
  the ViewModel calls on every `reminders` Flow emission (idempotent via `FLAG_UPDATE_CURRENT`) and
  `BootReceiver` calls from a one-shot snapshot after reboot; arming also honors a global toggle
  (`reminder_alerts_enabled` in `theme_prefs`) and the exact-alarm permission.
  [ReminderAlarmReceiver](../app/src/main/java/in/sreerajp/sms_sentry/receiver/ReminderAlarmReceiver.kt)
  posts the notification (channel `sms_sentry_reminders`), advances recurring reminders to their
  next future occurrence and re-arms, and handles the notification "Done" action. Recurring
  reminders are excluded from the expiry purge (`deleteExpiredReminders` filters `recurrence = 'NONE'`).
  This is **in addition to** the older manual "Add to calendar" path (`addEventToCalendar`, an
  `ACTION_INSERT` hand-off to an external calendar app), which is unchanged.
