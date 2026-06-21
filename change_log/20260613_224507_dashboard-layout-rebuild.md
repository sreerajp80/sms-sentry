# Rebuilt the Dashboard screen + top bar + bottom nav to match the mockup

Implements [plans/20260613_214530_dashboard-layout-rebuild.md](../plans/20260613_214530_dashboard-layout-rebuild.md).

## Why
The prior change only recoloured the existing Dashboard. The user wanted the Dashboard
**layout** to match the Claude Design mockup (`SMS Sentry Dashboard.dc.html` + reference
screenshots).

## Changes — `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

### Dashboard body (`DashboardScreen`, replaced old donut/AC-balance layout)
Rebuilt into the mockup's sections, reusing existing data + finance-auth wiring:
1. **Hero** — "WELCOME BACK" eyebrow, "Your inbox is secure." (accent on "secure."),
   offline subtext (`buildAnnotatedString`).
2. **Status pills** — "Engine active" (good) + "Scanned 2m ago · 0 threats" (muted).
3. **Available Balance** card (full-width, accent-soft container) — Locked/Visible pill,
   masked `₹ ••,•••.••` vs real balance, "Tap to decrypt" button; same `triggerDevice
   Authentication` flow and `testTag("dashboard_balance_card")`.
4. **Stat row** — Spam blocked (spam-soft icon + "Safe" badge) and Threads encrypted
   (accent-soft lock + "AES-256" badge).
5. **Message breakdown** — stacked proportional segment bar + four legend rows each with a
   count and a track/fill progress bar (replaces `SmsDonutChart`).
6. **Operational channels** — 2×2 grid: Rules→Settings, Calendar→Reminders,
   P2P Backup→Sync, Vault→Finance.
- Removed the **Recent Transactions** list (not in the mockup; still on the Finance tab).
- Deleted now-unused `SmsDonutChart`, `ChartLegendItem`, `DashboardActionItem`; added
  private `StatusPill`, `DashboardStatCard`, `BreakdownRow`, `OperationalChannelCard`.
- Added import `spamSoftColor`.

### Top app bar
- Shield glyph in an accent rounded-square + "SMS Sentry" with an "Offline · Encrypted"
  subtitle; dropped the hardcoded `FontFamily.SansSerif` so it uses Plus Jakarta Sans.
  Existing simulate-SMS and compose actions kept.

### Bottom navigation
- Fixed wrapping labels ("Dashbo ard"/"Remind ers") → single line (`maxLines = 1`,
  `softWrap = false`, 9.sp).
- Icons now match the mockup: Dashboard `GridView`, Inbox `MailOutline`, Finance
  `MonetizationOn`, Reminders `Schedule` (clock), Sync `Sync` (circular arrows), Settings
  `Settings`. Tab titles/routing unchanged.

## Verification
`./gradlew :app:installDebug` to the emulator. Verified on-device:
- Dashboard matches the mockup in **light** and **dark** (system night toggle; app follows
  system theme) — hero, pills, balance card, stat cards, breakdown bars, 2×2 channels.
- Bottom nav labels single-line with correct icons.
