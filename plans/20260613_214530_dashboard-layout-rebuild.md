# Rebuild the Dashboard screen to match the Claude Design mockup

## What the issue is
The earlier change ported the mockup's **colours + font** onto the *existing* Dashboard
layout. The user expected the Dashboard **layout/components** to match the mockup
(`SMS Sentry Dashboard.dc.html` + the two reference screenshots). The current
`DashboardScreen` (AC Balance + Spam cards, donut chart, 3 channel chips, Recent
Transactions) is structurally different from the mockup.

## Target layout (from mockup)
1. **Hero** — eyebrow "WELCOME BACK"; headline "Your inbox is secure." (the word
   "secure." in the accent colour); subtext "Threads are evaluated locally by offline
   security engines. Nothing leaves your device."
2. **Status pills row** — "● Engine active" (good colour on good-soft pill) and
   "🕘 Scanned 2m ago · 0 threats" (muted pill).
3. **Available Balance card** (full width, accent-soft container) — "AVAILABLE BALANCE"
   + a "Locked/Visible" pill; large `₹` amount (masked `••,•••.••` when locked, real when
   unlocked); subtitle ("Parsed from bank SMS · stays on device" / account line); a
   "Tap to decrypt" pill button when locked. Wired to the existing finance-auth flow
   (`isFinanceHidden`, `triggerDeviceAuthentication`, navigate to Finance).
4. **Stat row** (2 cards) — left: spam icon (spam-soft) + "Safe" badge + big `spamCount`
   + "Spam blocked · 7d". Right: lock icon (accent-soft) + "AES-256" badge + big
   `totalMessagesCount` + "Threads encrypted".
5. **Message breakdown card** — header "Message breakdown / Auto-sorted · this week" +
   total count; a **stacked segment bar** (Personal/Finance/Reminders/Spam by proportion,
   using `categoryColors()`); four legend rows, each = colour dot + label + count + a thin
   track-and-fill progress bar (fill ∝ count / max count). Replaces the donut chart.
6. **Operational channels** — header + "All"; **2×2 grid** of row-cards (icon in an
   accent-soft rounded square + title + subtitle): Rules ("7 active"→Settings),
   Calendar ("3 events"→Reminders), P2P Backup ("Synced"→Sync), Vault ("Locked"→Finance).

## Files to change
### `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Rewrite the body of `DashboardScreen` (~242–550) into the 6 sections above. Keep the
  existing data wiring (`inboxMessages/spamMessages/reminders/transactions`, counts,
  `activeBalance`, finance-auth launcher) and `onNavigate`.
- Add small private composables: `StatusPill`, `DashboardStatCard`, `BreakdownRow`
  (label+count+bar), `OperationalChannelCard`. Remove/replace `DashboardActionItem`
  usage; keep `ChartLegendItem`/`SmsDonutChart` only if still referenced (else delete
  `SmsDonutChart` to avoid dead code).
- Keep `testTag("dashboard_balance_card")` on the balance card so existing UI tests pass.

### Top app bar (same file, ~104–153) — *pending your choice*
- Optional: swap the `FilterList` glyph for a shield in a small accent-rounded square and
  add an "Offline · Encrypted" subtitle under the title. Existing action buttons
  (simulate-SMS badge, compose/edit) are kept and functional. The mockup's search/bell
  are decorative; I will **not** add dead buttons.

## Decisions (confirmed)
- **Restyle the top app bar**: shield-in-accent-square logo + "Offline · Encrypted"
  subtitle; keep existing simulate-SMS + compose actions.
- **Remove the "Recent Transactions" list** from the Dashboard (matches mockup;
  transactions stay on the Finance tab).

## Out of scope
- Bottom nav already matches the mockup (Dashboard/Inbox/Finance/Reminders/Sync/Settings).
- Decrypt scramble animation and the ambient-glow/gradient flourishes from the HTML are
  cosmetic; I'll approximate with a soft container, not a literal port.

## Verification
`./gradlew :app:installDebug` to the emulator; screenshot the Dashboard in light + dark
and at least one non-Lavender scheme; confirm balance lock/decrypt + channel navigation.
