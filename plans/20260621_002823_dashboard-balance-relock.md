# Plan: Allow re-locking the balance display from the Dashboard

**Status:** completed

## Issue

On the Dashboard, the "Available Balance" card can be **unlocked** (via device
authentication) to reveal the parsed balance, but there is **no way to re-lock it
again from the Dashboard**.

- The lock/unlock state is `SmsOrganizerViewModel.isFinanceAuthenticated`
  (`true` = revealed). Setting it back to `false` re-hides the balance.
- On the Dashboard balance card (`DashboardScreen`, `SmsOrganizerUi.kt` ~lines
  441–497), the only click handler is on the whole `Card`, and it only either
  triggers authentication (when hidden) or `onNavigate("Accounts")`. The
  "Visible / Locked" status pill (the `Surface` at ~lines 474–497) is purely
  decorative.
- The only existing relock affordance is the "Lock Now" button inside Settings
  (~line 4642). A user who unlocked from the Dashboard has no obvious way to
  relock there.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
  - `DashboardScreen` — the "AVAILABLE BALANCE" card, specifically the
    status-pill `Surface` (~lines 474–497).

(No ViewModel changes needed — `isFinanceAuthenticated` already exists and is the
single source of truth.)

## Fix

Make the status pill on the balance card interactive so it acts as a re-lock
control when the balance is currently visible:

1. Add a `clickable` modifier to the pill `Surface` that is active only when the
   finance section is **not hidden** (i.e. unlocked: `isSecured && isAuthenticated`).
   When tapped in that state it sets `viewModel.isFinanceAuthenticated.value = false`,
   re-hiding the balance, and shows a short Toast (e.g. "Balance locked").
2. When the balance is hidden (locked), leave the pill non-interactive so the
   existing whole-card click continues to drive authentication — i.e. the pill's
   `clickable` is only attached when unlocked. This avoids double-handling the tap.
3. Because the pill sits inside the clickable `Card`, ensure tapping the pill to
   relock does **not** also fire the card's `onNavigate("Accounts")`. The pill's
   own `clickable` consumes the press, so the card click won't run for taps that
   land on the pill.
4. Keep the visual exactly as-is otherwise (LockOpen + "Visible" when unlocked,
   Lock + "Locked" when hidden); only behavior is added. Optionally add a
   `testTag("dashboard_lock_pill")` for testability.

### Behavior after fix

- Locked  → tap card → authenticate → unlocked (unchanged).
- Unlocked → tap the "Visible" pill → balance re-locks immediately (new).
- Unlocked → tap elsewhere on the card → navigate to Accounts (unchanged).

## Notes / risks

- This is a small, localized UI change; no data or persistence implications
  (`isFinanceAuthenticated` is in-memory session state, same as today).
- The Settings "Lock Now" path is left untouched.
