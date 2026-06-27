# Plan: Make Reminders intelligent, auto-expire, and badge the nav count

**Status:** completed

## The issue

The Reminders section (screenshots) is not "intelligent":

1. **Too many false positives.** Plain transaction confirmations and receipts are being
   saved as reminders ("Your a/c is credited by Rs.1.00 … IMPS", "SBI Txn Ref … is Scheduled
   for 22/06/2022", "Thank you for the payment of Rs.745 …"). These are not actionable
   reminders. Root cause is in [SmsClassifier.kt](app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt):
   - `isReminder = REMINDER_KEYWORDS.any { … } || body.contains("due", true)` is far too
     loose. `body.contains("due", true)` matches substrings, and `REMINDER_KEYWORDS`
     contains `"scheduled"`, which appears in *past* transaction SMS ("Txn … is Scheduled
     for 22/06/2022").

2. **Wrong / past due dates, never removed.** Reminders show dates in 2022/2023 (already
   expired) and even garbage years like "0012". Root causes:
   - The due-date extractor grabs the **first** date in the body — for a receipt that is the
     (past) transaction date.
   - `dateRegex1` does not understand ISO `yyyy-MM-dd` (e.g. "PUC validity is 2027-06-12"),
     so it mis-matches the substring "27-06-12" → parsed as 27 Jun 2012 ("0012"-ish), or
     falls back to "3 days from now".
   - Nothing ever deletes a reminder whose due date is in the past.

3. **No count badge.** The Reminders bottom-nav button shows no count of active reminders.

## The fix

### 1. Tighten reminder detection (intelligence) — `SmsClassifier.kt`
- Replace `REMINDER_KEYWORDS` with a forward-looking, obligation-only set and drop the
  noisy `"scheduled"` and bare-substring `"due"` matching. New set (lowercase phrases):
  `"due on"`, `"due date"`, `"due by"`, `"payment due"`, `"pay by"`, `"last date"`,
  `"bill due"`, `"outstanding"`, `"overdue"`, `"expires on"`, `"expiring"`, `"expiry"`,
  `"valid till"`, `"valid until"`, `"validity"`, `"renew"`, `"renewal"`, `"recharge before"`,
  `"appointment"`, `"reminder"`, `"kindly pay"`, `"please pay"`.
- Add `RECEIPT_MARKERS` (completed-transaction phrases): `"credited"`, `"debited"`,
  `"thank you for the payment"`, `"payment received"`, `"received your payment"`,
  `"txn ref"`, `"ref no"`, `" successful"`, `"has been credited"`, `"is credited"`.
- New rule in `runExtractions`:
  `isReminder = REMINDER_KEYWORDS.any { body.contains(it) } && !isPromotionalOffer(body)
   && !(isReceipt && !hasStrongDuePhrase)` — i.e. a pure receipt is **not** a reminder
  unless it also carries an explicit due/expiry/renew phrase. (`hasStrongDuePhrase` = any of
  the due/expiry/renew/pay-by phrases above, excluding the soft `"reminder"`/`"appointment"`.)
- Update the `classify()` branch that routes on `containsReminderKeyword` to use the same
  tightened list (keep routing such messages to `"Others"`).

### 2. Better due-date extraction — `SmsClassifier.kt`
- Add ISO support: a regex for `\d{4}[-/.]\d{1,2}[-/.]\d{1,2}` (yyyy-MM-dd) tried **first**,
  with `parseDate` formats extended (`yyyy-MM-dd`, `yyyy/MM/dd`).
- When multiple dates are present, prefer a **future** date (>= today) over a past one, so a
  receipt's past transaction date is not chosen when a real future date exists. If only past
  dates exist, keep the existing behaviour (used together with auto-expiry below).
- Keep the tomorrow/today/3-day fallbacks.

### 3. Auto-remove expired reminders — `SmsDao.kt`, `SmsRepository.kt`, `SmsOrganizerViewModel.kt`
- `SmsDao`: add `@Query("DELETE FROM reminders WHERE dueDate < :cutoff") suspend fun
  deleteExpiredReminders(cutoff: Long)`.
- `SmsRepository`: add `suspend fun purgeExpiredReminders()` computing `cutoff` = start of
  **today** (midnight, local) so a reminder due today still shows, then calling the DAO.
- `SmsOrganizerViewModel.init`: launch `repository.purgeExpiredReminders()` on startup so
  stale reminders are cleared each launch. (The `reminders` Flow updates automatically.)
- No DB schema/version change (only a new DELETE query), so no Room migration needed.

### 4. Red count badge on the Reminders nav button — `SmsOrganizerUi.kt`
- In the main scaffold composable, collect `val reminders by viewModel.reminders.collectAsState()`.
- Wrap the Reminders `NavigationBarItem` icon in a `BadgedBox` and show a red `Badge`
  (`containerColor = MaterialTheme.colorScheme.error`, `contentColor = onError`) with the
  count when `reminders.isNotEmpty()`. Other nav items render their icon unchanged.

## Files to change
- `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt` — tighten reminder
  keywords/receipt exclusion; ISO date + future-date-preference extraction.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt` — `deleteExpiredReminders`.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt` — `purgeExpiredReminders`.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt` — purge on init.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — red count badge on the
  Reminders nav item.

## Notes / trade-offs
- Existing bad reminders from past classification: the auto-expiry purge clears the past-dated
  ones immediately on next launch. Genuinely mis-detected future ones can be re-cleaned by the
  existing Settings "re-classify" path (it only adds missing reminders, so it won't recreate
  ones the user dismissed; tightened detection means fewer get created going forward).
- Classifier changes only affect newly processed / re-classified messages; the badge +
  auto-expiry give immediate visible improvement without a DB migration.
```
```

## Verification
- Build: `./gradlew :app:assembleDebug` (no wrapper — use the documented build command).
- Existing `ExampleRobolectricTest` should still pass; optionally add a unit check that a
  "credited … IMPS" receipt is not flagged as a reminder and an "expires on <future>" is.
