# Change log: Intelligent reminders, auto-expiry, nav count badge

Implements [plans/20260627_151735_intelligent-reminders.md](../plans/20260627_151735_intelligent-reminders.md).

## What changed

### 1. Smarter reminder detection — `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
- Rewrote `REMINDER_KEYWORDS` to forward-looking obligation phrases only (added `due by`,
  `payment due`, `last date`, `bill due`, `overdue`, `expires on`, `expiring`, `expiry`,
  `valid till`, `valid until`, `validity`, `renewal`, `kindly pay`, `please pay`). Removed the
  noisy `"scheduled"` (which appears in *past* transaction confirmations) and `"bill payment"`.
- Added `STRONG_DUE_KEYWORDS` (the same set minus the soft `reminder`/`appointment` cues) and
  `RECEIPT_MARKERS` (`credited`, `debited`, `payment received`, `txn ref`, `ref no`, …).
- Changed the `isReminder` rule in `runExtractions`: a message is a reminder only when it has a
  reminder keyword, is not a promotional offer, and is not a completed-transaction receipt
  unless it also carries a strong "do this by <date>" phrase. Removed the old
  `body.contains("due", true)` substring match.

### 2. Better due-date extraction — `SmsClassifier.kt`
- Added `isoDateRegex` for `yyyy-MM-dd` (tried first) and extended `parseDate` with
  `yyyy-MM-dd`/`yyyy/MM/dd`/`yyyy.MM.dd` formats. Fixes "2027-06-12" previously mis-parsed as
  "27-06-12" → year 0012.
- Now collects *all* candidate dates (ISO, DD-MM-YY, alpha-month) and picks the earliest
  **future** date, falling back to the latest past date only when no future date exists — so a
  receipt's past transaction date is not chosen when a real deadline is present.

### 3. Auto-remove expired reminders
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt`: added
  `deleteExpiredReminders(cutoff)` DELETE query.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`: added
  `purgeExpiredReminders()` (cutoff = local midnight today, so today's reminders survive).
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`: `init` launches the
  purge on startup; the `reminders` Flow updates automatically. No DB schema/version change.

### 4. Red count badge on the Reminders nav button — `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Collect `navReminders` in `SmsOrganizerApp` and wrap the Reminders `NavigationBarItem` icon
  in a `BadgedBox` with a red `Badge` (`error`/`onError`) showing the active count when > 0.

## Verification
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL (existing tests pass).
