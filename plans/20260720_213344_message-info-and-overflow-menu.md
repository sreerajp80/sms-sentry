# Plan: Selection overflow menu + "Message info" sheet

**Status:** completed

## What the user wants

In the **thread screen selection bar** (the toolbar that appears when you long-press a
message bubble — the one in the screenshot with mark-read / mark-unread / copy / share /
delete icons):

1. Add a **dotted overflow menu** (⋮, `MoreVert`).
2. **Move "Share"** out of the toolbar row and into that overflow menu.
3. Add a new **"Message info"** item to the overflow menu.
4. "Message info" is **enabled only when exactly one message is selected**; it is shown
   **disabled** (greyed out) when 0 or 2+ messages are selected.

"Message info" opens a dialog showing these fields for the selected message:

- **Address / sender** — number or shortcode, resolved to contact name + photo if matched
- **Date received** (`date`) — when the device got it
- **Date sent** (`date_sent`) — when the sender's network stamped it
- **Read** — whether it's been opened
- **Seen** — whether it appeared in a notification
- **Thread ID** — which conversation it belongs to
- **Status** — none / complete / pending / failed
- **Subscription ID (SIM)** — which SIM received/sent it
- **Service center** (`service_center`) — the SMSC number that routed it
- **Protocol** — SMS protocol identifier
- **Priority / error code**

## The issue / gap

The current `SMSMessage` entity ([SmsEntities.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt))
only stores **some** of these fields:

| Field the user asked for | Where it is today |
|---|---|
| Address / sender | stored (`sender`) |
| Date received | stored (`timestamp`) |
| Read | stored (`isRead`) |
| Thread ID | stored (`threadId`) |
| Status | stored (`status`, our own SENDING/SENT/DELIVERED/FAILED codes) |
| Subscription (SIM) | stored (`simId`) |
| **Date sent** | **not stored** |
| **Seen** | **not stored** |
| **Service center** | **not stored** |
| **Protocol** | **not stored** |
| **Priority / error code** | **not stored** |

The five missing fields live only in the **system SMS provider** (`content://sms`). We can
read them on demand from the provider using the message's `systemId`, but this only works
when:

- the `READ_SMS` permission is granted, **and**
- the message actually has a `systemId` (real SMS synced from the device). Demo-seed,
  simulated, imported (CSV/P2P) messages have `systemId == null` and therefore have **no**
  provider row — for those we will show the fields we do have and mark the rest as
  "Not available".

So we will **not** add columns to the database. Instead we read the extra fields live from
the provider when the dialog opens. This keeps the schema stable (no Room migration).

## Files to be changed

1. **`app/src/main/java/in/sreerajp/sms_sentry/data/SystemSmsStore.kt`**
   - Add a `SystemSmsDetails` data class holding the provider-only fields:
     `dateSent: Long?`, `seen: Boolean?`, `serviceCenter: String?`, `protocol: Int?`,
     `status: Int?` (provider status: -1 none / 0 complete / 32 pending / 64 failed),
     `errorCode: Int?`, `subscriptionId: Int?`, `read: Boolean?`, `threadId: Long?`,
     `dateReceived: Long?`.
   - Add `fun readDetails(systemId: Long): SystemSmsDetails?` that queries the single row
     `content://sms/<id>` with an extended projection (`DATE_SENT`, `SEEN`,
     `SERVICE_CENTER`, `PROTOCOL`, `STATUS`, `ERROR_CODE`, `SUBSCRIPTION_ID`, `READ`,
     `THREAD_ID`, `DATE`). Wrapped in the same try/catch as `readAll()`, returns `null` on
     `SecurityException` (no `READ_SMS`) or any error. Use `getColumnIndex` (not
     `...OrThrow`) for optional columns and null-guard each, since some are absent on older
     providers.

2. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`**
   - Add a suspend/callback function, e.g.
     `fun loadMessageDetails(msg: SMSMessage, onLoaded: (MessageInfo) -> Unit)` that:
     - builds a `MessageInfo` UI model from the entity fields we already have, then
     - if `msg.systemId != null` and `READ_SMS` is granted, enriches it off the main thread
       (`viewModelScope.launch` → provider read) with `SystemSmsStore.readDetails()`.
   - Define a small `MessageInfo` data/UI model (either in the ViewModel file or alongside
     the dialog) carrying the display-ready values (nullable where "Not available").

3. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
   - **ThreadScreen selection header** (around
     [SmsOrganizerUi.kt:2079-2210](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2079)):
     - Remove the standalone **Share** `TooltipIconButton`.
     - Add a `Box { TooltipIconButton(MoreVert) + DropdownMenu }` overflow at the end of the
       row (after Delete), mirroring the existing non-selection overflow pattern already in
       this file.
     - Overflow items:
       - **Share** — same behaviour as the current Share button (join selected bodies in
         on-screen order, `shareText`, then `clearSelection()`).
       - **Message info** — `enabled = selectedIds.size == 1`. On click, resolve the single
         selected `SMSMessage` from `threadMessages`, call
         `viewModel.loadMessageDetails(...)`, and open the info dialog. Disabled item shows
         greyed text/icon (Compose `DropdownMenuItem(enabled = false)` handles the styling).
   - Add a new `@Composable MessageInfoDialog(info: MessageInfo, onDismiss: () -> Unit)`:
     - An `AlertDialog` (or `Dialog`) titled "Message info".
     - Header row: avatar (`AvatarTile` + `photoUriFor`) + resolved name (`displayNameFor`)
       + raw number underneath.
     - A vertical list of label/value rows for every field. Format:
       - dates via `SimpleDateFormat("dd MMM yyyy · hh:mm:ss a")`,
       - Read / Seen as "Yes" / "No",
       - Status mapped to a readable label (None / Complete / Pending / Failed),
       - SIM shown as the subscription/SIM label when resolvable, else `simId`,
       - any missing/unavailable value shown as "Not available".
     - A single "Close" (dismiss) button. Add a `testTag("message_info_dialog")` and
       per-row test tags for the important fields.
   - Wire dialog visibility state (`var infoMessage by remember { mutableStateOf<MessageInfo?>(null) }`
     or a boolean + held model) near the other ThreadScreen dialog state
     (around [SmsOrganizerUi.kt:2045-2047](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2045)).

## Scope notes / decisions

- **Only the thread screen** selection bar gets this change. The **inbox** selection bar is
  sender-level (whole conversations), not single-message, so "Message info" does not apply
  there and is out of scope.
- **No database migration** — extra fields are read live from the provider, not persisted.
- **Graceful degradation** — without `READ_SMS`, or for messages with no `systemId`, the
  provider-only fields display "Not available"; the entity-backed fields still show.
- **Copy** and the other toolbar icons are unchanged.

## Testing

- Add a Robolectric unit test (in the style of `CopyMessageTest.kt`) covering
  `SystemSmsStore.readDetails()` against a Robolectric-backed SMS provider row (verify the
  provider columns are read and mapped), and the `MessageInfo` mapping for a message with a
  null `systemId` (all provider fields "Not available").
- Manual check: build/install per [docs/build-and-test.md](../docs/build-and-test.md), open a
  thread, long-press one message → overflow shows **Share** + **Message info** (enabled);
  select a second message → **Message info** greyed; open the dialog on a single real SMS and
  confirm the fields populate.

## After implementation

Write a change log to `change_log/` referencing this plan.
