# Change log: Split Appearance & theme and System integration into sub-pages

Implements the theme/integration portion (plan parts B–E) of
[plans/20260627_082654_move-sandbox-and-split-settings-pages.md](../plans/20260627_082654_move-sandbox-and-split-settings-pages.md).

## What changed

File: `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

- **`SettingsScreen` routing** — extended the `subPage` state machine with `"theme"` and
  `"integration"` keys, routing to the new `ThemeSettingsPage` / `IntegrationSettingsPage`.
  The existing generic `BackHandler { subPage = null }` covers them.
- **`SettingsMainPage`** — now a list of navigation rows only. Removed the inline
  "Aesthetics & theme" cards (color scheme + appearance) and the inline "System integration"
  cards. Added two new `SettingsNavRow`s:
  - Appearance & theme → `Icons.Default.Palette` → opens theme page
  - System integration → `Icons.Default.Extension` → opens integration page

  (Advanced settings and About rows unchanged.) Signature gained `onOpenTheme` /
  `onOpenIntegration` callbacks.
- **New `ThemeSettingsPage(viewModel, onBack)`** — `SubPageHeader("Appearance & theme")` plus
  the COLOR SCHEME and APPEARANCE cards moved verbatim, including the `appTheme`/`forceDark`/
  `systemTheme` state and `schemes` list that previously lived in `SettingsMainPage`. Reuses
  `SchemeCard` and `AppearanceSegmented`.
- **New `IntegrationSettingsPage(viewModel, onBack)`** — `SubPageHeader("System integration")`
  plus the existing `DefaultSmsAppCard(viewModel)` and `SystemIntegrationCard()`.
- Removed the now-unused `SettingsSectionHeader` helper.

## Not done (still pending from the plan)

Plan parts A and F (move the SMS Sentry Sandbox out of the top-app-bar dialog into a new
**Testing** sub-page, delete `SimulateSmsDialog`) are NOT implemented in this change.

## Verification

`./gradlew :app:compileDebugKotlin` compiles cleanly.
