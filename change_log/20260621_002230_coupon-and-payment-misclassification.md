# Change log: coupon & "payment received" misclassification

Implements [plans/20260621_002230_coupon-and-payment-misclassification.md](../plans/20260621_002230_coupon-and-payment-misclassification.md).

## What was changed

### `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`

1. **Promotional-offer override (Issue 1 — coupons logged as credit).**
   - Added `COUPON_OFFER_MARKERS` (`coupon`, `voucher`, `% off`, `percent off`, `use code`,
     `promo code`, `shop now`, `shop at`, `buy now`) and a private `isPromotionalOffer()` helper.
   - In `classify()` heuristics, a body matching these markers now routes to **Promotions**,
     ahead of the money/`Others` branch — so a coupon that says "Rs.300 credited!" is marketing,
     not a transaction.
   - In `runExtractions()`, the `isFinance` flag is now gated with `&& !isPromotionalOffer(...)`,
     so no ledger entry/amount is produced for coupons even via a custom rule.

2. **Payment-receipt direction fix (Issue 2 — "payment received" logged as credit).**
   - In `extractFinanceFields()`, when the body contains `payment received`,
     `received your payment`, or `received towards`, the direction is forced to **debit**. Normal
     credits ("Rs X credited", "salary received", "received from/in a/c") are unaffected.

### `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`

Added three regression tests:
- Clovia coupon → `category == "Promotions"`, `isFinance == false`.
- MoRTH PUC payment → `isFinance == true`, `isCredit == false`, `amount == 100.0`.
- Plain "Rs.1495 credited … avail bal" → still `isFinance == true`, `isCredit == true`.

## Verification

`./gradlew.bat :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.SmsClassifierTest"` —
BUILD SUCCESSFUL, all tests pass.

## Notes / scope

- Pure classification logic; no DB schema/migration change.
- Only affects newly ingested messages. The two already-logged rows are unchanged unless the user
  runs Settings ▸ Categorization ▸ "Re-categorize all messages".
