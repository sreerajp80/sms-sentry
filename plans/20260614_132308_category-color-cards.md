# Plan: Category-colored filter pills + leaner cards (border = category)

**Status:** completed

## The issue / goal

Each category (Personal/Finance/Reminder/Spam) already has a theme-aware color
(`categoryColor()` in [ui/theme/Theme.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Theme.kt)).
We want to lean on that color to declutter and shorten cards:

1. **Top filter pills** should carry their category color (both light & dark mode), instead
   of the current generic `primary`-when-selected styling.
2. **Drop the in-card `CategoryPill`** — the card's border color already encodes the
   category: thin category-colored border = read, thick category-colored border = unread.
3. **Move SIM (SIM 1 / SIM 2) inline with the sender name**, so the dedicated pill row
   (SimPill + CategoryPill) is removed entirely → one less line → shorter cards → more
   messages per screen.

## Files to change (single file: SmsOrganizerUi.kt)

### 1. `InboxFilterPill` — category color
- Add a `color: Color` param (the folder's category color; `primary` for "All").
- Caller (`InboxScreen`) maps folder → color: Personal/Finance/Spam via
  `categoryColor(name)`, "Reminders" → `categoryColor("Reminder")`, "All" → `primary`.
- Styling (works in both modes): unselected → `surface` bg + `1.dp` border in `color` +
  `color` text; selected → `color.copy(alpha=0.18)` bg + `2.dp` border in `color` + `color`
  text. Count badge tinted with `color`. Mirrors the read/unread border language.

### 2. `MessageCard` — border encodes category, drop CategoryPill, inline SIM
- Border: unread → `BorderStroke(2.dp, categoryColor)`; read → `BorderStroke(1.dp,
  categoryColor.copy(alpha = 0.45f))` (was `outline`).
- Remove the `Spacer(5.dp)` + `Row { SimPill(...); CategoryPill(...) }` block.
- Put the SIM indicator inline in the sender row, between sender and timestamp:
  `[unread dot] sender(weight 1f)  [SIM n]  time`.
- Set that sender `Row` to `Alignment.CenterVertically` so a single line centers against
  the 44dp avatar.

### 3. `CategoryPill`
- Becomes unused after removal; leave it defined (harmless) OR remove it. Plan: **remove**
  it to avoid an unused-symbol warning, since nothing else references it (verified by grep).

## Notes / non-goals
- `SimPill` stays (now used inline). `categoryColor`/`categoryColors` unchanged.
- No data/model changes; purely UI.

## Verification
- Recommend local `gradle :app:assembleDebug` (no gradle in this env).
- Manual: filter pills show category colors in light & dark; cards show category-colored
  border (thin=read, thick=unread), no category tag inside, SIM shown beside sender, and
  are visibly shorter.
