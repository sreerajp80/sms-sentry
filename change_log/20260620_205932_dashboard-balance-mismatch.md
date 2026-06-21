# Dashboard "Available Balance" vs Accounts "Liquid Savings" mismatch

Implements plan `plans/20260620_204909_dashboard-balance-mismatch.md`.

## Problem

The Dashboard "AVAILABLE BALANCE" card showed `₹ 0.00` while the Accounts "ESTIMATED LIQUID
SAVINGS" card showed the real `Rs. 85,175.00`, even though both claim to display the same
"latest parsed balance" and read the same `viewModel.transactions` flow (DB-ordered
newest-first). The cards had drifted to different selection predicates:

- Dashboard: `transactions.firstOrNull()?.balance` — newest row, whose balance is frequently
  `0.0` (unparsed), so the card collapsed to 0.00.
- Accounts: `transactions.firstOrNull { it.balance > 0.0 }?.balance` — most recent row that
  actually carried a balance.

## Changes

- **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
  - Added `internal fun latestParsedBalance(transactions): Double` next to
    `monthlyContributions` as the single source of truth for the figure. Declared `internal`
    (not `private`) so it is reachable from the unit-test module. Doc comment records the
    KNOWN LIMITATION that `balance` is a non-nullable Double defaulting to 0.0, so the
    predicate cannot distinguish "unparsed" from a genuine 0.0 and skips negative (overdraft)
    balances.
  - Dashboard card (was line 369) now calls `latestParsedBalance(transactions)`.
  - Accounts card (was lines 3104-3106) now calls `latestParsedBalance(transactions)`,
    replacing the duplicated inline logic + comment.

- **`app/src/test/java/in/sreerajp/sms_sentry/LatestParsedBalanceTest.kt`** (new)
  - Four JUnit cases guarding against re-drift: skips a newest zero-balance row and returns
    the most recent real balance; returns the first balance when the newest row already has
    one; returns 0.0 when no row carries a balance; returns 0.0 for an empty list.

## Verification

- `./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.LatestParsedBalanceTest"`
  → BUILD SUCCESSFUL, all 4 tests pass.
- Both cards now derive the figure from one function and will display the same value.

## Out of scope (carried over from the plan)

- Nullable `balance` column + migration to correctly handle genuine-zero / negative balances.
- "Latest parsed balance" staleness semantics (the change makes the cards consistent, not
  authoritative; the existing on-screen "estimate" disclaimer still applies).
- The separately-suspicious inflated "Total Credit (This Month)" figure.
