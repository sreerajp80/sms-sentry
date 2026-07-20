# Change log: Long-press tooltips for every icon-only button

**Implements:** [plans/20260720_211056_tooltips-for-selection-icons.md](../plans/20260720_211056_tooltips-for-selection-icons.md)

## What changed

Icon-only buttons in the app now show a small **tooltip on press-and-hold
(long press)** so users can learn what each icon does. A normal tap still runs
the action as before.

### New helper

Added `TooltipIconButton` in
`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`. It wraps
Material3's `TooltipBox` + `PlainTooltip` (already available via Compose BOM
`2024.09.00`, no new dependency) around a normal `IconButton`, and forwards
`onClick`, `enabled`, and `modifier` (so existing `testTag`s stay on the
button). A plain tooltip used this way appears automatically on long press for
touch input.

### Buttons given tooltips

All in `SmsOrganizerUi.kt`:

- **Inbox / top bar:** New message, Clear search.
- **Inbox selection bar:** Cancel selection, Mark as read, Move to folder,
  Delete, More actions.
- **Thread selection bar** (the screen in the request): Cancel selection,
  Mark as read, Mark as unread, Copy, Share, Delete.
- **Thread top bar:** Back, More actions.
- **Composers:** Send (thread reply), Send (new message), Attach file,
  Schedule send, Look up contact.
- **Other back buttons:** sender info, message detail, contribution,
  a generic screen back, and a dialog back.
- **Number stepper:** Decrease, Increase.
- **Sync / pairing:** Copy pairing code (two places).
- **Rules / scheduled list:** Delete rule, Cancel scheduled message.

Tooltip text reuses each icon's existing `contentDescription`, reworded where it
was unclear (e.g. "Compose New Message" → "New message",
"Contact Lookup Trigger" → "Look up contact").

Dropdown-menu items were left unchanged because they already show text labels.

## Testing

- `./gradlew.bat :app:compileDebugKotlin` — **BUILD SUCCESSFUL**.
- All existing `testTag`s are preserved on the underlying `IconButton`, so
  current UI tests still target the same nodes.
