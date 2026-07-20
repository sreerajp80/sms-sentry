# Change log: Selection overflow menu + "Message info" sheet

Implements [plans/20260720_213344_message-info-and-overflow-menu.md](../plans/20260720_213344_message-info-and-overflow-menu.md).

## What changed

In the **thread screen selection bar** (the toolbar shown when messages are long-pressed):

- The standalone **Share** icon button was removed and replaced with a **⋮ overflow menu**
  (`MoreVert`) placed after the Delete icon.
- The overflow menu holds two items:
  - **Share** — same behaviour as before (joins the selected message bodies in on-screen
    order and opens the system share sheet).
  - **Message info** — enabled **only when exactly one message is selected**; greyed out
    otherwise. Opens a new details dialog for that message.

The new **"Message info"** dialog shows:

- Address / sender (with resolved contact name + photo when matched)
- Date received, Date sent
- Read, Seen
- Thread ID
- Status (delivery report)
- Subscription (SIM)
- Service center, Protocol, Priority / error code

Fields that live only in the system SMS provider (date sent, seen, service center, protocol,
error code, subscription id) are read **live** from `content://sms` when the dialog opens.
When the message has no system row (demo seed, simulated, CSV/P2P import) or `READ_SMS` is
not granted, those fields show **"Not available"** and a short explanatory note appears. No
database schema change / Room migration was needed.

## Files changed

- **`app/src/main/java/in/sreerajp/sms_sentry/data/SystemSmsStore.kt`**
  - Added `SystemSmsDetails` data class and `readDetails(systemId)`, which reads the extended
    provider columns (`DATE_SENT`, `SEEN`, `SERVICE_CENTER`, `PROTOCOL`, `STATUS`,
    `ERROR_CODE`, `SUBSCRIPTION_ID`, `READ`, `THREAD_ID`, `DATE`) for a single row using
    non-throwing column lookups. Returns `null` on missing row, `SecurityException`, or error.

- **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`**
  - Added the `MessageInfo` UI model.
  - Added `loadMessageDetails(msg, onLoaded)` which builds the model from entity fields and
    enriches it off the main thread from the provider (when `systemId != null` and `READ_SMS`
    granted). Added `simLabelFor`, `ownStatusLabel`, and `providerStatusLabel` helpers.

- **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
  - Reworked the thread selection header: removed the Share icon button, added the overflow
    menu with Share + Message info (info disabled unless one message is selected).
  - Added the `MessageInfoDialog` composable and the private `InfoRow` helper + `NOT_AVAILABLE`
    constant; wired the dialog's visibility via `infoMessage` state.

- **`app/src/test/java/in/sreerajp/sms_sentry/MessageInfoTest.kt`** (new)
  - Verifies `loadMessageDetails` maps entity fields and marks provider fields unavailable for
    a message with no system row, that `readDetails` returns `null` (no crash) when the row is
    absent, and that `MessageInfoDialog` renders and shows the "Not available" placeholders.

## Scope notes

- Only the **thread screen** selection bar was changed. The inbox selection bar is
  sender-level (whole conversations), so "Message info" does not apply there.
- Copy, mark-read/unread, and delete actions are unchanged.

## Verification

- `./gradlew :app:compileDebugKotlin` — succeeds.
- `./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.MessageInfoTest"` — passes.
