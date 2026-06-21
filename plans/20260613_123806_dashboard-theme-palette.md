# Apply new "Claude Design" Dashboard theme palette

## Source
`SMSSentry.zip` → `SMS Sentry Dashboard.dc.html`. A Claude Design mockup that defines a
refined **OKLCH‑based** colour system with 5 schemes (Lavender, High Density, Sage,
Cosmic Slate, Slate Blue), each in **light + dark**, plus extra design tokens
(`accent`, `accent-soft`, `good`, `spam`, per‑category colours).

## What the issue is
The app already ships the same 5 schemes + a picker + dark/light switching, but the
colour *values* are the older Material/AI‑Studio palette. The mockup uses softer,
hue‑tinted surfaces, refined accents, and an "accent‑soft container" look (balance card,
stat chips). Most of the UI reads `MaterialTheme.colorScheme`, so swapping the palettes
restyles the whole app; a few category/legend colours are hardcoded and must be updated
to match the mockup's breakdown chart.

## Scheme → enum mapping (unchanged names, new values)
| Enum (kept) | Picker label | Hue/Chroma | accent L (light/dark) |
|---|---|---|---|
| LAVENDER | Lavender | 295 / .13 | .52 / .62 |
| HIGH_DENSITY | High Density | 262 / .115 | .42 / .60 |
| SAGE | Sage | 155 / .07 | .50 / .66 |
| SLATE | Cosmic Slate | 272 / .075 | .46 / .64 |
| BLUE | Slate Blue | 243 / .095 | .52 / .66 |

All hex below are pre‑computed sRGB from the mockup's `buildTheme()` OKLCH math.

## Files to change

### 1. `app/.../ui/theme/Color.kt`
Replace the 5 scheme palettes (keep existing val names so `Theme.kt` and the
`HighDensityBackground*` checks in the UI keep compiling) and add new tokens:
- Per scheme, light + dark: `Background`, `Surface`, `Surface2`, `Inset`, `Border`,
  `Text`, `TextMute`, `TextFaint`, `Primary`(accent), `OnPrimary`(accentText),
  `PrimaryContainer`(accentSoft), `OnPrimaryContainer`(accentSoftText).
- Shared status + category constants (light/dark):
  `Good/GoodSoft/Spam/SpamSoft`, `CategoryPersonal/Finance/Reminder/Spam`.

Computed values (0xFF + hex):
```
LAVENDER L: bg FAF9FF surf F6F3FF surf2 EDEBF5 inset E7E4F1 border DEDCE7 text 272430 mute 646171 faint 878491 acc 6F57AB onAcc FCFBFF accSoft E4D9FF accSoftTx 543A8B
LAVENDER D: bg 100A1D surf 1D172D surf2 241F33 inset 161222 border 343040 text F2F0F9 mute ABA9B6 faint 7B7885 acc 8C76C8 onAcc 0F091C accSoft 302748 accSoftTx D4C7FF
HIGH_DENSITY L: bg F6FAFF surf F0F5FF surf2 E9EDF5 inset E1E6F1 border D9DEE7 text 212731 mute 5B6473 faint 808693 acc 284A8B onAcc F8FCFF accSoft CBE2FF accSoftTx 284A8B
HIGH_DENSITY D: bg 060E1D surf 121C2D surf2 1A2332 inset 0D1522 border 2C3340 text EDF2FA mute A4ABB8 faint 747B87 acc 5C7FC1 onAcc 050D1E accSoft 1F2E48 accSoftTx B9D2FF
SAGE L: bg F7FCF8 surf F1F7F3 surf2 E9EEEB inset E2E8E3 border DAE0DC text 1E2A22 mute 58685D faint 7D8A81 acc 416F52 onAcc F7FEF9 accSoft CEE9D6 accSoftTx 2A583C
SAGE D: bg 07110A surf 131F17 surf2 1B261F inset 0E1711 border 2D352F text ECF4EE mute A1AFA5 faint 717E75 acc 719F80 onAcc 001206 accSoft 213327 accSoftTx BCDAC5
SLATE L: bg F8FAFF surf F3F5FB surf2 EBEDF2 inset E4E6ED border DCDEE4 text 232631 mute 5E6373 faint 828693 acc 495582 onAcc F9FCFF accSoft D7E0FE accSoftTx 3F4A76
SLATE D: bg 0B0E18 surf 181B27 surf2 1F222D inset 12141E border 30323C text EFF1FA mute A6AAB8 faint 767A87 acc 7D8AB8 onAcc 080B1E accSoft 282D3F accSoftTx C6D0EF
BLUE L: bg F5FBFF surf EFF6FC surf2 E8EEF3 inset DFE7EE border D8DFE4 text 1D2830 mute 576571 faint 7C8892 acc 316E9B onAcc F6FDFF accSoft C4E6FF accSoftTx 0D527C
BLUE D: bg 04101A surf 0F1D29 surf2 17242F inset 0B161F border 2A343D text EBF3F9 mute A0ADB7 faint 717C85 acc 5F99C5 onAcc 000F1D accSoft 1A3042 accSoftTx B1D6F4
Status L: good 348757 goodSoft CCF3D8 spam C43F3E spamSoft FFD9D4
Status D: good 57BC80 goodSoft 0F3620 spam EA6A64 spamSoft 551F1D
Category L: personal 7A59C3 finance 3A8357 reminder 207DBE spam CB4644
Category D: personal A083EA finance 61B380 reminder 4FA6E9 spam EA6A64
```

### 2. `app/.../ui/theme/Theme.kt`
For each of the 10 `lightColorScheme`/`darkColorScheme` blocks, map:
- `primary`=accent, `onPrimary`=onAcc, `primaryContainer`=accSoft,
  `onPrimaryContainer`=accSoftTx, `secondary`=accSoftTx, `secondaryContainer`=accSoft,
  `onSecondaryContainer`=accSoftTx, `background`=bg, `onBackground`=text,
  `surface`=surf, `onSurface`=text, `surfaceVariant`=inset, `onSurfaceVariant`=mute,
  `outline`=border, `outlineVariant`=border, `surfaceTint`=accent,
  `error`=spam, `onError`=onAcc, `errorContainer`=spamSoft, `onErrorContainer`=spam.
- Keeps `MyApplicationTheme(themeStyle, darkTheme, content)` signature and the
  `when(themeStyle)` switch intact.

### 3. `app/.../ui/SmsOrganizerUi.kt` (targeted, not all 306 colour sites)
Switch the canonical category/breakdown colours to the new category constants so the
chart matches the mockup, and refresh the picker dots:
- Pie slice colours (~618‑621) → `CategoryPersonal/Finance/Reminder/Spam` (light/dark aware).
- Chart legend (~425‑428) → same constants.
- `MessageCard` category colour (~766‑780) → same constants (drop the bespoke
  high‑density orange/teal branch in favour of the unified category palette).
- Theme picker dots (~1736‑1760) → new per‑scheme accent hexes
  (Lavender 6F57AB, HighDensity 284A8B, Sage 416F52, Cosmic 495582, Blue 316E9B).

### 4. Typography — Plus Jakarta Sans (added per user request)
Font is **SIL OFL 1.1** (open source). Bundle static weights offline (minSdk 24, so
no downloadable‑fonts / Play Services dependency):
- Add `app/src/main/res/font/`: `plus_jakarta_sans_regular/medium/semibold/bold/extrabold.ttf`
  (weights 400/500/600/700/800, matching the mockup).
- Add `third_party/PlusJakartaSans-OFL.txt` (license).
- In `Type.kt`: define `PlusJakartaSans` `FontFamily` and set it as the default
  `fontFamily` across the `Typography` text styles so the whole app uses it.

## Out of scope (call out, don't change unless you say so)
- The always‑dark **P2P / Sync** screens (~2371‑2820) keep their bespoke slate UI.
- Credit/debit greens & misc one‑off status colours in finance rows.

## Verification
Build with the project's no‑wrapper Gradle setup; spot‑check Dashboard light + dark for
each of the 5 schemes (surfaces, balance/stat cards = accent‑soft containers, breakdown
bar colours, picker selection state).
