# Change log: Consolidate to four categories + on-demand re-categorization

Implements plan
[plans/20260618_100208_four-category-consolidation.md](../plans/20260618_100208_four-category-consolidation.md).

## Summary

- Collapsed the five message categories (`Personal`/`Accounts`/`Reminder`/`Services`/`Spam`)
  into **four**: `Personal`, `Promotions`, `Others`, `Spam`.
- **Decoupled finance & reminders from the category string.** The classifier now flags messages
  (`ClassificationResult.isFinance` / `isReminder`) from *content*; finance/bill messages surface
  under `Others` but still create `FinanceTx` / `ReminderSms` rows. The **Accounts money ledger
  (with its device-auth lock) and the Reminders tab are preserved** as derived views over those
  rows — that is the "finance security" requirement.
- **Re-categorization is user-triggered** from Settings ▸ Categorization ▸ "Re-categorize all
  messages" (re-runs the classifier over every stored message, rebuilds the ledger, fills missing
  reminders). No DB schema/version change; legacy category strings are normalized to the new four.

## Decisions applied

- Remap intent: `Personal→Personal`, `Spam→Spam`, `Promotions→Promotions` (new),
  everything else (`Accounts`/`Reminder`/`Services`/unknown) → `Others`.
- Classifier priority: `Spam → finance(→Others) → reminder(→Others) → Promotions →
  services(→Others) → Personal`. Old `SPAM_KEYWORDS` split into genuine-spam vs a new
  `PROMO_KEYWORDS` (offer/discount/sale/cashback/coupon/% off/buy now/limited time…).
- Supplemental rows now keyed on the content flags, not the category, everywhere they are
  written (`processAndInsertMessage`, `restoreFromSpam`, `moveMessageToCategory`, the new bulk
  re-categorize).
- Bulk re-categorize rebuilds finance rows (purely derived) but only *adds* missing reminders,
  so manually-added reminders survive.
- No automatic migration (per user): DB stays `version = 5`. `SmsClassifier.normalizeCategory()`
  keeps legacy data/rules sane until the user taps the button.

## Files changed

- `engine/SmsClassifier.kt` — `ClassificationResult` gains `isFinance`/`isReminder`; new
  `PROMO_KEYWORDS`, `MONEY_KEYWORDS` (was `ACCOUNTS_KEYWORDS`); `normalizeCategory()`; `classify()`
  emits the four categories with the new priority; `runExtractions()` derives flags + extracts
  amount/balance/due-date from content (no longer keyed on the category).
- `data/SmsRepository.kt` — supplemental-row creation keyed on `isFinance`/`isReminder` in all
  three paths; new `recategorizeAllMessages(): Int`; seed rules retargeted (`OTP`/`RECHARGE`/
  `PAYMENT` → `Others`); seed messages reworked (added two Promotions samples, dropped the
  discount-spam one); comments updated.
- `data/SmsDao.kt` — added `getAllMessagesOnce()`, `getAllRulesOnce()`, `updateRuleCategory()`,
  `getReminderMessageIds()` for the bulk action.
- `data/SmsEntities.kt` — category doc comments → the four (+ control values).
- `data/SmsDatabase.kt` — note that categories are re-derived on demand (no new migration).
- `ui/theme/Color.kt` — category colours → Personal (violet), Promotions (amber), Others (teal),
  Spam (red); removed Accounts/Reminder/Services colours.
- `ui/theme/Theme.kt` — `categoryColors()` returns 4; `categoryColor()` maps the four (legacy →
  Others colour).
- `ui/SmsOrganizerUi.kt` — Dashboard counts/segments/breakdown rows (Promotions/Others); Inbox
  folder list, filters, counts, pill colours; move-to-category targets; rules dialog category
  chips; **new Settings "Categorization" card with the "Re-categorize all messages" button**.
  (Accounts/Reminders bottom-nav tabs + their lock left intact — now derived views.)
- `ui/SmsOrganizerViewModel.kt` — `recategorizeAllMessages(onDone)`; updated doc/comment.
- `test/SmsClassifierTest.kt` — money→`Others`+`isFinance`; OTP/delivery→`Others`; added
  Promotions + legacy-rule-normalization tests; allowlist test body uses genuine-spam words.
- `test/ExampleRobolectricTest.kt` — OTP fixture category `Services`→`Others`.
- `docs/architecture.md` — category list, ingestion-funnel note, finance-lock note updated.

## Verification

- `./gradlew.bat :app:testDebugUnitTest` — **BUILD SUCCESSFUL**; all 12 unit tests pass. App
  compiles (only pre-existing deprecation warnings). Roborazzi re-records the Dashboard
  screenshot (breakdown now shows Promotions/Others rows).
