# Dashboard "Available Balance" shows ₹ 0.00 while Accounts shows the real balance

**Status:** completed

## Issue

The Dashboard "AVAILABLE BALANCE" card and the Accounts "ESTIMATED LIQUID SAVINGS" card
both claim to show the **latest parsed balance**, but they disagree:

- Dashboard: `₹ 0.00`
- Accounts: `Rs. 85,175.00`

### Verified root cause

Both cards read the **same data source** — `viewModel.transactions.collectAsState()`
(dashboard [SmsOrganizerUi.kt:359](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L359),
FinanceScreen [SmsOrganizerUi.kt:3097](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3097)) —
and that flow is ordered **newest-first** at the DB layer
(`SELECT * FROM finance_transactions ORDER BY timestamp DESC`,
[SmsDao.kt:98](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt#L98)).

So the data and the ordering are identical. The mismatch is **purely a difference in the
selection predicate** — the two surfaces hand-rolled `activeBalance` differently and drifted:

- Dashboard ([line 369](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L369)):
  ```kotlin
  val activeBalance = transactions.firstOrNull()?.balance ?: 0.0
  ```
  Takes the **very newest** transaction's balance. The latest message frequently carries
  **no balance** (`balance == 0.0`), so the card collapses to ₹ 0.00.

- Accounts ([line 3106](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3106)):
  ```kotlin
  val activeBalance = transactions.firstOrNull { it.balance > 0.0 }?.balance ?: 0.0
  ```
  Takes the most recent transaction that **actually carried a balance** — the fix from
  `change_log/20260618_192143_finance-calc-duplicates-and-amount.md`. The Accounts screen
  got that fix; the Dashboard card was left on the old logic.

## Why a shared helper (not a one-line copy)

The original draft of this plan proposed copying the `{ it.balance > 0.0 }` predicate into
the dashboard plus a "keep in sync" comment. That is rejected: a comment is exactly the
(absent) protection that let these two copies drift in the first place. There are now **six**
`viewModel.transactions.collectAsState()` call sites and two hand-rolled balance formulas.

The codebase already established the correct pattern for this class of bug: `monthlyContributions()`
is documented as the "single source of truth ... so the breakdown list can never silently
disagree with the headline figure"
([SmsOrganizerUi.kt:3077-3086](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3077-L3086)).
We follow that precedent: extract one helper and have both cards call it.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — add one helper; update
  two call sites (lines 369 and 3106).
- `app/src/test/java/in/sreerajp/sms_sentry/LatestParsedBalanceTest.kt` — **new** unit test.

## Plan for the fix

1. Add a top-level helper next to `monthlyContributions` (around line 3086). It is declared
   `internal` (NOT `private`): a `private` top-level function is file-scoped and cannot be
   reached from the test module, so `internal` is required to make it unit-testable.

   ```kotlin
   /**
    * Single source of truth for the "latest parsed balance" shown on both the Dashboard
    * "Available Balance" card and the Accounts "Estimated Liquid Savings" card.
    *
    * The transaction stream is newest-first, but the most recent row frequently carries no
    * balance (balance == 0.0 when the SMS had no balance to parse), so we return the most
    * recent transaction that actually carried one.
    *
    * KNOWN LIMITATION: `balance` is a non-nullable Double defaulting to 0.0, so this cannot
    * distinguish "no balance parsed" from a genuine 0.0 balance, and it skips negative
    * (overdraft) balances. A genuinely-zero or negative current balance will therefore show
    * an older positive figure. Fixing that properly requires a nullable `balance` column +
    * migration and is deliberately out of scope here (see "Out of scope").
    */
   internal fun latestParsedBalance(transactions: List<FinanceTx>): Double =
       transactions.firstOrNull { it.balance > 0.0 }?.balance ?: 0.0
   ```

2. Dashboard ([line 369](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L369)):
   ```kotlin
   val activeBalance = latestParsedBalance(transactions)
   ```

3. Accounts ([line 3106](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3106)):
   ```kotlin
   val activeBalance = latestParsedBalance(transactions)
   ```
   (Drop the now-redundant inline comment at lines 3104-3105; the helper documents it.)

After this both cards derive the figure from one function and will read Rs. 85,175.00.

## Out of scope (named explicitly, not silently inherited)

- **`balance > 0.0` correctness.** The predicate conflates unparsed / genuine-zero / negative
  balances. The preserved behavior is the existing Accounts behavior; the real fix is a
  nullable `balance: Double?` + "latest non-null", a schema/migration change. Documented in
  the helper's KNOWN LIMITATION block so it is not forgotten.
- **Staleness semantics.** "Latest parsed balance" can be arbitrarily old relative to the
  newest activity. This change makes the two cards *consistent*; it does not make the figure
  *authoritative*. The "estimate" disclaimer already on the Accounts screen covers this.
- The separately-suspicious inflated "Total Credit (This Month)" figure — a different issue.

4. Add the unit test `app/src/test/java/in/sreerajp/sms_sentry/LatestParsedBalanceTest.kt`
   (plain JUnit, matching the style of `SmsClassifierTest`). `FinanceTx` is a public data
   class; `balance` is the field under test. Newest-first means index 0 is the most recent.

   ```kotlin
   package `in`.sreerajp.sms_sentry

   import `in`.sreerajp.sms_sentry.data.FinanceTx
   import `in`.sreerajp.sms_sentry.ui.latestParsedBalance
   import org.junit.Assert.assertEquals
   import org.junit.Test

   /**
    * Guards the Dashboard/Accounts "latest parsed balance" against the re-drift that let the
    * two cards disagree (dashboard showed 0.00 while accounts showed the real balance).
    */
   class LatestParsedBalanceTest {

       private fun tx(balance: Double, timestamp: Long) =
           FinanceTx(messageId = 0, bankName = "TEST", amount = 0.0,
               isCredit = true, balance = balance, timestamp = timestamp)

       @Test
       fun `skips newest zero-balance row and returns most recent real balance`() {
           // newest-first: a 0.0 (unparsed) row on top, real balance just below it
           val txns = listOf(tx(0.0, 300), tx(85175.0, 200), tx(50000.0, 100))
           assertEquals(85175.0, latestParsedBalance(txns), 0.0)
       }

       @Test
       fun `returns first balance when newest row already carries one`() {
           val txns = listOf(tx(85175.0, 300), tx(50000.0, 200))
           assertEquals(85175.0, latestParsedBalance(txns), 0.0)
       }

       @Test
       fun `returns zero when no row carries a balance`() {
           val txns = listOf(tx(0.0, 200), tx(0.0, 100))
           assertEquals(0.0, latestParsedBalance(txns), 0.0)
       }

       @Test
       fun `returns zero for empty list`() {
           assertEquals(0.0, latestParsedBalance(emptyList()), 0.0)
       }
   }
   ```

## Verification

- Build the app and run unit tests (`docs/build-and-test.md`); `LatestParsedBalanceTest`
  must pass.
- Manual: Dashboard "AVAILABLE BALANCE" and Accounts "ESTIMATED LIQUID SAVINGS" must show the
  same value (Rs. 85,175.00 with current data).
