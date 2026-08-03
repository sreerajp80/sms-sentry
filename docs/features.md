# SMS Sentry - Features & Capabilities

## App Description

**SMS Sentry** is a privacy-first, 100% offline, native Android SMS management, rich messaging, smart organization, task reminder, and testing sandbox platform built using Kotlin, Jetpack Compose, Material 3 Design, Kotlin Coroutines/Flow, and Room SQLite (`version = 6`). Engineered from the ground up for total data sovereignty, zero cloud dependency, and high-density performance, SMS Sentry operates seamlessly either as a passive, read-only SMS organizer or as the device's full **Default SMS Handler** (supporting live SMS/MMS sending, dual-SIM slot routing, carrier delivery status tracking, headless quick replies, WAP PUSH MMS media attachment rendering, and two-way system database provider synchronization). A folder-scoped, on-device message search and smart thread matching (`digitsTail`) let users instantly find and view any message across their inbox, while integration with `ContactsContract` resolves phone numbers into display names and avatars.

All message categorization, weighted offline spam detection, multi-lingual OTP auto-extraction (supporting English and regional Malayalam verification phrases), financial transaction ledger extraction, bank recognition, passbook balance disambiguation, payment receipt inversion, obligation reminder tracking, and future scheduled SMS delivery queueing run **100% offline on-device** using deterministic rule engines, heuristic classifiers, and boundary-anchored regular expressions. No SMS message contents, personal contact details, financial account ledgers, or telemetry are ever uploaded to cloud servers, external APIs, or remote tracking services. Financial account ledgers, liquid balance totals, and spending statistics are safeguarded behind an integrated **Biometric & Device Security Lock** (`BiometricPrompt` and `KeyguardManager`) with an interactive monthly cash flow credit/debit contribution breakdown sheet.

Obligation reminders and scheduled SMS deliveries are backed by exact system alarms (`AlarmManager`) and an automatic **Reboot Receiver** (`BootReceiver`) ensuring alarm resilience across device restarts. Reminders support flexible recurrence frequencies (**Daily**, **Weekly**, **Monthly**, **Yearly**) and one-tap export to native system calendar apps (`CalendarContract.EVENTS`), while scheduled SMS messages remain isolated in a dedicated queue until delivery to prevent conversation thread pollution.

Custom theme-matched RemoteView system notifications (`notification_otp`) feature one-tap OTP clipboard copying (`ACTION_COPY_OTP`), inline quick replies (`RemoteInput`), direct message deletion (`ACTION_DELETE_MESSAGE`), and strict Android 12+ notification trampoline compliance. For multi-device workflows without compromising privacy, SMS Sentry includes a serverless, end-to-end encrypted Peer-to-Peer (P2P) local Wi-Fi / hotspot sync engine powered by AES-256-GCM authenticated encryption, high-entropy pairing keys derived via PBKDF2-HMAC-SHA256 (300,000 iterations, ~320 bits of entropy over a 31-symbol unambiguous alphabet `23456789ABCDEFGHJKMNPQRSTUVWXYZ`), interactive connect-then-choose category sharing, camera-scannable QR code pairing (`SyncQr`), bounded line readers, strict memory safety caps, and add-only non-destructive data merges.

Complete data portability is provided via CSV/JSON exports, CSV/JSON file imports through a single unified ingestion funnel, and a dedicated raw clipboard paste-to-import dialog (`ImportBackupDialog`). Developers and users can test and preview the live classification pipeline in real-time using an integrated **Testing Sandbox** (`TestingSandboxPage`) complete with typed inputs, SIM slot selection, and one-tap preset scenario chips (Bank Transaction, Due Bill, Spam, Friend Message, Malayalam OTP, Google OTP). The UI features a customizable multi-palette Material 3 dynamic theme engine (Lavender, Sage, Slate, Blue, High Density) with Light, Dark, and System Auto modes.

---

## Key Features & Capabilities

### 1. Privacy Architecture & Technical Stack
* **100% Offline On-Device Execution**: Zero cloud dependency, zero external network API calls, zero tracking SDKs, and zero telemetry. Classification and features execute strictly on-device using local Kotlin business logic and Room SQLite.
* **Single Gradle Module Architecture**: Built as a clean single-module Android app under namespace `in.sreerajp.sms_sentry` targeting modern Android SDK standards with Compose Material 3 toolkits.
* **Room SQLite Schema & Versioning**: Robust persistence using Room SQLite database (`version = 6`) with clean sequential migrations managing core entities: `SMSMessage`, `FilterRule`, `FinanceTx`, `ReminderSms`, and `ScheduledSms`.
  * `MIGRATION_1_2`: Added local read/unread tracking column (`isRead`).
  * `MIGRATION_2_3`: Added system provider sync IDs (`systemId`, `threadId`), MMS indicators (`isMms`, `attachmentUri`), message type (`type`), and outgoing status (`status`).
  * `MIGRATION_3_4`: Added `scheduled_messages` table for queued future SMS delivery.
  * `MIGRATION_4_5`: Executed category data normalization (`Finance` -> `Accounts`).
  * `MIGRATION_5_6`: Added reminder recurrence (`recurrence`) and in-app alert flags (`alertEnabled`).
* **Reboot Resilience Architecture**: Automatic system alarm re-arming across device reboots via `BootReceiver` (`RECEIVE_BOOT_COMPLETED`) for both future scheduled SMS deliveries and reminder due-date notifications (`ReminderAlarmScheduler` & `ScheduledSmsScheduler`).
* **Modern Jetpack Compose & Material 3 UI**: Declarative UI built with custom dynamic theme engines, high-density visual cards, dialog overlays, smooth transition animations (`AnimatedContent`), and responsive layouts.
* **Reactive State Architecture**: `StateFlow` and `SharedFlow` reactive data streams backed by Room SQLite DAO observables for real-time UI synchronization across all navigation tabs.
* **Single Ingestion Funnel**: Every message source (live broadcast receivers, system default app delivery, MMS payloads, P2P sync, CSV/JSON backups, demo seeds, and testing sandbox inputs) passes through `SmsRepository.processAndInsertMessage()`, ensuring unified rule evaluation, de-duplication, classification, and derived ledger/reminder creation.
* **Dual Operational Modes**: Functions either as a passive, read-only SMS organizer (no default SMS status required) or as the complete system **Default SMS Handler**.
* **Biometric & Device Security Lock**: Integrated `BiometricPrompt` and `KeyguardManager` device lock protecting liquid savings cards, financial account ledgers, and transaction details with instant hide/reveal and tap-to-relock controls.
* **Granular Android Permission Management**: Runtime permission manager for `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, `READ_CONTACTS`, `READ_PHONE_STATE`, `SCHEDULE_EXACT_ALARM`, `CAMERA`, and `POST_NOTIFICATIONS` with background data import upon grant.

### 2. Dashboard & Analytics Module
* **Real-Time Security Banner**: Dynamic visual status banner indicating local security scan engine state ("Engine active · 0 threats").
* **Liquid Savings & Net Balance Card**: Interactive card aggregating current parsed balances across recognized financial institutions with biometric lock/reveal and tap-to-relock control.
* **Category Distribution Breakdown**: Visual progress/ring chart illustrating real-time message distribution across **Personal**, **Promotions**, **Others**, and **Spam** categories with color keys.
* **Quick Metrics Grid**: Stat cards displaying counts of Spam Blocked, Pending Task Reminders, and Total Inbox Messages.
* **Interactive Navigation Shortcuts**: Direct tap targets to jump straight to Inbox, Accounts, Reminders, Sync, and Settings tabs.

### 3. Offline Smart Categorization & Taxonomy
* **4-Category Organization Taxonomy**: Messages are sorted into four core categories: **Personal**, **Promotions**, **Others**, and **Spam**.
* **Content-Derived Supplemental Flags**: Financial transactions (`isFinance`) and obligation reminders (`isReminder`) are flagged based on body content independently of category placement, allowing finance and reminder messages to surface in the "Others" inbox while populating derived ledgers.
* **OTP Detection & Auto-Extraction Engine**: Dedicated regular expression engine that automatically identifies and extracts 4 to 8-digit One-Time Passwords (OTPs) alongside contextual validation keywords in both English (`otp`, `one time password`, `code`, `verification`) and regional Malayalam formats (`ലോഗിൻ`, `ഒ.ടി.പി`, `പാസ്‌വേഡ്`, `രഹസ്യകോഡ്`).
* **Ingestion & Re-Categorization De-Duplication**: Automatic duplicate detection skipping identical incoming messages (same sender, body, and timestamp) and preferring system-backed survivors (`systemId != null`).
* **Legacy Category Normalization**: Legacy category buckets (`Accounts`, `Reminder`, `Services`) are automatically mapped into `Others` while preserving underlying content flags (`isFinance`, `isReminder`).
* **Custom Keyword & Contact Rules**:
  * **Keyword Rules**: Custom keyword matches to route messages into designated target categories or block them.
  * **Contact Rules**: Map specific phone numbers or sender IDs to categories, including explicit **Allowlist** (`NotSpam`) and **Blocklist** (`Blocked`/`Spam`) rules.
* **Professional Weighted Offline Spam Engine**:
  * Evaluates scam phrases (+3 weight), URL shorteners (`bit.ly`, `tinyurl`, `goo.gl`, `t.co/`, `is.gd`, `cutt.ly`, `rb.gy`, `tiny.cc`, `ow.ly`, +1 weight), and high-pressure urgency cues (+1 weight).
  * Uses boundary-anchored regex (`\b...\b`) to eliminate false-positive substring matches (e.g., distinguishing "investment" from "invest").
  * Offsets scam scores against trusted institutional sender DLT headers (-5 weight for banks, pensions, insurers, government agencies, telecom operators: HDFC, SBIBNK, SBI, ICICI, AXIS, HSBC, CITI, KOTAK, PNB, BOI, YESBK, INDUS, CANBNK, UNIONB, IDFC, RBLBNK, PTNNPS, NPSCRA, PROTEAN, NSDL, PFRDA, CAMSKRA, NIALTD, NIACL, LICIND, HDFCLI, ICICIP, SBILIF, MAXLIF, STARHE, MORTH, ITDCPC, UIDAI, EPFO, IRCTC, AIRTEL, JIONET, VODAFON, BSNL) and legitimate transactional context keywords (-2 weight).
  * Classifies message as Spam when total score reaches `SPAM_THRESHOLD = 3`.
* **Promotional Offer Prioritization**: Promotional coupons and cashback offers containing money verbs (e.g., "Rs.300 credited!") are correctly categorized under Promotions rather than financial transactions.
* **Sender Type Heuristics**: Sender IDs looking like dialable phone numbers are categorized as Personal, while alphanumeric DLT headers or short codes fall back to Others.
* **Sender Spam Reporting & Allowlisting**: One-tap "Report Spam" (`reportSpamSender`) moves all messages from a sender to Spam and creates a CONTACT->Spam rule; "Not Spam" (`markNotSpamSender`) allowlists the sender with a CONTACT->NotSpam rule and restores messages.
* **Manual Category Overrides**: Capability to reassign single messages or full conversation threads to any target category (`moveMessageToCategory` / `moveConversationToCategory`).
* **Bulk Re-Categorization Engine**: On-demand utility in Settings to re-evaluate and re-sort all stored messages whenever classification rules or regex patterns are updated, rebuilding derived ledgers, purging historical stored duplicates, and refreshing reminder due dates.

### 4. Automated Finance Ledger & Accounts Module
* **Automatic Financial Field Extraction**: Extracts bank/institution names, transaction amounts, credit vs. debit movement direction, and account balance values from transactional SMS messages.
* **Automatic Bank Recognition**: Identifies financial institutions from sender DLT headers or body text (HDFC, SBI, ICICI, AXIS, HSBC, CITI, CHASE, BOFA, KOTAK, PNB, BOI, YESBK, INDUS, fallback provider ID).
* **Passbook Balance Disambiguation**: Dedicated algorithm isolates account balance numbers following balance keywords (`avail bal`, `a/c bal`, `closing bal`, `bal`), preventing passbook balances from being misidentified as transaction figures, and excluding info-only balance SMS with zero transaction amount.
* **Payment Receipt Inversion**: Intelligently handles payment receipts ("payment received for X", "received your payment", "received towards"), categorizing them as debits (user payment) rather than credits.
* **Derived Accounts & Monthly Cash Flow View**: Displays net financial balances, monthly credit vs. debit cash flows (Income vs. Expense), and overall spending statistics without creating separate message categories.
* **Interactive Monthly Cash Flow Contribution Breakdown Sheet**: Interactive drilldown for monthly credit and debit card totals to inspect individual contributing SMS messages (`ContribKind.CREDIT` / `ContribKind.DEBIT`) and navigate directly to the underlying message text.
* **Payment Status Tracking**: Capability to mark specific transaction messages as "paid" or resolved directly from the detail view (persisted via `paid_message_ids`).
* **Biometric Security Gate**: Accounts tab access is protected by device biometric / keyguard authentication (`isFinanceLocked` / `triggerDeviceAuthentication`).

### 5. Intelligent Task & Obligation Reminders
* **Automatic Obligation Extraction**: Identifies utility bills, credit card dues, subscription renewals, appointments, and recharges from incoming messages.
* **Forward-Looking Obligation Phrase Matching**: Filters for forward-looking phrases (`due on`, `due date`, `due by`, `payment due`, `pay by`, `last date`, `bill due`, `outstanding`, `overdue`, `expires on`, `expiring`, `expiry`, `valid till`, `valid until`, `validity`, `renew`, `renewal`, `recharge before`, `appointment`, `reminder`, `kindly pay`, `please pay`).
* **Receipt Marker Exclusion**: Completed transaction receipts are excluded from reminder creation unless a strong forward-looking obligation phrase is present.
* **Multi-Format Date Parsing**: Extracts due dates from ISO format (`YYYY-MM-DD`), standard formats (`DD/MM/YYYY`, `DD-MM-YY`, `DD.MM.YY`), alpha dates (`16-Jun-2026`, `16 Jun 2026`, `16.Jun.2026`, `16/Jun/2026`), and relative keywords (`today`, `tomorrow`, 3-day fallback). Prefers earliest future date relative to message timestamp over past dates.
* **Unicode Dash Tolerance**: Supports non-standard Unicode dashes (non-breaking hyphen `U+2011`, figure dash `U+2012`, en dash `U+2013`, em dash `U+2014`, minus sign `U+2212`) alongside standard ASCII separators.
* **Exact AlarmManager Due Alerts**: Arms exact system alarms for reminder due dates (at 09:00 local on due date via `ReminderAlarmScheduler`, single reconcile funnel) with configurable advance lead-time notice (0 to 30 days), near-due highlighting thresholds, and vibration controls. Alarms are re-armed automatically after a device reboot via `BootReceiver`, so due-date alerts survive a restart.
* **Dual-Channel Reminder Notifications**: Dynamically routes due alerts to vibration-enabled (`REMINDER_CHANNEL_VIB_ID`) or silent (`REMINDER_CHANNEL_NOVIB_ID`) notification channels based on user preferences.
* **Flexible Recurrence Engine**: Supports recurring reminder schedules (**None**, **Daily**, **Weekly**, **Monthly**, **Yearly** via `RecurrenceUtil`) that automatically advance to the next future occurrence upon completion instead of expiring.
* **System Calendar Export**: One-tap handoff to export reminder details into native system calendar apps via `ACTION_INSERT` (`CalendarContract.EVENTS`).
* **Manual Reminder Creation & Editing**: Ability to create or edit custom reminders for any arbitrary SMS message directly within the detail sheet.
* **Automated Expired Reminder Purging**: Automatically purges expired one-shot reminders on app launch and re-categorization (cutoff at local midnight) while retaining recurring (`recurrence != 'NONE'`) and near-due obligations.

### 6. Full Default SMS App & Rich Messaging Composer
* **Default SMS App Support**: Fully compliant Android default SMS handler supporting incoming `SMS_DELIVER` broadcasts (`SmsDeliverReceiver`), `WAP_PUSH_DELIVER` (MMS parsing via `MmsParser`), outgoing `SmsManager` delivery status tracking (`SENT`, `DELIVERED`, `FAILED` via `SmsSendStatusReceiver`), and `HeadlessSmsSendService` (`RESPOND_VIA_MESSAGE`).
* **Folder-Scoped Inbox Search**: Live search bar (`inbox_search_field`) filters messages within the active category folder by sender or body text, showing matching results with a clear button and an empty-state indicator, and jumping straight into the matched sender's thread on tap.
* **Message Share/Forward**: One-tap share of an individual message's text to any other installed app via the system share sheet (`ACTION_SEND`).
* **Grouped Conversation Threads**: Groups inbox and sent messages into per-sender chat threads (`ThreadScreen`) with instant scroll-to-bottom, unread indicators, and draft status markers.
* **System Contact & Avatar Integration**: Integrates with `ContactsContract` via `ContactNameResolver` to resolve phone numbers into contact display names and avatar photos; includes one-tap "Add to contacts" (`ACTION_INSERT_OR_EDIT`) handoff for unknown senders.
* **Smart Thread Matching**: Trailing 10-digit matching algorithm (`digitsTail`) for phone numbers to fold incoming messages with country code variations into existing conversation threads seamlessly.
* **MMS Media Attachment Rendering**: Parses SMIL and multipart WAP PDU payloads (`MmsParser`) and renders embedded image attachments (`attachmentUri`) within threads and detail overlays via `SystemMmsStore`.
* **Sender Information & History Sheet**: Dedicated sheet (`SenderInfoSheet`) displaying sender stats, category breakdown, fast category overrides, notification muting toggles, and allowlist controls.
* **Rich Messaging Composer**:
  * **Dual-SIM Routing**: Select preferred SIM slot (SIM 1, SIM 2, or Ask Every Time) for outgoing messages, dynamically resolving active subscription IDs via `SimManager`.
  * **Dual-Layer Draft Persistence**: Per-thread unsent text drafts (`drafts`) AND standalone new-message composer draft persistence (`compose_draft_recipient` / `compose_draft_body`) across app sessions.
  * **Character & Segment Counter**: Tracks character counts and GSM-7 (160 single / 153 multi) vs. Unicode (70 single / 67 multi) SMS segment limits via `SmsSegment`.
  * **Contact Auto-Suggestions**: Auto-suggests matching contacts while typing recipient numbers (`ContactNameResolver.queryContacts`).
  * **Deep-Link Intent Handoff**: Handles `sms:`, `smsto:`, `mms:`, `mmsto:`, and `SENDTO` deep-link intents from external apps with pre-filled recipient and body text.
* **Scheduled SMS Queueing & Management**: Schedule messages for future delivery backed by Room SQLite persistence (`ScheduledSms` entity), Android `AlarmManager` exact alarms (`ScheduledSmsScheduler`), `ScheduledSmsReceiver`, and `BootReceiver` alarm re-arming across device reboots. Users can view queued scheduled SMS messages and cancel/delete pending deliveries before they fire.
* **Failed Message Resend**: One-tap retry action for outgoing messages in `FAILED` state.
* **Multi-Select & Bulk Operations**: Long-press selection to mark multiple conversations/messages as read/unread, delete messages (with two-way system provider synchronization), or block/unblock senders.
* **Technical Message Info Inspector**: Detailed metadata sheet (`MessageInfoSheet`) inspecting raw sender, SIM slot, date received, date sent, read/seen status, thread ID, provider delivery status, subscription ID, service center address, protocol, and error codes.

### 7. Notifications & Smart Actions
* **Custom Theme-Matched RemoteView Notifications**: Styled system notifications (`SmsNotificationHelper`) displaying message snippets, sender details, and carrier SIM tags matching active app theme palettes (`notification_otp`).
* **One-Tap Quick Actions**:
  * **Copy OTP**: Automatically detects and extracts One-Time Passwords into clipboard with one tap (`ACTION_COPY_OTP`), updating notification UI state to "Copied" (green highlight).
  * **Mark as Read**: Marks message thread read directly from notification shade.
  * **Delete Message**: Deletes message directly from database and system provider from notification shade (`ACTION_DELETE_MESSAGE`).
  * **Inline Quick Reply**: Direct reply input (`RemoteInput`) straight from notification action bar (when active as Default SMS app).
  * **Open Action**: Direct `PendingIntent.getActivity()` launch ensuring compliance with Android 12+ notification trampoline restrictions.
* **Sender Muting**: Mute notifications for specific high-volume senders while retaining messages in the inbox.
* **Notification Vibration Control**: Toggle notification vibration preferences across dual channels (`REMINDER_CHANNEL_VIB_ID` vs. `REMINDER_CHANNEL_NOVIB_ID`).

### 8. End-to-End Encrypted Offline Peer-to-Peer (P2P) Sync
* **Serverless Local Wi-Fi / Hotspot Transport**: Connects two nearby devices over a direct TCP socket connection on random OS-assigned ports without cloud relay.
* **High-Entropy AES-256-GCM Encryption**: Encrypts all sync payloads with AES-256-GCM, using keys derived via `PBKDF2WithHmacSHA256` (300,000 iterations) from a 64-character pairing code (~320 bits of entropy over a 31-symbol unambiguous alphabet `23456789ABCDEFGHJKMNPQRSTUVWXYZ`).
* **QR Code Pairing & Camera Scanning**: Host device generates a pair-and-connect QR code (`SyncQr`: `smssentry://sync?v=1&ip=...&port=...&code=...`) scanned via device camera (ZXing library integration) or manual code entry.
* **Connect-Then-Choose Workflow**: Host authenticates the peer connection first (`v:3` wire protocol), then interactively selects what categories to share:
  * **Full Sync** or selective subsets (**Messages**, **Filter Rules**, **Finance Transactions**, **Reminders**, **Scheduled SMS**, **Settings**).
* **Add-Only Non-Destructive Sync**: Client-wins merge logic skips records already present based on natural keys, preserving existing local user data and host classification.
* **Wire Protocol Security & Bounding**: Bounded line-length readers (`readBoundedLine`, 4 KB handshake / 64 MB payload line caps) and payload item caps (MAX_MESSAGES 100k, MAX_RULES 10k, MAX_FINANCE 100k, MAX_REMINDERS 100k, MAX_SCHEDULED 10k, MAX_FIELD_LEN 100k) to prevent OOM/DoS from untrusted peers. Handshake timeout (30s), payload wait timeout (10 min), host idle auto-stop (120s), single-client lock, and peer drop detection.
* **Local Provider Field Exclusion**: Device/provider-specific fields (`systemId`, `threadId`, `attachmentUri`) are intentionally excluded from sync payloads to preserve target device database integrity.
* **Syncable Settings Allow-List**: Syncs non-sensitive behavior preferences (`SyncSettings`: reminder alerts, lead days, near days, vibration, auto-mark-read seconds) while excluding device/theme specific configs.

### 9. Data Backup, Export & Portability
* **CSV & JSON Backup/Export**: Export message database to standard CSV or JSON files via `SmsShareUtils` for local backup or transfer.
* **CSV & JSON Import**: Import CSV/JSON files back through the unified ingestion funnel (`SmsShareUtils.importFromCsv` / `importFromJson`), ensuring classification, deduplication, and derived ledger/reminder creation.
* **Paste-to-Import Raw Backup**: Dedicated dialog (`ImportBackupDialog`) to paste a raw CSV string or JSON payload directly from the clipboard for instant restore without needing file system access.
* **System Provider Synchronization**: Two-way sync with Android `content://sms` and `content://mms` system databases when acting as Default SMS App (`SystemSmsStore` / `SystemMmsStore`).
* **Database Reset**: Ability to wipe local SMS database on demand (`clearAllSms`).

### 10. Customization & System Controls
* **Multi-Palette Theme Engine**: Customizable UI themes including **Lavender**, **Sage**, **Slate**, **Blue**, and **High Density** styles.
* **Theme Modes**: Supports Light, Dark, and System Auto dark mode toggle.
* **Auto-Mark-Read Delay**: Configurable timer (0 to 3600 seconds, default 3s) before an opened conversation automatically marks messages as read.
* **Reminder Preference Controls**: Configurable lead days (advance alert notice), near-due highlighting thresholds, and vibration preferences.
* **Explicit Permissions Manager**: Built-in UI to audit and request necessary system permissions (`READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, `READ_CONTACTS`, `READ_PHONE_STATE`, `SCHEDULE_EXACT_ALARM`, `CAMERA`, `POST_NOTIFICATIONS`) with automatic background import upon permission grant.
* **About & Build Metadata**: Displays app version, author, last build date, IDE used, and AI tooling used (`AboutConfig`).
* **Testing Sandbox**: Dedicated Settings ▸ Testing page (`TestingSandboxPage`) that feeds a typed fake sender + message body through the real, live ingestion and classification pipeline (`viewModel.simulateSmsReceived`) to preview how a message would be categorized, without needing an actual incoming SMS. Includes one-tap preset scenario chips (Bank Transaction, Due Bill, Spam, Friend Message, Malayalam OTP, Google OTP) and a SIM slot selector, providing real-time UI feedback for category, flags, spam score breakdown, extracted fields (bank, amount, balance, due date), and extracted OTP code.
