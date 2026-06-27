# Real dual-SIM routing (wire the SIM picker to the radio)

**Status:** completed

## What the issue is

The app already has the dual-SIM **UI surface** — a SIM picker in the composer, a
"Choose default outgoing SIM" setting, a per-message `simId` field, and "SIM X" badges
in the list and notifications. But none of it is connected to telephony:

- **`SubscriptionManager` is never referenced anywhere in the codebase.** `simId` is a
  hardcoded 1/2 label, not a real `subscriptionId`. The picker offers a fixed
  `["SIM 1", "SIM 2", "Ask Every Time"]` list — it shows two SIMs even on a single-SIM
  phone, has no carrier name / number, and "1/2" maps to nothing the radio understands.
- **Sending ignores the chosen SIM.** [SmsSender.dispatch()](../app/src/main/java/in/sreerajp/sms_sentry/util/SmsSender.kt#L25)
  takes no SIM argument and uses `getSystemService(SmsManager::class.java)` /
  `SmsManager.getDefault()` — always the **system default subscription**. Picking SIM 2
  in the composer still sends from the default SIM; the choice is only written to the Room
  row as a cosmetic label.
- **Quick-reply / headless send** ([HeadlessSmsSendService.kt:28](../app/src/main/java/in/sreerajp/sms_sentry/receiver/HeadlessSmsSendService.kt#L28))
  also uses `SmsManager.getDefault()`.
- **Incoming SIM is hardcoded to 1.** [SmsReceiver.kt:34](../app/src/main/java/in/sreerajp/sms_sentry/receiver/SmsReceiver.kt#L34)
  and [SmsDeliverReceiver.kt:41](../app/src/main/java/in/sreerajp/sms_sentry/receiver/SmsDeliverReceiver.kt#L41)
  both set `val simId = 1` and never read the real subscription off the incoming intent.
  Every received message is labeled "SIM 1" regardless of which SIM it arrived on.

## Design decisions

- **Keep `simId` = 1-based SIM slot index** (not the raw `subscriptionId`). This preserves
  existing Room data, CSV/JSON export/import, P2P sync payloads, and the "SIM X" UI labels,
  and **avoids a DB migration**. The slot↔subscriptionId mapping is resolved on demand.
- **Resolve slot → subscriptionId at the moment of use** (send time / scheduled fire time),
  not at storage time. Subscription IDs are not stable across reboots / SIM swaps; the slot
  index is the durable identifier, so we map slot→subId right before `createForSubscriptionId`.
- **Single-SIM fallback:** when ≤1 active subscription, hide the composer SIM picker and the
  multi-SIM setting; send with subId `-1` (meaning "use default", i.e. unchanged behavior).
- **Permission:** `getActiveSubscriptionInfoList()` requires `READ_PHONE_STATE`. Add it and
  request it at runtime; degrade gracefully to single-SIM/default behavior if denied.

## Files to change

1. **`app/src/main/AndroidManifest.xml`** — add `android.permission.READ_PHONE_STATE`.
2. **NEW `app/src/main/java/in/sreerajp/sms_sentry/util/SimManager.kt`** — central SIM helper:
   - `activeSubscriptions(context): List<SimInfo>` where `SimInfo(slot: Int /*1-based*/,
     subscriptionId: Int, displayLabel: String /*carrier or "SIM N"*/, number: String?)`,
     backed by `SubscriptionManager.getActiveSubscriptionInfoList()`. Returns empty on
     missing permission / no telephony.
   - `subscriptionIdForSlot(context, slot: Int): Int` → real subId, or `-1` if unknown
     (caller then uses the default `SmsManager`).
   - `slotForSubscriptionId(context, subId: Int): Int` → 1-based slot for incoming mapping
     (defaults to 1 if not resolvable).
   - `isMultiSim(context): Boolean` and `hasPhoneStatePermission(context): Boolean`.
3. **`util/SmsSender.kt`** — `dispatch(context, recipient, body, msgId, subscriptionId: Int)`.
   When `subscriptionId >= 0` use `SmsManager.getSmsManagerForSubscriptionId(subscriptionId)`
   (API 31+: `getSystemService(SmsManager::class.java).createForSubscriptionId(subId)`);
   else fall back to the current default-manager path. Multipart logic unchanged.
4. **`ui/SmsOrganizerViewModel.kt`**:
   - Expose `activeSims: StateFlow<List<SimInfo>>` (refreshed alongside `refreshDefaultStatus`).
   - `sendSms(recipient, body, slot)` and `dispatchSms(...)`: resolve `slot → subId` via
     `SimManager` and pass it to `SmsSender.dispatch`. Still store `simId = slot` in Room.
   - `scheduleSms` keeps storing `slot`; resolution happens at fire time (see #5).
   - Add `refreshActiveSims()` + a `READ_PHONE_STATE`-granted hook (mirrors existing
     contacts/default-app permission refresh pattern).
5. **`receiver/ScheduledSmsReceiver.kt`** — resolve `row.simId (slot) → subId` via `SimManager`
   at fire time and pass to `SmsSender.dispatch`.
6. **`receiver/HeadlessSmsSendService.kt`** — send via the user's default outgoing SIM
   (default-SIM setting → slot → subId; `-1` falls back to system default).
7. **`receiver/SmsReceiver.kt`** & **`receiver/SmsDeliverReceiver.kt`** — read the real
   subscription from the intent (`intent.getIntExtra("subscription", -1)`, fallback
   `SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX`) and map via
   `SimManager.slotForSubscriptionId` instead of the hardcoded `val simId = 1`.
8. **`ui/SmsOrganizerUi.kt`**:
   - Composer SIM badge (~L5813): cycle through **real** active subscriptions and label with
     carrier name; hide the badge entirely when single-SIM. `selectedSimId` stays a slot int.
   - Default-SIM setting (~L4685): replace the fixed `["SIM 1","SIM 2","Ask Every Time"]`
     with real subscriptions (carrier labels) + "Ask Every Time"; hide the whole card when
     single-SIM. Initialize composer's `selectedSimId` from the default-SIM setting.
   - Reply path at L2225 (`sendSms(sender, replyText, 1)`): use the default outgoing slot
     instead of the hardcoded `1`.
9. **`MainActivity.kt`** — include `READ_PHONE_STATE` in the runtime permission request flow
   and trigger `viewModel.refreshActiveSims()` on grant.

## Out of scope (note, don't implement now)

- No DB migration (intentional — `simId` stays a slot index).
- Per-SIM MMS sending, dual-SIM data/roaming, and per-subscription send for the simulate/test
  and CSV/JSON/P2P import paths (those keep their literal `simId` values).
- The `SmsShareUtils` / `P2PSyncEngine` `simId` fields stay as-is (slot labels).

## Verification

- Single-SIM device/emulator: picker + multi-SIM setting hidden; send still works (subId −1).
- Dual-SIM: composer shows both carriers; sending from each routes via the matching
  `subscriptionId`; received messages show the correct originating SIM slot.
- Permission denied: app behaves exactly as today (single default SIM), no crash.
- Existing Roborazzi/Robolectric tests still pass (mock `SimManager` to empty → single-SIM UI).
