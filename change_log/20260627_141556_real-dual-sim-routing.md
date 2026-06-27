# Change log: Real dual-SIM routing

Implements [plans/20260627_124311_real-dual-sim-routing.md](../plans/20260627_124311_real-dual-sim-routing.md).

## Summary

The dual-SIM UI (composer picker, default-SIM setting, per-message "SIM X" labels) was
cosmetic — `simId` was a hardcoded 1/2 label, `SubscriptionManager` was never used, and every
send went out the system default SIM. This change wires the SIM selection to the telephony stack
while keeping `simId` as a durable 1-based slot index (no DB migration).

## Files changed

- **`app/src/main/AndroidManifest.xml`** — added `READ_PHONE_STATE` permission.
- **NEW `util/SimManager.kt`** — enumerates active subscriptions (`activeSubscriptions`), maps
  slot↔subscriptionId (`subscriptionIdForSlot` / `slotForSubscriptionId`), `isMultiSim`, and
  extracts the incoming subscription from a broadcast (`subscriptionIdFromIntent`). Degrades to
  "single default SIM" without the permission / telephony.
- **`util/SmsSender.kt`** — `dispatch(...)` now takes a `subscriptionId` (default
  `NO_SUBSCRIPTION`) and resolves the manager via the new public `smsManagerFor()`
  (`createForSubscriptionId` on API 31+, `getSmsManagerForSubscriptionId` below), falling back to
  the system default manager.
- **`ui/SmsOrganizerViewModel.kt`** — added `activeSims` StateFlow + `refreshActiveSims()`;
  persisted `defaultSmsSim` (so the headless service agrees); `sendSms`/`dispatchSms` resolve the
  chosen slot → subId; added `defaultOutgoingSlot()` and `resendMessage()`.
- **`receiver/ScheduledSmsReceiver.kt`** — resolves `row.simId` (slot) → subId at fire time.
- **`receiver/HeadlessSmsSendService.kt`** — sends on the persisted default SIM via
  `SmsSender.smsManagerFor` instead of `SmsManager.getDefault()`.
- **`receiver/SmsReceiver.kt`** & **`receiver/SmsDeliverReceiver.kt`** — record the real
  originating SIM (`SimManager.slotForSubscriptionId(subscriptionIdFromIntent(...))`) instead of
  the hardcoded `simId = 1`.
- **`ui/SmsOrganizerUi.kt`** — composer SIM badge now cycles real active subscriptions and hides
  on single-SIM; reply row sends on `defaultOutgoingSlot()`; the "Multi-SIM Preferences" setting
  lists real subscriptions (carrier labels) and is hidden on single-SIM; `selectedSimId` is
  coerced to a present slot. Startup + `onResume` request `READ_PHONE_STATE` and call
  `refreshActiveSims()`.
- **`MainActivity.kt`** — `onResume` refreshes active SIMs.

## Notes / scope

- `simId` remains a 1-based slot index; no Room migration. CSV/JSON/P2P payloads keep their
  literal slot labels. Simulate/test and import paths keep their literal `simId` values.
- Per-SIM MMS sending and dual-SIM data/roaming remain out of scope.

## Verification

- `./gradlew :app:compileDebugKotlin` and `:app:assembleDebug` — BUILD SUCCESSFUL (only
  pre-existing deprecation warnings).
- Unit tests not run: the test module has a **pre-existing** compile error in
  `ExampleRobolectricTest.kt` (`assertDoesNotExist` import, already modified in the working tree
  before this change); no test files were touched here.
