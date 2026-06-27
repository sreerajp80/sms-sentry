# Personal category requires a phone-number / contact sender

Implements [plans/20260627_204256_personal-requires-phone-sender.md](../plans/20260627_204256_personal-requires-phone-sender.md).

## Problem

Alphanumeric DLT sender headers (`AD-ITDCPC-S`, `VM-LULUHP-S`, `BT-SBYONO-S`, …) were
appearing under the **Personal** filter. The classifier's heuristic routing ended with a
catch-all `else -> Personal`, so any message that matched no keyword bucket became Personal
regardless of who sent it.

## Changes

- **`app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`**
  - Added private `looksLikePhoneNumber(sender)` helper (digits with optional `+ - ( ) space`,
    needs >= 3 digits — mirrors `ContactNameResolver.isPhoneNumberLike`, kept local so the
    classifier keeps zero `android.*` dependencies).
  - Replaced the `else -> Personal` fallback in `classify()` with sender-gated routing:
    `looksLikePhoneNumber(sender) -> Personal`, else `Others`. A saved contact is just a phone
    number in the address book, so both "from a contact" and "from a mobile number" reduce to a
    phone-number-like sender at classification time.
  - Keyword precedence, custom contact/keyword rules, and the allowlist path are unchanged.

- **`app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`**
  - Updated `allowlisted sender with spammy text is not Spam` (sender `PROMO-XY`): expected
    category is now `Others` (the allowlist only suppresses Spam; an alphanumeric header is not
    Personal). The test's intent — "not Spam" — is preserved.
  - Added `alphanumeric header with no keywords is Others, not Personal` (`AD-ITDCPC-S`).
  - Added `mobile number sender with no keywords is Personal` (`+919876543210`).

## Verification

`./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.SmsClassifierTest"` —
BUILD SUCCESSFUL, all cases pass.
