# Change log: Category-colored filter pills + leaner cards

Implements [plans/20260614_132308_category-color-cards.md](../plans/20260614_132308_category-color-cards.md).
All changes in [SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt).

## What changed

1. **Category-colored top filter pills (`InboxFilterPill` + `InboxScreen` caller).**
   - Added a `color: Color` parameter. The caller maps each folder to its category color:
     Personal/Finance/Spam via `categoryColor(name)`, "Reminders" → `categoryColor("Reminder")`,
     "All" → `primary`.
   - Restyled so the category color shows in both light & dark mode: unselected = `surface`
     bg + `1.dp` border at 45% color + colored label/count; selected = `color.copy(0.18)` bg
     + `2.dp` border at full color. (Mirrors the read/unread card border language.)

2. **Leaner `MessageCard` — border now encodes the category.**
   - Read border changed from `outline` to `BorderStroke(1.dp, categoryColor.copy(0.45))`;
     unread stays `BorderStroke(2.dp, categoryColor)`. Thin = read, thick = unread, both in
     the category color.
   - Removed the second in-card row (`SimPill` + `CategoryPill`) — the category is now
     conveyed by the border, so the tag is redundant.
   - Moved the SIM indicator inline into the sender row:
     `[unread dot] sender  [SIM n]  time`. The row is now `CenterVertically` so the single
     line centers against the avatar.
   - Net effect: one fewer line per card → shorter cards → more messages per screen.

3. **Removed the now-unused `CategoryPill` composable** (no remaining references; verified
   by grep). `SimPill` remains (used inline).

## Notes
- UI-only; no data/model changes. `categoryColor`/`categoryColors` unchanged.
- No build run (no gradle in this environment) — recommend `gradle :app:assembleDebug` and a
  visual check in both light and dark mode.
