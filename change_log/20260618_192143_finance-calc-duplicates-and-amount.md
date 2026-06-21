# Redo finance: correct amount extraction, de-duplication, accurate ledger/UI

Implements plan [plans/20260618_192143_finance-calc-duplicates-and-amount.md](../plans/20260618_192143_finance-calc-duplicates-and-amount.md).

## Problem
- Identical SMS (same sender/body/timestamp) were stored and counted twice in the ledger.
- Accounts overview showed absurd totals (Credit ≈ Rs 39,796,370 / Debit ≈ Rs 9,120,902 /
  Liquid Savings Rs 0.00) because the amount extractor grabbed the **balance** figure (e.g. a
  passbook balance of Rs 13,97,889) as the transaction amount, "(This Month)" totals summed all
  history, and the savings figure read the latest row's frequently-zero balance.

## Changes

### `engine/SmsClassifier.kt` — rewrote financial extraction
- Replaced `amtRegex`/`balRegex`/the old movement regex with: `currencyRegex` (matches
  rs/inr/₹/$/usd amounts, tolerates Indian `/-` and `13,97,889` grouping), `balanceKeywordRegex`,
  an expanded `movementRegex`, and a `creditWords` set.
- New `extractFinanceFields(body)` helper:
  - Tokenizes every currency figure with its position.
  - Balance = first currency figure following a balance keyword (handles "balance …4015 is Rs X").
  - Amount = the remaining currency figure nearest a money-movement verb (never the balance);
    if the only figure present is the balance, amount is null ⇒ **no FinanceTx is created**.
  - Direction taken from the movement verb nearest the chosen amount (credit set vs. debit default).
- Traced regression cases hold: "debited for Rs 1,200 … Avail.Bal: Rs 43,450" ⇒ 1,200/43,450;
  "Salary of Rs 45,000 credited … Bal: Rs 88,450" ⇒ 45,000 credit/88,450; passbook/contribution
  message ⇒ 18,894 credit / 13,97,889 balance (previously 13,97,889 as the amount).

### `data/SmsDao.kt`
- Added `findMessageId(sender, body, timestamp)` for de-dup lookups.

### `data/SmsRepository.kt`
- `processAndInsertMessage()`: returns the existing id without inserting when an identical message
  already exists (de-dup in the single ingestion funnel — covers broadcast/deliver/import/P2P/seed).
- `recategorizeAllMessages()`: added a de-dup pass that keeps one survivor per
  (sender, body, timestamp) group (prefers a `systemId`-backed row, else lowest id) and deletes the
  rest plus their finance/reminder rows, before the existing re-classify/rebuild loop. This is how
  pre-existing duplicates get cleaned up — via Settings ▸ "Re-categorize all messages".

### `ui/SmsOrganizerUi.kt` — `FinanceScreen`
- `totalCredits`/`totalDebits` now filter to the current calendar month (matches the labels).
- "Estimated Liquid Savings" uses the most recent transaction with a balance > 0.
- Added an accuracy disclaimer notice under the overview card: figures are estimated from SMS and
  may be incomplete/inaccurate; verify with the bank's official statement.

## Notes
- No DB schema change → no Room version bump.
- User must tap Settings ▸ Categorization ▸ "Re-categorize all messages" once to de-dup existing
  rows and rebuild the ledger with corrected amounts.
- Verified with `./gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL (only pre-existing
  deprecation warnings).
