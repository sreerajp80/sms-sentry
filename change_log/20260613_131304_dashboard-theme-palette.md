# Applied Claude Design Dashboard theme palette + Plus Jakarta Sans

Implements [plans/20260613_123806_dashboard-theme-palette.md](../plans/20260613_123806_dashboard-theme-palette.md).

## Source
`SMSSentry.zip` → `SMS Sentry Dashboard.dc.html` (Claude Design mockup). Its OKLCH colour
system for 5 schemes × {light, dark} was pre-converted to sRGB and applied to the app.

## Changes

### `app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Color.kt`
- Rewrote all 5 scheme palettes (Lavender, High Density, Sage, Cosmic Slate, Slate Blue)
  with the mockup's OKLCH-derived values, light + dark. Each scheme now exposes a full
  token set: Background / Surface / SurfaceAlt / Inset / Border / Text / TextMute /
  TextFaint / Primary / OnPrimary / PrimaryContainer / OnPrimaryContainer.
- Kept the `HighDensityBackgroundLight/Dark` val names (still referenced by the UI).
- Added scheme-independent status colours (`Good/GoodSoft/SpamStatus/SpamSoft`, light+dark)
  and message-category colours (`CategoryPersonal/Finance/Reminder/Spam`, light+dark).

### `app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Theme.kt`
- Each `lightColorScheme`/`darkColorScheme` now wires the richer tokens: primary/onPrimary,
  primaryContainer/onPrimaryContainer (the "accent-soft" card look), secondary(+container),
  surfaceVariant/onSurfaceVariant, outline/outlineVariant, surfaceTint, and error tokens
  mapped to the spam red. `MyApplicationTheme` signature unchanged.
- Added `@Composable` helpers `isDarkScheme()`, `categoryColors()`, `categoryColor(category)`
  that pick light/dark category colours from the active scheme's background luminance.
- Added `goodColor()/goodSoftColor()/spamColor()/spamSoftColor()` (theme-aware green/red).

### `app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Type.kt`
- Defined `PlusJakartaSans` `FontFamily` from bundled static weights (400/500/600/700/800)
  and applied it across the whole Material 3 `Typography` scale.

### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Donut chart, chart legend, and `MessageCard` category colour now use `categoryColors()` /
  `categoryColor()` (removed the bespoke high-density orange/teal category branch).
- Theme picker dots updated to the new per-scheme accent hexes.
- Finance credit/debit colours (month totals + per-row icon/amount) now use
  `goodColor()`/`spamColor()`; the "Calendar Synced" chip, "Active Sync Server" indicator,
  and "Sync Completed" success state use the `good` tokens. These now adapt to light/dark.

### Fonts / license (new files)
- `app/src/main/res/font/plus_jakarta_sans_{regular,medium,semibold,bold,extrabold}.ttf`
  (Plus Jakarta Sans, downloaded from the upstream Tokotype repo).
- `third_party/PlusJakartaSans-OFL.txt` — SIL Open Font License 1.1.

## Out of scope (unchanged)
- The always-dark P2P / Sync screens keep their bespoke slate UI.
- The SMS/notification permission status colours (green-when-granted / orange-when-missing)
  stay hardcoded — orange has no equivalent in the design palette, so converting only the
  green half would look inconsistent.

## Verification
`./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (only pre-existing deprecation
warnings; font `R.font.*` references resolve). Visual spot-check of the dashboard across
schemes/modes still pending on a device/emulator.
