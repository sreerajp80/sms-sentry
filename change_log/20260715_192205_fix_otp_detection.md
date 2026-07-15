# Change Log - Fix OTP Detection False Positives

This change log documents the changes made to resolve incorrect OTP detection and false positives in promotional messages.

## Plan Referenced
- Plan: [20260715_191717_fix_otp_detection.md](file:///l:/Android/sms-sentry/plans/20260715_191717_fix_otp_detection.md)

## Changes Implemented
1. **Added Local JVM Unit Tests:**
   - Created [OtpDetectorTest.kt](file:///l:/Android/sms-sentry/app/src/test/java/in/sreerajp/sms_sentry/OtpDetectorTest.kt) containing test cases for standard OTP messages, user-reported false positives (promotional messages containing quantities like "5000 GB"), and incorrect selections (messages containing masked card/account suffix alongside the actual OTP).
2. **Refined OTP Detection Logic:**
   - Updated `detectOtp` in [SmsOrganizerUi.kt](file:///l:/Android/sms-sentry/app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt) to:
     - Find all candidate 4-8 digit numeric sequences.
     - Filter out candidates preceded by account/card mask characters (`X`, `x`, `*`, `·`, `•`), identifier prefixes (`UAN`, `a/c`, `card`, `OTP-ID`, `Ref`), or currency symbols (`Rs.`, `₹`).
     - Filter out candidates followed by data, time, currency, or count units (`GB`, `MB`, `mAh`, `am`, `pm`, etc.).
     - Rank/score multiple valid candidates based on proximity to the nearest OTP keyword in the message body.
3. **Unified OTP Detection:**
   - Refactored `SmsNotificationHelper.kt` to call the shared `in.sreerajp.sms_sentry.ui.detectOtp` helper, removing duplicate custom regex logic.

## Verification Results
- Ran [OtpDetectorTest](file:///l:/Android/sms-sentry/app/src/test/java/in/sreerajp/sms_sentry/OtpDetectorTest.kt) successfully: all assertions passed.
- Ran [SmsClassifierTest](file:///l:/Android/sms-sentry/app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt) successfully: existing classification tests still pass.
