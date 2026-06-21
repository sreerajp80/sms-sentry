# Change log: Finance contribution drill-down (auditable income/expense)

Implements [plans/20260620_203703_finance-contribution-drilldown.md](../plans/20260620_203703_finance-contribution-drilldown.md).

## Why

The finance card's "Total Credit / Total Debit (This Month)" figures are produced by offline
regex heuristics and were previously unverifiable by the user; tapping a ledger entry also did
nothing. This change lets the user tap a figure to see exactly which SMS-derived transactions were
summed into it, and tap any entry (in the breakdown or the main ledger) to open the source SMS and
confirm the parsed amount and credit/debit direction. View-only: no in-place correction of a
mis-parsed entry (scope decision recorded in the plan).

## Files changed

- **app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt**
  - Added `suspend fun getMessageById(id): SMSMessage?` passthrough to `smsDao.getMessageById`.

- **app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt**
  - Added top-level `enum class ContribKind { CREDIT, DEBIT }`.
  - Added overlay state `openedContribution: MutableState<ContribKind?>`.
  - Added `openContribution(kind)` / `closeContribution()`.
  - Added `openMessageById(messageId)` — loads the source SMS via the repository and reuses
    `openMessage` (so it also marks the message read, per scope decision); no-op if the SMS was
    deleted.

- **app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt**
  - Added private `monthStartMillis()` and `monthlyContributions(transactions)` — the single
    source of truth for the month-scoped credit/debit split, used by both the card and the
    breakdown so they can never disagree.
  - Refactored `FinanceScreen`'s `totalCredits`/`totalDebits` to derive from
    `monthlyContributions(...)` (removed the inline `monthStart`/`monthTransactions` calc).
  - Made the card's "Total Credit"/"Total Debit" columns clickable (open the breakdown) with a
    "Tap to verify ›" affordance.
  - Added `onClick: () -> Unit = {}` to `TransactionRowItem` and made its `Card` clickable;
    wired the main ledger list to `openMessageById(tx.messageId)` (fixes the "tapping a ledger
    entry does nothing" gap).
  - Added `ContributionBreakdownScreen(viewModel, kind)` — month-scoped, titled "… This Month",
    header total computed from `monthlyContributions`, list of `TransactionRowItem`s each opening
    the source SMS, with an empty state.
  - Rendered the breakdown overlay in the root composable **before** the message-detail block so
    SMS detail stacks above it and Back unwinds detail → breakdown → finance.

## Verification

- `./gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- Manual on-device verification (tap-through, total parity, back-navigation, deleted-SMS no-op)
  not yet performed.
