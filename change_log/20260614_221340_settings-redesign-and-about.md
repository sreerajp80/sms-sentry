# Settings redesign + About screen — change log

Implements plan `plans/20260614_215447_settings-redesign-and-about.md`
(with the follow-up decision to move Security/SIM/Filters/Backup into **one
combined "Advanced settings" page**).

## Files added
- **`app/src/main/assets/about_config.json`** — the About config file. Holds
  `author`, `lastBuildDate`, `ideUsed`, `aiUsed`. Editable without code changes.
  Initial values: Sreeraj P / 2026-06-14 / Android Studio / Claude (Claude Code).
- **`app/src/main/java/in/sreerajp/sms_sentry/util/AboutConfig.kt`** — `AboutInfo`
  data class + `loadAboutConfig(context)` which reads the JSON asset via
  `org.json.JSONObject` (no new dependency) and falls back to "—" on any
  missing file/key.

## Files changed
- **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
  - Added imports: `util.AboutInfo`, `util.loadAboutConfig`, `BuildConfig`,
    `androidx.compose.foundation.isSystemInDarkTheme`,
    `androidx.compose.runtime.saveable.rememberSaveable`.
  - **Rewrote `SettingsScreen`** into a small router with a local
    `subPage` state (`null` / `"advanced"` / `"about"`) + `BackHandler`.
  - **New `SettingsMainPage`** matching the Claude Design mockup:
    - "PREFERENCES / Settings" hero header.
    - **Aesthetics & theme**: vertical `SchemeCard` list (swatch strip + name +
      description + check/radio circle) replacing the old horizontal
      `ThemePillButton` row; `AppearanceSegmented` 3-way control
      (Light / Dark / System) + mode hint line, replacing the two switches.
      Maps onto existing `selectedTheme` / `isDarkTheme` / `isSystemTheme` state
      (no ViewModel change). Works across all 5 schemes and all 3 modes since it
      uses only `MaterialTheme.colorScheme` tokens.
    - **System integration**: restyled status card (circular check badge + On/Off
      pill) via new `SystemIntegrationCard` / `IntegrationStatusRow`; keeps the
      runtime permission request + "Open app info"; preserves
      `testTag("permissions_integration_card")`.
    - **Nav rows** (`SettingsNavRow`): "Advanced settings" and "About".
    - Version footer using `BuildConfig.VERSION_NAME`.
  - **New `AdvancedSettingsPage`**: back-header + the existing Security & Privacy
    (preserves `testTag("security_card")`), Multi-SIM Preferences, Custom
    Filtering Safeguards (add-rule form + rule list), and Backup & Database
    Integrity sections — logic unchanged, relocated off the main page.
  - **New `AboutPage`** + `AboutRow`: app-identity block (logo + name + version)
    and a card listing Author / Last Build Date / IDE Used / AI Used loaded from
    `about_config.json`.
  - Helper composables added: `SettingsSectionHeader`, `SchemeCard`,
    `AppearanceSegmented`, `AppearanceSegment`, `SettingsNavRow`, `SubPageHeader`.
  - **Removed** the now-unused `ThemePillButton` composable.

## Behaviour
- All prior functionality retained (scheme/mode selection, permission granting,
  finance lock, SIM default, filter rule add/delete, CSV/JSON export, import,
  wipe DB) — only restyled/relocated.
- No ViewModel/data/Gradle changes (`buildConfig` was already enabled).

## Verification
- `./gradlew :app:compileDebugKotlin --offline` → **BUILD SUCCESSFUL** (only
  pre-existing deprecation warnings: `Divider`, `Icons.Filled.ArrowBack`, etc.).
- No unit/screenshot test asserts on Settings UI, so existing tests are unaffected.
