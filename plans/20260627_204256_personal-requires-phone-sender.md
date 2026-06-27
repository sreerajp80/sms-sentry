# Personal category requires a phone-number / contact sender

**Status:** completed

## The issue

In the Inbox, alphanumeric DLT sender IDs (e.g. `AD-ITDCPC-S`, `VM-LULUHP-S`,
`BT-SBYONO-S`) are showing up under the **Personal** filter. Personal should only
contain SMS from a saved contact or from a mobile number — never from a business /
short-code header.

Root cause is in
[engine/SmsClassifier.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt).
The heuristic routing ends with a catch-all:

```kotlin
else -> runExtractions("Personal", body, sender)
```

So *any* message that fails to match Spam / money / reminder / promo / services
keywords is labelled Personal, even when the sender is clearly an alphanumeric
header. The income-tax notice from `AD-ITDCPC-S` in the screenshot matches no keyword
bucket, so it falls through to Personal.

## The fix

Personal is fundamentally a property of the **sender**, not the body. A saved contact
is just a phone number that happens to be in the address book, so at classification
time both "from a contact" and "from a mobile number" reduce to "the sender is a
dialable phone number". Anything else (alphanumeric header, short code) is not
Personal.

Change the catch-all so Personal is only assigned when the sender looks like a phone
number; otherwise it falls into **Others**:

```kotlin
else ->
    if (looksLikePhoneNumber(sender)) runExtractions("Personal", body, sender)
    else runExtractions("Others", body, sender)
```

Add a small private `looksLikePhoneNumber(sender)` helper to `SmsClassifier` — same
rule as `ContactNameResolver.isPhoneNumberLike` (digits, allowing `+ - ( ) space`,
needs >= 3 digits). I keep it local rather than calling `ContactNameResolver` so the
classifier stays free of any `android.*` dependency (it currently has none, which keeps
its JVM unit tests fast and Context-free).

Scope notes (deliberately *not* changing):
- Keyword precedence is unchanged. A money / promo / reminder / OTP message still routes
  by keyword first; this only affects the final fallback that was over-assigning Personal.
- Explicit custom-contact and custom-keyword rules (`customContacts` / `customKeywords`)
  and the allowlist path are unchanged — a user rule that targets "Personal" is still
  honoured for any sender.

## Files to change

1. **`app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`**
   - Add private `looksLikePhoneNumber(sender: String): Boolean`.
   - Gate the `else` branch in `classify()` on it (Personal vs Others).

2. **`app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`**
   - The existing test `allowlisted sender with spammy text is not Spam` uses sender
     `"PROMO-XY"` (alphanumeric) and currently expects `"Personal"`. With the fix this
     becomes `"Others"` (the assertion's real intent is "not Spam"). Update its expected
     value accordingly.
   - Add new tests:
     - alphanumeric header with no keywords (e.g. `AD-ITDCPC-S`, plain body) → `Others`.
     - phone-number sender with no keywords (e.g. `+919876543210`, "Amit will call you")
       → `Personal`.

## Verification

- `gradlew testDebugUnitTest` (or the project's documented test command) passes,
  including the new cases.
