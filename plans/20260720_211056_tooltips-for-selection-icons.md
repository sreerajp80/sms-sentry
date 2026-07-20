# Plan: Long-press tooltips for every icon-only button

**Status:** completed

## What the issue is

Many buttons in the app are **icon-only** — an icon with no visible text label.
A new user cannot tell what they do. The trigger was the message selection bar
(double check / flag / copy / trash), but the same problem exists on top bars,
the message composer, the sync/pairing screen, steppers, and rule/schedule rows.

Every icon already has a `contentDescription` (read aloud by screen readers),
but that text is never shown on screen. The user wants a **tooltip that appears
on press-and-hold (long press)** so the meaning becomes clear — applied to **all
icon-only buttons**.

## The plan for the fix

Jetpack Compose Material3 (version 1.3.0, from Compose BOM `2024.09.00` already
in use) provides `TooltipBox` + `PlainTooltip`. A `PlainTooltip` used this way
appears automatically on **long-press** for touch input — exactly what the user
asked for. No new dependency is needed.

### Step 1 — add a reusable helper

Add a helper composable `TooltipIconButton` in `SmsOrganizerUi.kt`:

- wraps `TooltipBox` (using `TooltipDefaults.rememberPlainTooltipPositionProvider()`,
  a `rememberTooltipState()`, and `PlainTooltip { Text(tooltip) }`) around an
  `IconButton`;
- forwards `onClick`, `enabled` (default `true`), `modifier` (so existing
  `testTag`s are preserved) and the icon `content`;
- takes a `tooltip: String` for the hint text.

### Step 2 — use it for every icon-only button

Replace each icon-only `IconButton` with `TooltipIconButton`, passing a short
label. The label text is the existing `contentDescription`, reworded only where
that text is unclear to a normal user. The buttons and their tooltip text:

**Main inbox / top bar**
- Compose new message (line ~224) → "New message"
- Clear search (line ~1259) → "Clear search"

**Inbox selection bar** (lines ~1095-1147)
- Cancel selection, Mark as read, Move to folder, Delete, More actions

**ThreadScreen selection bar** (lines ~2054-2159 — the screenshot)
- Cancel selection, Mark as read, Mark as unread, Copy, Share, Delete

**ThreadScreen top bar**
- Back (line ~2180) → "Back"
- More actions (line ~2218) → "More actions"

**Message composer(s)**
- Send in thread (line ~2444) → "Send"
- Attach file (line ~7133) → "Attach file"
- Schedule send (line ~7149) → "Schedule send"
- Send SMS (line ~7232) → "Send"
- Contact lookup (line ~6914) → "Look up contact"

**Other Back buttons**
- lines ~2841, ~3207, ~3980, ~5656, ~6862 → "Back"

**Number stepper**
- Decrease (line ~4182) → "Decrease"
- Increase (line ~4198) → "Increase"

**Sync / pairing**
- Copy pairing code (lines ~4554, ~4766) → "Copy pairing code"

**Rules / scheduled list**
- Delete rule (line ~6337) → "Delete"
- Cancel scheduled message (line ~6825) → "Cancel scheduled message"

Buttons that already show a text label next to them, and the dropdown-menu items
(which already have text), are left unchanged.

### Behaviour notes
- Long-press shows the tooltip; a normal tap still runs the action, unchanged.
- Disabled buttons keep working the same way (helper forwards `enabled`).
- All existing `testTag`s stay on the `IconButton`, so current UI tests pass.

## Files to be changed

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
  - add the `TooltipIconButton` helper;
  - swap every icon-only `IconButton` listed above to use it.

## Testing

- Build the app; long-press a sample of the icons (selection bar, top-bar back,
  compose, send, stepper) to confirm the tooltip text appears and a normal tap
  still performs the action.
- Run the existing unit/UI tests to confirm nothing broke (test tags unchanged).
