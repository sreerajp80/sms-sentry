# Plan: Tap income / expense → list of contributing SMS (auditable totals)

**Status:** completed

## What the user wants

On the Finance card, the "Total Credit (This Month)" and "Total Debit (This Month)"
figures are computed by offline regex heuristics and cannot currently be verified by
the user. The request: **tapping the income (credit) or expense (debit) figure opens a
page listing exactly the SMS that were summed into that total**, and from there the user
can tap any entry to open the original SMS to confirm it was parsed correctly
(amount + credit/debit direction).

This makes the headline numbers auditable and is also the natural fix for the earlier
observation that ledger rows are not tappable.

## The issue / current state

- Totals are computed in `FinanceScreen` ([SmsOrganizerUi.kt:3074-3076](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3074)):
  - `monthTransactions = transactions.filter { it.timestamp >= monthStart }`
  - `totalCredits = monthTransactions.filter { it.isCredit }.sumOf { it.amount }`
  - `totalDebits  = monthTransactions.filter { !it.isCredit }.sumOf { it.amount }`
- The credit/debit `Column`s on the card ([SmsOrganizerUi.kt:3118-3125](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3118)) are plain `Text`, not clickable.
- The ledger row `TransactionRowItem` ([SmsOrganizerUi.kt:3193](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3193)) is a plain `Card` with no `onClick`.
- Each `FinanceTx` already carries `messageId` ([SmsEntities.kt:55](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt#L55)) linking back to its source SMS.
- Infrastructure that already exists and will be reused:
  - `SmsDao.getMessageById(id: Long): SMSMessage?` ([SmsDao.kt:34](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt#L34)) — currently **not** exposed via repository/viewmodel.
  - Overlay navigation pattern: `viewModel.openedMessage` + `openMessage(msg)` / `closeMessage()` ([SmsOrganizerViewModel.kt:45,313-320](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt#L313)), rendered as a full-screen overlay in the root composable ([SmsOrganizerUi.kt:307-317](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L307)).
  - `MessageDetailScreen(viewModel, msg)` ([SmsOrganizerUi.kt:2555](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2555)) — the destination when an individual SMS is opened.

## Design decision

Reuse the existing **full-screen overlay** pattern (same as `openedThread` /
`openedMessage`) rather than introducing a NavHost. Add one new overlay:
`openedContribution` holding which side was tapped (`Credit` or `Debit`). The new
breakdown screen filters the already-loaded `transactions` for the current month +
direction, so totals shown on the breakdown screen are guaranteed to match the card
(same data, same filter).

Tapping a row in the breakdown screen (and, as a bonus, a row in the main ledger) loads
the source `SMSMessage` by `messageId` and opens the existing `MessageDetailScreen`.
Because `openedMessage` is rendered last in the root, it correctly stacks above the
breakdown overlay.

### Scope decisions (from critical review)

- **View-only v1.** The breakdown lets the user *verify* a figure by opening the source
  SMS, but does **not** offer correcting/excluding a mis-parsed entry. A wrong
  credit/debit direction or amount stays in the total; fixing it is explicitly out of
  scope for this change. (Recategorizing the message in `MessageDetailScreen` does not
  alter the `FinanceTx`.)
- **Auditing marks the SMS read.** `openMessageById` reuses the existing `openMessage`,
  which marks the message read. Accepted as-is — no read-preserving variant.

### Correctness guarantees (from critical review)

- **Single source of truth for "what counts."** The month filter + credit/debit split
  must not be duplicated. Extract one helper, e.g.
  `fun monthlyContributions(transactions): Pair<List<FinanceTx> /*credits*/, List<FinanceTx> /*debits*/>`
  (or two functions sharing one `monthStart`), used by **both** `FinanceScreen` (for the
  card totals) and `ContributionBreakdownScreen` (for the list + header total). This
  guarantees the breakdown total always equals the card figure.
- **Scope clarity.** The card and breakdown are month-scoped, but the main ledger list
  ([SmsOrganizerUi.kt:3184](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3184)) shows *all* transactions. This is intentional; the breakdown
  screen must be clearly titled "… (This Month)" so the shorter list isn't mistaken for
  a bug.
- **Overlay ordering.** The new `openedContribution` overlay must be rendered in the root
  composable **before** the `openedMessage` block ([SmsOrganizerUi.kt:308](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L308)) so SMS
  detail stacks above it and `BackHandler` precedence closes detail → breakdown →
  finance in that order.

## Files to be changed

1. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`**
   - Add a passthrough: `suspend fun getMessageById(id: Long): SMSMessage? = dao.getMessageById(id)`
     (verify exact dao field name used elsewhere in the file).

2. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`**
   - Add overlay state: `var openedContribution = mutableStateOf<ContribKind?>(null)`
     where `ContribKind` is a small enum `{ CREDIT, DEBIT }` (declared in the viewmodel or entities file).
   - Add `fun openContribution(kind: ContribKind)` / `fun closeContribution()`.
   - Add `fun openMessageById(id: Long)` that launches a coroutine, calls
     `repository.getMessageById(id)`, and if non-null calls `openMessage(msg)`
     (and shows a toast / no-op if the SMS was deleted). This reuses existing read-marking.

3. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
   - Add a shared helper (top-level/private) `monthlyContributions(transactions)` that
     returns the month-scoped credit list and debit list from one `monthStart`. Refactor
     `FinanceScreen`'s `totalCredits`/`totalDebits` ([3074-3076](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3074)) to derive
     from it so the card and breakdown can never disagree.
   - Make the two `Column`s on the finance card clickable
     ([3118-3125](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3118)):
     wrap each in `Modifier.clickable { viewModel.openContribution(CREDIT/DEBIT) }`,
     add a deliberate affordance (a chevron + "tap to verify" caption) so it's
     discoverable — a bare figure does not read as tappable.
   - Add a new composable `ContributionBreakdownScreen(viewModel, kind)`:
     - Use `monthlyContributions(...)` to get the matching list (no re-derivation of the
       filter rule).
     - Header titled "Income — This Month" / "Expenses — This Month" showing the count
       and the summed total (equals the card by construction).
     - A `LazyColumn` reusing `TransactionRowItem`, each tappable →
       `viewModel.openMessageById(tx.messageId)`.
     - Empty state when the list is empty.
   - Render the breakdown overlay in the root composable **before** the `openedMessage`
     block ([308-317](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L308)), with a `BackHandler { viewModel.closeContribution() }`, so SMS
     detail stacks above it and Back closes detail → breakdown → finance.
   - Add an `onClick` parameter to `TransactionRowItem` (default no-op to avoid
     touching other call sites) and pass `{ viewModel.openMessageById(tx.messageId) }`
     from both the main ledger list ([3184-3186](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3184)) and the new breakdown list, so ledger rows
     also become tappable (addresses the earlier "tapping does nothing" gap).

## Out of scope (not changing)

- The classification / parsing heuristics themselves (`SmsClassifier`) — this change is
  purely about surfacing what was parsed so the user can judge correctness.
- The "Estimated Liquid Savings" computation.
- No DB schema/migration changes (DAO method already exists).

## Verification

- Build the app (see docs/build-and-test.md).
- Tap "Total Credit" → breakdown lists only current-month credit SMS; the summed total
  in the header equals the card's credit figure. Same for debit.
- Tap a breakdown row → the original SMS opens in `MessageDetailScreen`; verify amount
  and direction against the raw text.
- Tap a main ledger row → same SMS detail opens.
- Back navigation returns breakdown → finance screen correctly.
- Deleted-source SMS case: tapping shows a graceful message, no crash.
