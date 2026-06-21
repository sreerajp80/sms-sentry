# Change log: Allow re-locking the balance display from the Dashboard

Implements plan `plans/20260621_002823_dashboard-balance-relock.md`.

## What changed

`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — `DashboardScreen`,
the "AVAILABLE BALANCE" card status pill (~lines 474–497):

- The previously decorative "Visible / Locked" pill `Surface` is now interactive
  **when the balance is unlocked** (`isSecured && !isFinanceHidden`, captured in a
  new local `canRelock`).
- When `canRelock` is true, the pill gets a `clickable` modifier that sets
  `viewModel.isFinanceAuthenticated.value = false` (re-hiding the balance) and shows
  a "Balance locked" Toast, plus a `testTag("dashboard_lock_pill")`.
- When the balance is hidden/locked (or security is disabled), the pill carries a
  plain `Modifier` and stays non-interactive, so the existing whole-card
  authentication / navigation behavior is unchanged. The pill's own click consumes
  the tap, so re-locking does not also trigger `onNavigate("Accounts")`.

## Not changed

- No `SmsOrganizerViewModel` changes — `isFinanceAuthenticated` already existed and
  remains the single source of truth (in-memory session state).
- The Settings "Lock Now" relock path is untouched.
- Visuals are unchanged (LockOpen + "Visible" when unlocked, Lock + "Locked" when
  hidden); only behavior was added.

## Behavior after change

- Locked  → tap card → authenticate → unlocked (unchanged).
- Unlocked → tap the "Visible" pill → balance re-locks immediately (new).
- Unlocked → tap elsewhere on the card → navigate to Accounts (unchanged).
