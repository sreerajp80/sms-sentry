# Change log: Fix "Host Sync Server" button label wrapping

Implements [plans/20260614_204042_fix-sync-button-wrap.md](../plans/20260614_204042_fix-sync-button-wrap.md).

## What changed

`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — Sync page action buttons:

- Renamed the host action button label from `"Host Sync Server"` to `"Host Server"`
  so it fits on one line at half-row width (it shares the row equally with
  "Connect to Peer" via `weight(1f)`), parallels "Connect to Peer", and matches
  the "Host Configuration" panel title.
- Added `maxLines = 1` to both action button `Text`s as a guard against future
  silent wrapping/clipping under the fixed `height(48.dp)`.

No behavior change — label text and a maxLines guard only.
