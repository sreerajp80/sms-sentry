# Professional offline spam classifier (weighted scoring + sender trust)

Implements plan
[plans/20260720_220000_professional-spam-classifier.md](../plans/20260720_220000_professional-spam-classifier.md).
(Supersedes the dropped narrow plan
[20260720_215354_spam-classification-false-positives.md](../plans/20260720_215354_spam-classification-false-positives.md).)

## Problem

Legitimate messages were classified as **Spam**: NPS statements ("**Investment** value in
Tier I ..."), SBI banking alerts, and New India insurance renewals. Root cause: the old spam
check matched a list of broad single words with a plain substring `contains`, so `"invest"`
matched `"Investment"`, and a single weak keyword sent a message straight to Spam with no sense
of how scam-like it was or whether the sender was a trusted institution.

## What changed

### `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`

- Removed `SPAM_KEYWORDS` and the `isProbableSpamSender` "promo"-header path.
- Added a fully offline, deterministic **weighted scoring engine**:
  - Signal lists: `SCAM_PHRASES` (curated scam phrases/whole words, +3 each), `URL_SHORTENERS`
    (+1), `URGENCY_PHRASES` (+1), `TRUSTED_ENTITIES` (banks / NPS / insurers / govt / telecom,
    -5), `LEGIT_CONTEXT_KEYWORDS` (money / statement / policy / OTP context, -2).
  - `spamScore(sender, body)` sums these signals; `SPAM_THRESHOLD = 3`.
  - Scam/urgency signals are matched with `\b`-anchored, `Pattern.quote`-escaped, pre-compiled
    patterns (`boundaryPattern`), so a substring like `invest` inside `investment` can never
    trip the classifier.
  - `isTrustedSender(sender)` matches the letters-only DLT header against `TRUSTED_ENTITIES`; a
    dialable phone number is treated as a person, never an institution.
- The spam decision is now `if (!allowlisted && spamScore(...) >= SPAM_THRESHOLD) -> Spam`.

The change is scoped to the spam decision. Custom CONTACT/KEYWORD rules still run first and keep
full priority, the `NotSpam` allowlist still fully overrides (the `!allowlisted` guard is kept),
and promo / money / reminder / services classification and finance/reminder extraction are
unchanged. No changes were needed in `SmsRepository.kt`: `restoreFromSpam`,
`moveMessageToCategory`, and `recategorizeAllMessages` all funnel through the same `classify()`.

### `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`

Added 7 regression tests: NPS statement / SBI e-mandate / insurance renewal are not Spam
(insurance still flags a reminder); a real prize scam with link + urgency is Spam; a trusted
sender is not spammed by a single scam-ish word; a lone shortened link is below threshold; and a
user `CONTACT→Spam` rule still spams a trusted sender (user rule beats built-in trust).

## Verification

`./gradlew.bat :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.SmsClassifierTest"` —
**BUILD SUCCESSFUL**, all tests pass (the two pre-existing spam tests remain green).

## Follow-up for the user

Existing messages already stored as Spam were classified by the old logic. Run
**"Re-categorize all"** in Settings (wired to `recategorizeAllMessages()`) to pull the
already-stored NPS / SBI / insurance messages out of Spam.
