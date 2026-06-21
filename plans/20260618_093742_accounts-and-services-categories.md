# Plan: Rename "Finance" → "Accounts" and add a new broad "Services" category

## Issue / goal

Today the app has four message categories, stored as **plain strings** on each
`SMSMessage.category`: `Personal`, `Finance`, `Reminder`, `Spam`. The `Finance`
category is doing double duty — it captures *both* real money movement (debits/
credits/balances, which feed the ledger, balance card, and finance lock) *and*
service-ish alerts like OTPs and recharges that aren't really "money".

Requested change (confirmed with user):

- Rename **Finance → Accounts**. Accounts keeps *only* money/transaction/ledger
  behavior (amount, balance, credit/debit extraction → `FinanceTx` ledger row,
  balance card, the existing device-auth lock).
- Add a new, broader **Services** category (5th category) for non-money
  transactional/informational alerts: OTP / verification codes, deliveries /
  orders / shipments, bookings / tickets, recharges, etc. Services is a plain
  message category — **no ledger, no balance, no lock, no new bottom-nav tab**;
  it shows up as an Inbox folder + a Dashboard breakdown row.

Because the category is a bare string compared with `== "Finance"` in ~a dozen
places and persisted in the Room DB, the rename has to be applied consistently
everywhere *and* existing stored rows need to be relabeled via a DB migration.

## Design decisions (proposed — please confirm)

1. **Category string value**: emit `"Accounts"` (was `"Finance"`) and `"Services"`
   (new) from the classifier; these are the canonical stored strings.
2. **Keep internal code names unchanged** to limit churn and risk: the ledger
   entity stays `FinanceTx` / table `finance_transactions`; the lock state stays
   `isFinanceLocked` / `isFinanceAuthenticated`; the screen stays `FinanceScreen`.
   Only **user-facing strings**, the **category value**, and **keyword lists**
   change. (Alternative: full rename of these too — more churn, no functional
   gain. Recommend NOT doing it now.)
3. **Keyword split** in `SmsClassifier`:
   - `ACCOUNTS_KEYWORDS` (money): `debited, credited, spent, withdrawn, withdrew,
     paid, received, transfer, txn, transaction, a/c balance, available bal,
     payment of, bank`
   - `SERVICES_KEYWORDS` (new): `otp, one time password, verification code, verify,
     delivered, out for delivery, shipped, dispatched, order, booking, booked,
     ticket, confirmed, recharge`
   - i.e. `otp` and `recharge` move **out of** money and **into** Services.
4. **Classification priority order**: Spam → Accounts (money) → Reminder → Services
   → Personal. Rationale: money and time-sensitive reminders stay highest; Services
   is the softer catch-all for transactional-but-not-due/not-money alerts.
   (This is the main judgement call — flag if you'd prefer Services before Reminder.)
5. **Services extraction**: none. `runExtractions("Services", …)` returns a plain
   result (no amount/balance/dueDate). OTP-copy notification behavior is unaffected
   (it lives in the notification helper, independent of category).
6. **DB migration v4 → v5** (non-destructive) to relabel existing data:
   `UPDATE messages SET category='Accounts' WHERE category='Finance'` and
   `UPDATE filter_rules SET targetCategory='Accounts' WHERE targetCategory='Finance'`.
   No schema change, just data. (Without this, already-installed users' old
   "Finance" messages would fall into no folder.)
7. **Theme**: add a 5th category color slot for Services; `categoryColors()` returns
   5 colors; `categoryColor()` maps `"Accounts"`→c[1], `"Services"`→c[4], Spam stays
   the fallback. Color-constant names: rename `CategoryFinance*`→`CategoryAccounts*`
   and add `CategoryServices*` (proposed Services hue: teal/cyan, distinct from the
   green Accounts and blue Reminder).
8. **Services is NOT a bottom-nav tab.** Bottom nav just renames the existing
   "Finance" tab label → "Accounts". Services appears only as an Inbox filter pill
   and a Dashboard breakdown row.

## Files to change

### Engine
- `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
  - Replace `FINANCE_KEYWORDS` with `ACCOUNTS_KEYWORDS`; add `SERVICES_KEYWORDS`.
  - In `classify()`: emit `"Accounts"` for the money branch; add a Services branch
    in the priority order above.
  - In `runExtractions()`: `if (category == "Finance")` → `"Accounts"`; Services
    falls through with no extraction.
  - Update the `category` doc comment (line 8) to list the 5 categories.

### Data
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
  - 3× `classification.category == "Finance"` / `targetCategory == "Finance"`
    → `"Accounts"` (lines ~75, ~217, ~275) and surrounding comments (~249–256).
  - Seed `addRule` calls: `OTP`→`Services`, `RECHARGE`→`Services` (lines 347–348);
    update seed comments.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt`
  - Update category-list doc comments (lines 12, 49). `FinanceTx` entity unchanged.
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt`
  - `version = 4` → `5`; add `MIGRATION_4_5` (data relabel above); register it in
    `addMigrations(...)`.

### Theme
- `app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Color.kt`
  - Rename `CategoryFinanceLight/Dark` → `CategoryAccountsLight/Dark`; add
    `CategoryServicesLight/Dark`.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Theme.kt`
  - `categoryColors()`: append the Services color (5 entries); update doc comment.
  - `categoryColor()`: `"Finance"`→`"Accounts"` (c[1]); add `"Services"`→c[4].

### UI
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
  - `activeTab` default/comment (line 42): `"Finance"` → `"Accounts"` wording.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
  - Bottom-nav item label + tab routing: `"Finance"` → `"Accounts"` (lines ~200,
    ~251); `onNavigate("Finance")` → `"Accounts"` (lines ~434, ~437, ~712).
  - Dashboard: `financeCount` filter `"Finance"`→`"Accounts"` (line 348); add
    `servicesCount`; rename the "Finance" breakdown row → "Accounts" and add a
    "Services" row; adjust `catColors[...]` indices and `maxCount`/`breakdownTotal`
    (lines ~348–351, ~642–649).
  - Inbox: add `"Services"` to the folders list (line 1105); add Services case to
    `filteredMessages` (914–920); `"Finance"`→`"Accounts"` filter; counts &
    pill-colors (1108–1121).
  - Move-to-category sheet targets (1182–1187): `Finance`→`Accounts`, add `Services`.
  - Rules `listCat` (line 4327): `Finance`→`Accounts`, add `Services`.
  - User-facing lock copy in `FinanceLockedScreen` ("Financial Folder Locked",
    descriptions) and the dashboard finance card subtitles — reword "Finance/
    Financial" → "Accounts" where it's a visible label (lines ~503, ~2796, ~2803,
    ~2864 comment). Internal `isFinance*` names stay.
  - Misc comments mentioning the category list (lines ~395, ~912, ~2054).

### Tests
- `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`
  - Update the finance test to expect `"Accounts"`.
  - Add a test: an OTP / delivery message classifies as `"Services"`.

### Docs
- `docs/architecture.md` — update the category string-literal list (line 33),
  the funnel description (line 23), and the Finance-tab/lock notes (lines 41, 90)
  to reflect Accounts + Services.

## Out of scope / explicitly NOT changing
- `FinanceTx` entity, `finance_transactions` table, `FinanceScreen`,
  `isFinanceLocked`/`isFinanceAuthenticated` identifiers (internal names only).
- The OTP-copy notification pipeline.
- No new bottom-nav tab, no new ledger/screen for Services.

## Risks
- **Missed `== "Finance"` site** → that path silently stops matching. Mitigation:
  after edits, grep the whole `app/src/main` tree for `"Finance"` and confirm only
  intended (internal-name/comment) hits remain.
- **Classifier ordering ambiguity** (e.g. "OTP for txn of Rs.500") — decision #4
  governs this; called out for confirmation.
- **Migration**: data-only `UPDATE`, low risk; `fallbackToDestructiveMigration()`
  remains as the safety net.

## Verification
- `./gradlew testDebugUnitTest` (classifier tests) — see docs/build-and-test.md.
- Manual/Roborazzi: Dashboard breakdown shows 5 rows; Inbox shows Services pill;
  Accounts tab still locks + shows ledger; a seeded OTP lands in Services, a seeded
  bank debit lands in Accounts with a ledger row.
