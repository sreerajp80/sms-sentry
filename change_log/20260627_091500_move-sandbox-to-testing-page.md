# Change log: Move SMS Sentry Sandbox into Settings → Testing

Implements the sandbox portion (plan parts A & F) of
[plans/20260627_082654_move-sandbox-and-split-settings-pages.md](../plans/20260627_082654_move-sandbox-and-split-settings-pages.md),
completing that plan (the theme/integration split was logged separately in
[20260627_090000_split-theme-and-integration-pages.md](20260627_090000_split-theme-and-integration-pages.md)).

## What changed

File: `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

### A. Removed the old top-bar trigger
- Deleted the `showSimulationSmsDialog` state.
- Deleted the top-app-bar `simulate_sms_bar_button` `IconButton` (the `Icons.Default.Sms`
  `BadgedBox` action).
- Deleted the `if (showSimulationSmsDialog) { SimulateSmsDialog(...) }` block.

### B. Settings routing & nav row
- `SettingsScreen` gained a `"testing"` route → new `TestingSandboxPage`.
- `SettingsMainPage` gained an `onOpenTesting` callback and a new **Testing** `SettingsNavRow`
  (`Icons.Default.Science`, subtitle "SMS Sentry sandbox simulator"), placed between
  Advanced settings and About.

### C. `SimulateSmsDialog` → `TestingSandboxPage(viewModel, onBack)`
- Converted the pop-up `Dialog` into a full settings sub-page: `LazyColumn` with
  `SubPageHeader("Testing", onBack)` + a card holding the sandbox form (sender field, body
  field, the six template scenario chips, SIM-slot selector, trigger button).
- Dropped the dialog's custom scrollbar/`verticalScroll` plumbing (the `LazyColumn` scrolls).
- **Restyled** the hardcoded dark colors to theme-aware ones so the page reads correctly in
  both light and dark themes: card `Color(0xFF1E2235)` → `surface`; title/labels/chip text
  `Color.White` → `onSurface`; description `Color(0xFF8C92AC)` → `onSurfaceVariant`; selected
  SIM-slot text `Color.White` → `onPrimary`.
- Removed the **Cancel** button; the trigger button is now full-width. On trigger it validates
  non-empty sender/body (toasting a hint if empty), calls
  `viewModel.simulateSmsReceived(...)`, then shows a confirmation `Toast` and **stays on the
  page** so multiple messages can be simulated.
- Preserved test tags: `sandbox_sender_input`, `sandbox_body_input`,
  `sandbox_trigger_simulate_button`.

No ViewModel, data-layer, or manifest changes. No tests referenced the removed
`simulate_sms_bar_button` / `SimulateSmsDialog`.

## Verification

`./gradlew :app:compileDebugKotlin` compiles cleanly.
