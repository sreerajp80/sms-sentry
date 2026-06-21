# Plan: Consolidate to four categories (Personal / Promotions / Others / Spam) + on-demand re-categorization

## What the issue is

Today messages are classified into **five** free-form string categories — `Personal`,
`Accounts`, `Reminder`, `Services`, `Spam` — and these overlap/conflict (e.g. a bill SMS is
both "Reminder" and "Accounts"; an offer is both "Services" and "Spam"). We want to collapse
the user-facing set to **four**: `Personal`, `Promotions`, `Others`, `Spam`.

Two features are currently *coupled to the category string* and must keep working:

- **Accounts ledger** (money `FinanceTx` rows + balance) — created only when `category == "Accounts"`,
  shown on the **Accounts** tab, which is gated behind the existing **device-auth lock**
  (`isFinanceLocked` / `isFinanceAuthenticated`).
- **Reminders** (`ReminderSms` rows + due dates) — created only when `category == "Reminder"`,
  shown on the **Reminders** tab.

Since `Accounts`, `Reminder`, and `Services` all fold into `Others`, we must **decouple** the
ledger/reminder row creation from the category string, or those features lose their data.

## Decisions (confirmed with the user)

1. **Four categories only:** `Personal`, `Promotions`, `Others`, `Spam`.
2. **Finance folds into `Others`** but stays *flagged* so it still produces `FinanceTx` rows.
   The **Accounts ledger tab + device-auth lock are preserved unchanged** — that is the
   "security for finance messages". (Finance message *bodies* in the Inbox were never behind
   the lock before; only the ledger/balance view is. We keep that exact behavior.)
3. **Re-categorization is driven by a Settings button**, not an automatic DB migration. When the
   new categories ship, the user taps **"Re-categorize all messages"** in Settings and the app
   re-analyzes every stored message and rewrites its category + supplemental rows.
4. **Remap intent:** `Personal→Personal`, `Spam→Spam`, `Promotions→Promotions` (new), everything
   else (`Accounts`/`Reminder`/`Services` and any unknown legacy value) → `Others`.

## Design decisions needing your sign-off (proposed defaults)

- **D1. Decouple supplemental rows from category.** Add `isFinance: Boolean` and
  `isReminder: Boolean` to `ClassificationResult`, computed from message *content* (the existing
  money/reminder keyword + amount/due-date detection). The repository creates `FinanceTx` when
  `isFinance && amount != null` and `ReminderSms` when `isReminder && dueDate != null`,
  regardless of the (now collapsed) category. This is what keeps the Accounts ledger and
  Reminders tab alive under `Others`.
- **D2. Keep the Accounts and Reminders bottom-nav tabs** as *derived views* (they read
  `FinanceTx` / `ReminderSms`, not a category). The Accounts tab keeps its device-auth lock
  verbatim. Only the *category taxonomy* (Inbox folders, Dashboard breakdown, move-to-category,
  rule targets) shrinks to the four.
- **D3. Classifier priority:** `Spam → Finance(→Others) → Reminder(→Others) → Promotions →
  Services(→Others) → Personal`. Split today's `SPAM_KEYWORDS` into genuine-spam vs a new
  `PROMO_KEYWORDS` (offer/discount/sale/cashback/coupon/% off/buy now/limited time…). Genuine
  spam (lottery/prize/jackpot/win/claim/crypto/casino) stays `Spam`.
- **D4. No schema/DB-version change** (no entity columns change — DB stays `version = 5`).
  Instead of `MIGRATION_5_6`, we add a `legacy → new` category **normalization** so the app
  degrades gracefully before the user taps the button, and the button does the real work:
  - `SmsClassifier` normalizes any custom-rule `targetCategory` that is a legacy value
    (`Accounts`/`Reminder`/`Services`) to `Others`, so re-running with old seeded rules is safe.
  - The re-categorize action also rewrites `filter_rules.targetCategory` legacy→new.
  - **Trade-off:** until the button is tapped, legacy messages keep their old category string and
    only appear under the Inbox "All" folder (not under a named folder). Acceptable per your
    "use the setting to recategorize now" instruction. *Alternative if you prefer:* auto-run the
    re-categorization once on first launch after upgrade. **Recommend: manual button only.**

## Files to be changed

1. **`app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`**
   - `ClassificationResult`: doc comment → 4 categories; add `isFinance`, `isReminder` flags.
   - Replace `SPAM_KEYWORDS` content split → keep genuine-spam list + new `PROMO_KEYWORDS`.
   - Rename `ACCOUNTS_KEYWORDS`→ keep as money-detection list (drives `isFinance`),
     `REMINDER_KEYWORDS` drives `isReminder`, `SERVICES_KEYWORDS` now routes to `Others`.
   - `classify()`: new routing (D3); custom rule/contact categories normalized via a new
     `normalizeCategory()`; emit `Personal/Promotions/Others/Spam` only.
   - `runExtractions()`: compute `isFinance`/`isReminder` from content and run amount/balance/
     due-date extraction independent of the category string.
   - Add `fun normalizeCategory(raw: String): String` (legacy→new, used by classifier + repo).

2. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`**
   - `processAndInsertMessage` step 4, `restoreFromSpam`, `moveMessageToCategory`: gate
     `FinanceTx`/`ReminderSms` creation on `classification.isFinance`/`isReminder` (not `== "Accounts"`/`"Reminder"`).
   - `seedDemoData()`: retarget seed rules + seed messages to the new four (OTP/RECHARGE/PAYMENT
     rules → `Others`; WINNER/GIFT → `Spam`; add a `Promotions` seed message). Keep finance &
     reminder seed messages so the ledger/reminders demo still populates (now via flags).
   - **New:** `suspend fun recategorizeAllMessages(): Int` — load all messages + current rules,
     re-run `SmsClassifier.classify`, `updateMessageCategory`, rebuild `FinanceTx`/`ReminderSms`
     (delete-then-insert by `messageId`), and rewrite legacy `filter_rules.targetCategory`→new.
     Returns count processed.

3. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt`**
   - Update `SMSMessage.category` and `FilterRule.targetCategory` doc comments to the four.

4. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt`**
   - No version bump / no new migration (per D4). Add a short comment noting categories are now
     re-derived on demand via the Settings action, not a migration.

5. **`app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt`**
   - Add any one-shot query needed by `recategorizeAllMessages` if not already present
     (likely none — `allMessages.first()` + existing update/delete/insert suffice). Verify and
     add `getAllRulesOnce()`-style helper only if required.

6. **`app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Color.kt`**
   - Keep `CategoryPersonal*` and `CategorySpam*`. Repurpose/add `CategoryPromotions*` (amber)
     and `CategoryOthers*` (neutral slate/teal — reuse Accounts/Services hues). Remove now-unused
     names or alias them.

7. **`app/src/main/java/in/sreerajp/sms_sentry/ui/theme/Theme.kt`**
   - `categoryColors()` → 4 colors (Personal, Promotions, Others, Spam).
   - `categoryColor(category)` → map the four; legacy strings fall through to `Others` color.

8. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`**
   - Inbox: `folders` list, per-folder filter, counts, pill colors, and the move-to-category
     label→stored-string map → the four.
   - Dashboard: replace `accountsCount`/`reminderCount`/`servicesCount` with `promotionsCount`/
     `othersCount`; update breakdown segments + `BreakdownRow`s.
   - Bottom nav: **keep** the `Accounts` and `Reminders` tabs + routing (D2) — they are ledger/
     reminder views, not categories.
   - Rules dialog category chips (`listOf("Spam","Accounts",...)` and default `ruleCategory`) →
     `listOf("Personal","Promotions","Others","Spam")`.
   - **Settings:** add a "Categorization" card with a **"Re-categorize all messages"** button
     that calls the new ViewModel function and toasts the processed count (placed near the
     Backup/DB-integrity card, styled like the existing buttons).

9. **`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`**
   - Add `fun recategorizeAllMessages(onDone: (Int) -> Unit = {})` → `viewModelScope.launch`
     calling `repository.recategorizeAllMessages()`.
   - Update `moveConversationToCategory` doc + `activeTab` comment to the new taxonomy.

10. **`app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`**
    - Money test → expect `Others` + `isFinance == true`; OTP/delivery tests → `Others`;
      add a `Promotions` test (e.g. "Flat 50% OFF sale, shop now").

11. **`app/src/test/java/in/sreerajp/sms_sentry/ExampleRobolectricTest.kt`**
    - Fixture `category = "Services"` → `"Others"`.

12. **`docs/architecture.md`**
    - Update the category list, the ingestion-funnel note (supplemental rows keyed on flags, not
      category), the on-demand re-categorization action, and the Accounts-lock note.

## Verification

- `./gradlew.bat :app:testDebugUnitTest` — all unit tests pass (updated + new Promotions test).
- App compiles (only pre-existing deprecation warnings).
- Manual: tap Settings ▸ Re-categorize → legacy messages move into the four folders; Accounts
  ledger still populates and stays behind the device-auth lock; Reminders tab still populates.

## Out of scope / notes

- P2P sync re-classifies on import (already routes through `processAndInsertMessage`) — no extra
  change beyond the classifier update.
- Screenshot test (`greeting.png`) may need a Roborazzi re-record after the Dashboard breakdown
  changes; will record if the verify run flags a mismatch.
