# Plan: Move Sandbox into Settings + split Theme / Integration into their own pages

**Status:** completed

## Issue / goal

Two related UI reorganizations in the Settings area:

1. **Move the SMS Sentry Sandbox into Settings.** Today the sandbox is a pop-up
   (`SimulateSmsDialog`) launched from a top-app-bar SMS icon (`simulate_sms_bar_button`).
   We want it relocated under Settings as a new **"Testing"** section. Tapping that row
   opens a dedicated sub-page (same navigation pattern as **Advanced settings**) that hosts
   the sandbox simulator.

2. **Split Themes and System Integration into their own sub-pages.** Today
   `SettingsMainPage` renders the "Aesthetics & theme" and "System integration" blocks inline.
   We want each moved to its own sub-page reached from a nav row, exactly like
   **Advanced settings** already works.

End state of the Settings main page = a list of navigation rows:
Appearance & theme → · System integration → · Advanced settings → · Testing → · About →

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (only file)

No ViewModel changes — `viewModel.simulateSmsReceived(...)` is reused as-is.

## Plan

### A. Remove the old sandbox trigger (top app bar)
- Delete the top-bar "Simulate SMS Incoming" `IconButton` (the `Icons.Default.Sms` /
  `BadgedBox` action, ~lines 181–194).
- Delete the `showSimulationSmsDialog` state (~line 137) and the
  `if (showSimulationSmsDialog) { SimulateSmsDialog(...) }` block (~lines 300–305).

### B. Settings routing (`SettingsScreen`, ~line 3902)
- Extend the `subPage` state machine with new keys: `"theme"`, `"integration"`, `"testing"`
  (keeping existing `"advanced"` and `"about"`). The existing generic
  `BackHandler { subPage = null }` already covers all sub-pages.
- Wire each new branch to its new page composable; pass `onOpen*` callbacks into
  `SettingsMainPage`.

### C. `SettingsMainPage` (~line 3936)
- Remove the inline **Aesthetics & theme** cards (color scheme + appearance) and the inline
  **System integration** cards (`DefaultSmsAppCard`, `SystemIntegrationCard`); move their state
  (`appTheme`, `forceDark`, `systemTheme`, `schemes`, derived labels) into the new
  `ThemeSettingsPage`.
- Keep the hero header + footer.
- Replace removed sections with `SettingsNavRow`s:
  - Appearance & theme → `Icons.Default.Palette` → `onOpenTheme`
  - System integration → `Icons.Default.Extension` → `onOpenIntegration`
  - Advanced settings → `Icons.Default.Tune` → `onOpenAdvanced` (unchanged)
  - Testing → `Icons.Default.Science` → `onOpenTesting`
  - About → `Icons.Default.Info` → `onOpenAbout` (unchanged)

### D. New `ThemeSettingsPage(viewModel, onBack)`
- `LazyColumn` with `SubPageHeader("Appearance & theme", onBack)` followed by the existing
  COLOR SCHEME card (`SchemeCard` list) and APPEARANCE card (`AppearanceSegmented` + hint),
  moved verbatim from `SettingsMainPage`. Reuses existing `SchemeMeta`, `SchemeCard`,
  `AppearanceSegmented` helpers.

### E. New `IntegrationSettingsPage(viewModel, onBack)`
- `LazyColumn` with `SubPageHeader("System integration", onBack)` + `DefaultSmsAppCard(viewModel)`
  + `SystemIntegrationCard()` (existing private composables, just relocated call sites).

### F. New `TestingSandboxPage(viewModel, onBack)` (replaces `SimulateSmsDialog`)
- `LazyColumn` with `SubPageHeader("Testing", onBack)` + the sandbox simulator form moved out
  of `SimulateSmsDialog`: sender field, body field, the "Pretest Template Scenarios" chips
  (Bank Trx / Due Bill / Spam Box / Friend Msg / Malayalam OTP / Google OTP), the SIM-slot
  selector, and the **Trigger Simulated Receiver** button.
- Preserve existing test tags: `sandbox_sender_input`, `sandbox_body_input`,
  `sandbox_trigger_simulate_button`.
- **Restyle** the dialog's hardcoded dark colors (`Color(0xFF1E2235)`, `Color.White`,
  `Color(0xFF8C92AC)`) to theme-aware colors (`surface`, `onSurface`, `onSurfaceVariant`)
  so it reads correctly in both light and dark themes on a normal settings page.
- Behavior on trigger: validate non-empty, call
  `viewModel.simulateSmsReceived(sender, body, slot)`, then show a confirmation `Toast`
  and stay on the page (so multiple messages can be simulated). Keep the "SMS Sentry Sandbox"
  title/description text inside the card.
- Delete the now-unused `SimulateSmsDialog` composable.

## Notes / risks
- Behavioral change: the sandbox is no longer reachable from the top bar; it now lives at
  **Settings → Testing**. (This is the requested "move".)
- Roborazzi/UI tests that tap `simulate_sms_bar_button` (if any) would need updating; the
  inner field/button test tags are preserved to minimize churn. I'll grep tests during
  implementation and flag anything affected.
- No data-layer, manifest, or ViewModel changes.

## Out of scope
- The unrelated "Sandbox Bypass" finance-auth text and "virtual preset contacts" dialog are
  left untouched.
