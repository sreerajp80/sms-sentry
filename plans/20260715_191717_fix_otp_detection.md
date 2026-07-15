# Fix OTP Detection False Positives
**Status:** completed

## Files to be Changed
1. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` (Modifying `detectOtp` implementation)
2. `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt` (Using the modified `detectOtp` function instead of duplicating logic)
3. `app/src/test/java/in/sreerajp/sms_sentry/OtpDetectorTest.kt` [NEW] (Adding unit tests for OTP detection logic)

## Issue
1. **False Positive OTP in Promotional Messages:** When promotional messages contain an OTP keyword (like "code") and a 4-digit numeric value (like "5000 GB"), the app identifies the number as an OTP.
2. **Incorrect OTP Extraction:** In transactional SMS containing masked card/account suffixes or OTP-IDs (e.g. UAN ending in `8184` or `OTP-ID 8817`), the app identifies the first 4-8 digit number (`8184`) as the OTP instead of the actual verification code (`469279`).

## Plan for the Fix
1. **Create a local JVM unit test (`OtpDetectorTest.kt`):** This test file will run standard JUnit tests for `detectOtp` using various edge case SMS messages (including the user's examples) without using Robolectric (so it avoids the Android SDK 36 Robolectric failure).
2. **Refine `detectOtp` logic in `SmsOrganizerUi.kt`:**
   - Find all candidate 4-8 digit numeric sequences using `\b\d{4,8}\b`.
   - Filter out candidate sequences that:
     - Are preceded by account/card masking patterns (e.g. `XXXX `, `** ` or `Ending in `).
     - Are preceded by identifiers like `ID`, `Ref`, `Txn`.
     - Are preceded by currency symbols (e.g. `Rs.`, `₹`).
     - Are followed by data, time, currency, or count units (e.g. `GB`, `MB`, `mAh`, `Rs`, `PM`, `AM`, `days`).
   - If multiple candidates remain, score/rank them by proximity to the nearest OTP keyword in the message body.
   - Return the highest-ranking candidate, or `null` if none remain.
3. **Reference `detectOtp` in `SmsNotificationHelper.kt`:** Replace the duplicated OTP detection block in `SmsNotificationHelper.kt` with a call to `in.sreerajp.sms_sentry.ui.detectOtp` to ensure consistency.

## Verification Plan
### Automated Tests
- Run the newly created `in.sreerajp.sms_sentry.OtpDetectorTest` class using:
  `./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.OtpDetectorTest"`
- Verify that standard unit tests in `SmsClassifierTest` still pass:
  `./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.SmsClassifierTest"`
