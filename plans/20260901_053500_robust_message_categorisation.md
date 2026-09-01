# Robust Message Categorisation & Field Extraction Engine Enhancement

**Status:** Proposed

## Overview
This change analyzes and hardens the offline message categorisation, financial transaction extraction, task reminder parsing, and OTP detection engines across `SmsClassifier.kt`, `SmsOrganizerUi.kt`, and related test suites to maximize classification accuracy and edge-case robustness.

## Identified Issues & Areas of Improvement

1. **Task Reminder & Date Parsing Gaps (`SmsClassifier.kt`)**:
   - **Ordinal Suffixes**: Alpha dates containing ordinals (e.g., `25th May`, `1st Jan 2026`, `2nd Oct`, `3rd Nov`) fail date matching due to `st`/`nd`/`rd`/`th` suffixes.
   - **Month-First Formats**: Formats like `May 25, 2026`, `Jun 16`, `Oct 15 2025` are not parsed because existing alpha regex expects `\d{1,2}` day first.
   - **Punctuation & Delimiters**: Commas and mixed whitespace in dates (e.g., `May 25, 2026`) need normalization.

2. **Financial Movement Verbs & Balance Disambiguation (`SmsClassifier.kt`)**:
   - **Missing Transaction Verbs**: Real-world alerts frequently use terms like `deducted`, `charged`, `auto-debited`, `withdrawal`, `deposited`, `added to wallet`, `paid via UPI`.
   - **Abbreviated Balance Keywords**: `Avl Bal`, `Avl. Bal`, `Net Bal`, `Clear Bal`, `Updated Bal` should be explicitly matched in balance detection and trusted legit context.
   - **Currency Formatting**: Support `INR.`, `INR:`, `Rs:`, `Amt: Rs`, trailing currency tokens (`500 Rs`, `1000 INR`), and international symbols (`EUR`, `€`, `GBP`, `£`).

3. **Bank & Institutional Header Coverage (`SmsClassifier.kt`)**:
   - Expand `knownBanks` and `TRUSTED_ENTITIES` to include major financial institutions: Canara (`CANBNK`, `CANARA`), Union Bank (`UNIONB`), IDFC First (`IDFC`), RBL (`RBLBNK`), Federal Bank (`FEDERAL`, `FEDBNK`), Indian Overseas Bank (`IOB`), Central Bank (`CBI`), Paytm Bank (`PAYTM`), Airtel Payments Bank (`AIRTEL`), India Post (`IPPB`), Bandhan Bank (`BANDHAN`), Bank of Baroda (`BOB`, `BARODA`), Standard Chartered (`SCB`, `STANCHAR`).

4. **Modern Scam & Phishing Pattern Detection (`SmsClassifier.kt`)**:
   - Add modern scam signals: electricity/power disconnection threats (`electricity will be disconnected`, `power will be disconnected`), APK malware downloads (`.apk`, `download apk`), SIM/card/identity block threats (`pan card blocked`, `sim blocked`, `aadhaar suspended`), Telegram/WhatsApp task scams (`telegram task`, `daily income`, `whatsapp job`, `contact on whatsapp`, `wa.me/`, `t.me/`).
   - Add modern URL shorteners: `wa.me/`, `t.me/`, `bit.do`, `v.gd`, `s.id`, `qr.ae`, `shorturl.at`.

5. **Multi-Lingual OTP Detection Alignment (`SmsOrganizerUi.kt`)**:
   - Add regional Malayalam OTP keywords (`ലോഗിൻ`, `ഒ.ടി.പി`, `പാസ്‌വേഡ്`, `രഹസ്യകോഡ്`) to `OTP_KEYWORDS` in `SmsOrganizerUi.kt` to align with the documented feature specification and testing sandbox.
   - Support `G-XXXXXX` formatted Google verification codes.

## Proposed Changes

### 1. `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
- Expand `SCAM_PHRASES`, `URL_SHORTENERS`, `URGENCY_PHRASES`, `TRUSTED_ENTITIES`, `LEGIT_CONTEXT_KEYWORDS`.
- Expand `MONEY_KEYWORDS`, `movementRegex`, `balanceKeywordRegex`, `currencyRegex`.
- Enhance date parsing (`dateRegex2`, `parseAlphaDate`) to support ordinal suffixes (`st`, `nd`, `rd`, `th`), month-first formats (`MMM dd yyyy`, `MMM d yyyy`, `MMM dd`, `MMM d`), and comma separators.
- Expand `knownBanks` in `discoverBank`.

### 2. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
- Add Malayalam OTP keywords (`ലോഗിൻ`, `ഒ.ടി.പി`, `പാസ്‌വേഡ്`, `രഹസ്യകോഡ്`) and support alphanumeric prefix patterns like `G-` in `detectOtp`.

### 3. `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt` & `OtpDetectorTest.kt`
- Add comprehensive unit tests covering:
  - Ordinal and month-first alpha date parsing (`25th May 2026`, `May 25, 2026`, `1st Jan`).
  - New finance transaction verbs (`deducted`, `charged`, `withdrawal`, `auto-debited`).
  - Abbreviated balance extraction (`Avl Bal Rs. 5000`).
  - Bank recognition across expanded institutions (Canara, Federal, IDFC, BOB, Union).
  - Modern scam patterns (electricity disconnection scam, APK download scam, Telegram job scam).
  - Malayalam and `G-XXXXXX` OTP detection.

## Verification Plan

### Automated Tests
- Run `./gradlew testDebugUnitTest` to execute all unit tests.
- Run `./gradlew lint` to ensure zero Android lint errors.
