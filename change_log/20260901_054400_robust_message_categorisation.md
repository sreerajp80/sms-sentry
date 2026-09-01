# Robust Message Categorisation & Field Extraction Engine Enhancement

**Plan Reference:** [plans/20260901_053500_robust_message_categorisation.md](plans/20260901_053500_robust_message_categorisation.md)

## Summary of Changes

Enhanced and hardened the offline SMS categorisation, financial transaction extraction, task reminder parsing, and OTP detection engines across the application to maximize accuracy and robustness against real-world SMS patterns.

### 1. Engine Enhancements (`app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`)
- **Reminder Due-Date Parsing**:
  - Added support for ordinal alpha dates (`25th May 2026`, `1st Jan 2026`, `2nd Oct`, `3rd Nov`) with automatic ordinal suffix stripping.
  - Added support for month-first formats (`May 25, 2026`, `June 16`, `Oct 15 2025`).
  - Added normalization for comma and mixed whitespace delimiters in date strings.
- **Financial Movement Verbs & Balance Disambiguation**:
  - Added common banking transaction verbs (`deducted`, `charged`, `auto-debited`, `withdrawal`, `deposited`, `added to wallet`, `paid via`) and correctly categorized debits vs. credits.
  - Added explicit patterns for abbreviated balance keywords (`Avl Bal`, `Avl. Bal`, `Net Bal`, `Clear Bal`, `Updated Bal`).
  - Broadened currency parsing to support prefix/suffix placements (`500 Rs`, `1000 INR`, `INR. 500`, `INR: 1500`) and international currency symbols (`EUR`, `€`, `GBP`, `£`).
- **Bank & Institutional Header Discovery**:
  - Expanded `knownBanks` and `TRUSTED_ENTITIES` to include major financial institutions: Canara (`CANBNK`, `CANARA`), Union Bank (`UNIONB`), IDFC First (`IDFCFIRST`, `IDFC`), RBL (`RBLBNK`), Federal Bank (`FEDBNK`, `FEDERAL`), Indian Overseas Bank (`IOB`), Central Bank (`CBI`), Paytm Bank (`PAYTM`), Airtel Payments Bank (`AIRTEL`), India Post (`IPPB`), Bandhan Bank (`BANDHAN`), Bank of Baroda (`BARODA`, `BOB`), Standard Chartered (`SCB`, `STANCHAR`).
  - Prioritized clean sender header matching before falling back to message body analysis.
- **Modern Scam & Phishing Detection**:
  - Added patterns for electricity disconnection scams, APK malware download threats, SIM/card block threats, and Telegram/WhatsApp task scams.
  - Expanded URL shortener lists (`wa.me/`, `t.me/`, `bit.do`, `v.gd`, `s.id`, `qr.ae`, `shorturl.at`).

### 2. UI & OTP Detection (`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` & `AndroidManifest.xml`)
- Added regional Malayalam OTP keywords (`ലോഗിൻ`, `ഒ.ടി.പി`, `പാസ്‌വേഡ്`, `രഹസ്യകോഡ്`) to `OTP_KEYWORDS`.
- Added support for Google-style `G-XXXXXX` verification code formats in `detectOtp`.
- Added `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to `AndroidManifest.xml` for complete lint compatibility.

### 3. Unit Tests (`app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt` & `OtpDetectorTest.kt`)
- Added unit tests verifying:
  - Ordinal and month-first alpha date parsing for reminders.
  - Transaction verbs (`deducted`, `charged`, `auto-debited`, `withdrawal`, `deposited`).
  - Abbreviated balance extraction (`Avl Bal`).
  - Bank header recognition across expanded institutions.
  - Modern scam classification (electricity power cut scams, APK phishing, Telegram job scams).
  - Regional Malayalam and Google `G-` format OTP detection.

## Verification
- `./gradlew testDebugUnitTest` passed (all 81 unit tests clean).
- `./gradlew lint` passed with 0 errors.
- `./gradlew assembleDebug` passed.
