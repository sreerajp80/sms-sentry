# Settings redesign + About screen

## What this implements

1. Redesign the **Settings** screen to match the new Claude Design mockup
   (`SMSSentry.zip → "SMS Sentry Settings.dc.html"`), for **all 5 colour schemes**
   and **all 3 appearance modes** (Light / Dark / System). Because the screen is
   built entirely from `MaterialTheme.colorScheme` tokens, it automatically renders
   correctly for every theme/mode — no per-theme code.
2. Move **Security & Privacy, Multi-SIM, Filter Rules, Backup & Database** off the
   main Settings page into a **single combined "Advanced settings" sub-page**
   (per user decision).
3. Add an **About** entry that opens an **About sub-page**, whose values
   (Author, Last Build Date, IDE Used, AI Used) are read from a **config file**:
   `app/src/main/assets/about_config.json`.

## The issue / motivation

- The current `SettingsScreen` (lines ~2471–3034 of `SmsOrganizerUi.kt`) uses an
  older visual language: horizontal `ThemePillButton` row for schemes, and two
  separate switches ("Sync with System Dark Mode" + "Force Dark Theme") for
  appearance. The new design replaces these with a **vertical scheme-card list**
  (swatch + name + description + check circle) and a **3-way segmented control**
  (Light / Dark / System) with a hint line.
- The page is currently one long scroll mixing presentation and advanced/admin
  controls. The new design keeps the main page focused (Theme + System Integration),
  with everything else behind navigation rows.
- There is no About screen today.

## Design mapping (mockup → existing state)

The mockup's `scheme`/`appearance` state maps onto the **existing** ViewModel state
(no ViewModel changes needed):

| Mockup control            | Existing state                                             |
|---------------------------|-----------------------------------------------------------|
| Scheme card selected      | `viewModel.selectedTheme` (`ThemeStyle`)                  |
| Appearance = System       | `isSystemTheme = true`                                     |
| Appearance = Light        | `isSystemTheme = false`, `isDarkTheme = false`            |
| Appearance = Dark         | `isSystemTheme = false`, `isDarkTheme = true`             |

Scheme order/labels from the mockup: Lavender "Soft violet", High Density
"Indigo · compact", Sage "Calm green", Cosmic Slate "Muted plum",
Slate Blue "Cool blue".

## Files to change / add

### 1. `app/src/main/assets/about_config.json`  *(new)*
The About config file. Initial contents (user can edit; **please confirm values**):
```json
{
  "author": "Sreeraj P",
  "lastBuildDate": "2026-06-14",
  "ideUsed": "Android Studio",
  "aiUsed": "Claude (Claude Code)"
}
```

### 2. `app/src/main/java/in/sreerajp/sms_sentry/util/AboutConfig.kt`  *(new)*
- `data class AboutInfo(author, lastBuildDate, ideUsed, aiUsed)`.
- `fun loadAboutConfig(context): AboutInfo` — reads `about_config.json` from assets
  via `context.assets.open(...)` + `org.json.JSONObject` (no new dependency).
  Falls back to safe defaults / "—" if the file or a key is missing.

### 3. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`  *(edit)*
Rewrite `SettingsScreen` and add helper composables. Navigation between the main
page and the two sub-pages is local to `SettingsScreen`:
```kotlin
var subPage by rememberSaveable { mutableStateOf<String?>(null) } // null | "advanced" | "about"
if (subPage != null) BackHandler { subPage = null }
```

- **Main Settings page** (`subPage == null`):
  - Hero header: "PREFERENCES" eyebrow + "Settings" title.
  - **Section header** helper `SettingsSectionHeader(icon, title)` (icon + bold title).
  - **Aesthetics & theme** card:
    - `SchemeCard` list (new composable): swatch strip (accent / accent-soft /
      surfaceVariant), name, description, and a check/radio circle. Selected card =
      `primary` border + `primaryContainer` background + filled check.
    - `AppearanceSegmented` (new composable): 3 segments Light / Dark / System with
      icons; selected segment raised (surface + shadow), plus a hint line below.
  - **System integration** card: keep existing permission-status rows (SMS receiver,
    notifications) restyled to the mockup (circular check badge, "On" pill), keep
    the runtime permission request + "Open app info" button. Preserve existing
    `testTag("permissions_integration_card")`.
  - **Navigation rows** (new `SettingsNavRow(icon, title, subtitle, onClick)`):
    - "Advanced settings" → `subPage = "advanced"`
    - "About" → `subPage = "about"`
  - **Version footer**: `SMS Sentry · v{BuildConfig.VERSION_NAME} · Offline build`.
- **Advanced sub-page** (`subPage == "advanced"`): back-arrow header "Advanced
  settings", then the existing **Security & Privacy**, **Multi-SIM Preferences**,
  **Custom Filtering Safeguards** (+ add-rule form & rule list), and
  **Backup & Database Integrity** sections — moved verbatim (logic unchanged),
  lightly restyled to match. Preserves `testTag("security_card")`.
- **About sub-page** (`subPage == "about"`): back-arrow header "About", an app
  identity block (logo + name + version), and a card listing **Author**,
  **Last Build Date**, **IDE Used**, **AI Used** from `loadAboutConfig(context)`.
- Add import for `in.sreerajp.sms_sentry.BuildConfig` and the new `util.AboutConfig`.
- `ThemePillButton` becomes unused; remove it (or leave if referenced elsewhere —
  grep shows it is only used in `SettingsScreen`, so it will be removed).

## Behaviour preserved
- All existing functionality (theme/scheme selection, dark/system mode, permission
  granting, finance lock, SIM default, filter-rule add/delete, CSV/JSON export,
  import, wipe DB) is retained — only relocated/restyled.
- No ViewModel, data, or Gradle changes (BuildConfig is already enabled).

## Testing
- No existing unit/screenshot test asserts on Settings elements (the screenshot test
  captures the Dashboard tab), so this change does not break tests.
- Manual verification: build, open Settings, switch each scheme + each appearance
  mode, open Advanced and About sub-pages, confirm About values come from the JSON.

## Out of scope / assumptions
- The mockup's top app-bar (logo + info button) already exists app-wide; the
  in-page hero header is added but the global top bar/bottom nav are unchanged.
- `about_config.json` values above are placeholders to be confirmed by the user.
