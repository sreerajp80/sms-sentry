# Fix: coupons logged as credit, and "payment received" logged as credit

## Issues

Two real-world SMS are misclassified by the offline classifier
([SmsClassifier.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt)):

### Issue 1 — promo coupon treated as a money credit
> Rs.300 credited! Your Clovia coupon GK574A8794EDFE gives you flat Rs 300 Off.
> Valid till 11.59 PM only! Shop at http://u3.mnge.co/CLVLNG/5WErQzQ

- `credited` is in `MONEY_KEYWORDS`, so `containsMoneyKeyword` is true and the **money
  branch wins** over the promo branch in `classify()` (line ~154). Category becomes `Others`.
- `runExtractions()` re-derives `isFinance` directly from `MONEY_KEYWORDS` (line ~176), so the
  message is flagged finance and a **Rs.300 credit** ledger entry is created. It is actually a
  marketing coupon and should be **Promotions**, with **no** ledger entry.

### Issue 2 — "payment received" (a charge the user paid) treated as a credit
> Your PUC certificate validity is 2027-06-12 and payment received for certificate is Rs.100
> (excluding GST). (MoRTH)

- `received` is in `creditWords`, so the Rs.100 is logged as money **in** (credit). In context,
  "payment received for certificate" is a **receipt of money the user paid** — i.e. a **debit**
  (a charge/expense), not a credit. It should remain finance but be recorded as a debit.

## Files to change

1. `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt` — the fix.
2. `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt` — regression tests.

## Plan

### A. Promotional-offer override (Issue 1)

Add a high-precision detector for unambiguous advertising markers (a real bank transaction
won't contain these):

```kotlin
private val COUPON_OFFER_MARKERS = listOf(
    "coupon", "voucher", "% off", "percent off", "use code", "promo code",
    "shop now", "shop at", "buy now"
)
private fun isPromotionalOffer(normalizedBody: String): Boolean =
    COUPON_OFFER_MARKERS.any { normalizedBody.contains(it) }
```

- In `classify()` heuristics: when `containsMoneyKeyword` is true **but** `isPromotionalOffer()`
  is also true, route to **Promotions** instead of the money/`Others` branch. (Place the
  override so promo wins over money only when these markers are present; non-coupon money SMS
  are unaffected.)
- In `runExtractions()`: gate the finance flag —
  `val isFinance = MONEY_KEYWORDS.any { ... } && !isPromotionalOffer(normalizedBody)` — so no
  ledger entry/amount is produced for coupons even if they slip in via a custom rule.

Precision check: existing "Rs.1495.00 credited … Avail bal" test has no coupon marker → stays
finance/Others. Existing "Flat 50% OFF … coupon" test → stays Promotions.

### B. Payment-receipt direction fix (Issue 2)

In `extractFinanceFields()`, after computing `isCredit`, override to **debit** when the body is a
payment receipt:

```kotlin
val lower = body.lowercase(Locale.ROOT)
val isPaymentReceipt = lower.contains("payment received") ||
    lower.contains("received your payment") || lower.contains("received towards")
val finalCredit = if (isCredit == true && isPaymentReceipt) false else isCredit
```

This flips only the "payment received"/"received your payment"/"received towards" receipt
phrasing; "Rs X credited", "salary received", "received from", "received in a/c" stay credits.

### C. Tests

Add to `SmsClassifierTest.kt`:
- Coupon SMS (the Clovia example) → `category == "Promotions"`, `isFinance == false`.
- PUC SMS (the MoRTH example) → `isFinance == true`, `isCredit == false` (debit), `amount == 100.0`.
- Guard: plain "Rs.1495 credited … avail bal" still `isCredit == true`, `isFinance == true`
  (ensure B/A didn't regress normal credits).

## Out of scope

- No DB schema/migration changes (pure classification logic).
- Existing mislabeled rows are not retro-fixed by this change; the user can re-run
  Settings ▸ Categorization ▸ "Re-categorize all messages" if desired.
