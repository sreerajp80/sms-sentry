# Redo finance: correct amount extraction, de-duplication, and accurate ledger/UI

## The issue (reported)

1. Identical SMS delivered twice (same sender/body/timestamp) are stored twice and counted twice
   in the finance ledger. The existing duplicates must be removable, not just future ones blocked.
2. The Accounts/Finance overview shows absurd totals (Total Credit ≈ Rs 39,796,370, Total Debit
   ≈ Rs 9,120,902, Liquid Savings Rs 0.00) — wildly larger than reality.

## Root causes

### A. Amount extractor returns the *balance*, not the transaction amount
`SmsClassifier.runExtractions()` takes the **first** `Rs/INR/$` number in the body. For:

> "your passbook balance against KRKTM*****4015 is Rs. 13,97,889/-. Contribution of Rs. 18,894/-
> for due month May-26 has been received."

it picks the **balance (13,97,889)**, books it as a ~14-lakh **credit** (because "received" is
present), and ignores the real amount (18,894). A few such messages inflate totals into the
millions. `balRegex` also fails on this layout (masked account digits sit between "balance" and
the number), so `balance` stays 0.0 → "Liquid Savings" shows Rs 0.00.

### B. No de-duplication
`processAndInsertMessage()` always inserts; a duplicate broadcast/import creates a second
`SMSMessage` **and** a second `FinanceTx`. Existing duplicates are never cleaned.

### C. "This Month" totals are not filtered by month
`FinanceScreen` sums every transaction ever but labels it "(This Month)".

## Decisions (confirmed with user)
- Scope: **rebuild extraction + ledger + UI** (keep the Accounts card layout; fix the computations
  feeding it). No brand-new metrics.
- Existing data cleanup: **manual**, via Settings ▸ Categorization ▸ "Re-categorize all messages"
  — that action will now also de-duplicate messages and fully rebuild the ledger. Ingestion-time
  de-dup stays to prevent new duplicates.

## Files to change

### 1. `engine/SmsClassifier.kt` — rewrite financial extraction
Replace the ad-hoc amount/balance/credit logic in `runExtractions()` with a dedicated
`extractFinance(body)` helper:

- **Tokenize** every currency-tagged number with its position. Regex:
  `(?:rs\.?|inr|₹|\$|usd)\s*([\d,]+(?:\.\d{1,2})?)` (case-insensitive). Indian `/-` suffix is
  naturally tolerated (the trailing `/-` is outside the captured group). Commas stripped before
  parsing; handles both Indian (`13,97,889`) and Western grouping.
- **Balance** = the first currency number that appears *after* a balance keyword
  (`available bal(ance)`, `avail bal`, `a/c bal`, `closing bal`, `bal(ance)`), found by keyword
  position rather than an over-strict single regex (fixes the "balance …4015 is Rs. X" case).
- **Amount candidates** = all currency numbers except the one chosen as balance.
  - If a movement keyword is present (`credited`, `debited`, `spent`, `withdrawn`, `withdrew`,
    `paid`, `sent`, `received`, `contribution`, `transferred`, `purchase`), pick the candidate
    **nearest** that keyword's position; else the first candidate.
  - If the **only** currency figure is the balance (a pure "your balance is Rs X" info SMS),
    `amount = null` ⇒ **no `FinanceTx` is created**.
- **isCredit** decided from the movement keyword nearest the chosen amount
  (credited/received/refund/deposit/salary/contribution → credit; debited/spent/withdrawn/paid/
  sent/purchase → debit), defaulting to debit when ambiguous.
- Regression guard: existing seed messages must still extract correctly — e.g.
  "debited for Rs. 1,200 … Avail.Bal: Rs. 43,450" ⇒ amount 1,200 / balance 43,450;
  "Salary of Rs. 45,000 credited … Bal: Rs. 88,450" ⇒ amount 45,000 / balance 88,450 / credit.

### 2. `data/SmsDao.kt` — add de-dup support queries
- `@Query("SELECT id FROM messages WHERE sender = :sender AND body = :body AND timestamp = :timestamp ORDER BY id LIMIT 1") suspend fun findMessageId(sender, body, timestamp): Long?`
- Reuse existing `getAllMessagesOnce()`, `deleteMessageById(id)`, `deleteTransactionByMessageId(id)`,
  `deleteReminderByMessageId(id)` for the bulk cleanup (no new delete query needed).

### 3. `data/SmsRepository.kt`
- **Ingestion de-dup:** at the top of `processAndInsertMessage()`, if
  `findMessageId(sender, body, timestamp)` is non-null, return it without inserting a new message
  or supplemental rows. Sits in the single funnel ⇒ covers broadcast, deliver, import, P2P, seed.
- **Bulk cleanup + rebuild** in `recategorizeAllMessages()` (the Settings action), before the
  existing re-classify loop:
  1. Load all messages; group by `(sender, body, timestamp)`; for each group keep one survivor
     (prefer the row with a non-null `systemId`, else lowest `id`) and delete the rest along with
     their `FinanceTx`/`ReminderSms` rows.
  2. Then run the existing re-classify loop over the survivors, which already rebuilds `FinanceTx`
     from scratch using the corrected extractor.
  - Return value stays the count of messages processed (after de-dup).

### 4. `ui/SmsOrganizerUi.kt` — `FinanceScreen`
- Filter `totalCredits` / `totalDebits` to the **current calendar month** (match the
  "(This Month)" label) using a `Calendar`-derived month-start millis.
- **Estimated Liquid Savings** = most recent transaction *with a real balance*:
  `transactions.firstOrNull { it.balance > 0.0 }?.balance ?: 0.0` (transactions stream is already
  ordered by timestamp DESC), instead of the latest row's frequently-zero balance.
- **Accuracy disclaimer:** add a small, clearly-styled warning notice in the Accounts section
  (e.g. below the overview card) stating these figures are estimated from SMS text and may be
  incomplete or inaccurate — not a substitute for the bank's official statement. Uses a subdued/
  warning color with an info icon so it reads as a caveat, not an error.

## Notes / non-goals
- No DB schema change → no Room version bump. New DAO query is read-only; cleanup uses existing
  deletes.
- After deploying, the user taps Settings ▸ Categorization ▸ "Re-categorize all messages" once to
  de-dup existing rows and rebuild the ledger with correct amounts.
- Credit-vs-debit for unusual phrasings (e.g. chit-fund "contribution … received") follows the
  keyword convention above; the dominant bug being fixed is the amount, not direction nuance.

## Verification
- Manual extraction checks: passbook message ⇒ amount 18,894 / credit / balance 13,97,889; the two
  seed regression cases above; a pure "your balance is Rs 5,000" ⇒ no `FinanceTx`.
- Ingest the same SMS twice ⇒ one message + one `FinanceTx`.
- Seed a known duplicate, run "Re-categorize all messages" ⇒ duplicate message + its tx removed;
  totals reflect only the current month.
- Build per `docs/build-and-test.md` (no Gradle wrapper).
