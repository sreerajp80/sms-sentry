# Change log: Rename "Finance" → "Accounts" and add "Services" category

Implements plan
[plans/20260618_093742_accounts-and-services-categories.md](../plans/20260618_093742_accounts-and-services-categories.md).

## Summary

- Renamed the **Finance** message category to **Accounts** (keeps all money/
  transaction/ledger/balance/lock behavior). The category is a stored string, so
  every `== "Finance"` comparison and the persisted rows were updated.
- Added a new 5th category **Services** for non-money transactional/informational
  alerts (OTP, verification, delivery/order/shipment, booking/ticket, recharge).
  Services is a plain category — no ledger, no balance, no lock, no bottom-nav tab.
  It appears as an Inbox filter pill and a Dashboard breakdown row.

## Decisions applied (from the plan)

- Keyword split: `otp` and `recharge` moved out of money into `SERVICES_KEYWORDS`;
  money keeps debit/credit/balance/txn/transfer/payment/bank.
- Classification priority: Spam → Accounts → Reminder → Services → Personal.
- Internal code names left unchanged (`FinanceTx`, `finance_transactions` table,
  `isFinanceLocked`/`isFinanceAuthenticated`, `FinanceScreen`); only user-facing
  strings, the category value, and keyword lists changed.
- Non-destructive Room migration relabels existing data.

## Files changed

- `engine/SmsClassifier.kt` — `FINANCE_KEYWORDS` → `ACCOUNTS_KEYWORDS`; new
  `SERVICES_KEYWORDS`; classify() emits `"Accounts"` and adds a Services branch;
  `runExtractions` money branch keyed on `"Accounts"`; doc comment updated.
- `data/SmsRepository.kt` — 3× supplemental-row guards `"Finance"` → `"Accounts"`;
  seed rules `OTP`/`RECHARGE` retargeted to `Services`; added two seeded Services
  demo messages (OTP + delivery); comments updated.
- `data/SmsEntities.kt` — category-list doc comments updated.
- `data/SmsDatabase.kt` — `version = 4 → 5`; added `MIGRATION_4_5` (data-only
  `UPDATE` of `messages.category` and `filter_rules.targetCategory` Finance→Accounts)
  and registered it.
- `ui/theme/Color.kt` — `CategoryFinance{Light,Dark}` → `CategoryAccounts{Light,Dark}`;
  added `CategoryServices{Light,Dark}` (teal).
- `ui/theme/Theme.kt` — `categoryColors()` now returns 5 colors (Services at index 4);
  `categoryColor()` maps `"Accounts"`→c[1], `"Services"`→c[4], Spam fallback.
- `ui/SmsOrganizerUi.kt` — bottom-nav tab + routing `Finance`→`Accounts`
  (testTag `finance_tab`→`accounts_tab`); dashboard counts (`accountsCount`,
  `servicesCount`), breakdown segments + rows (added Services row); Inbox folder list,
  filters, counts, pill colors (added Services); move-to-category targets; rules
  category chips; lock-screen / card / settings user-facing copy reworded.
- `ui/SmsOrganizerViewModel.kt` — `activeTab` comment updated.
- `test/SmsClassifierTest.kt` — finance test now expects `"Accounts"`; added two
  Services tests (OTP, delivery).
- `test/ExampleRobolectricTest.kt` — OTP fixture message category `Finance`→`Services`.
- `test/screenshots/greeting.png` — regenerated (Roborazzi default record) to reflect
  the renamed tab and new Services breakdown row.
- `docs/architecture.md` — category list, ingestion funnel note, DB version, and the
  Accounts-lock note updated.

## Verification

- `./gradlew.bat :app:testDebugUnitTest` — BUILD SUCCESSFUL; all unit tests pass,
  including the new Services classifier tests. App compiles (only pre-existing
  deprecation warnings).
