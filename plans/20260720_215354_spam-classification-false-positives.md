# Fix wrong SPAM classification (false positives on legit messages)

**Status:** dropped

> Superseded by
> [20260720_220000_professional-spam-classifier.md](20260720_220000_professional-spam-classifier.md).
> The user asked for a full professional classifier (weighted scoring + sender trust) rather
> than the minimal keyword fix described below.

## What the issue is

The user sees legitimate messages wrongly placed in **Spam**:

- NPS statements from `VA-PTNNPS` / `TM-PTNNPS` — "**Investment** value in Tier I ..."
- SBI banking alerts from `VM-SBIBNK` — case updates, e-mandate details
- New India insurance from `VM-NIALTD` — policy expiry / renewal notices

Root cause is in [app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt](app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt):

1. **Substring matching on very broad words.** `SPAM_KEYWORDS` is matched with a plain
   `normalizedBody.contains(it)`. The word `"invest"` is a substring of `"Investment"`, so every
   NPS / mutual-fund statement that says "Investment value ..." is flagged as Spam. Other bare
   words in the list (`"win"`, `"won"`, `"claim"`) also match inside ordinary words
   (`winning`, `windows`, `reclaim`, insurance `claims`, etc.).

2. **A single weak word is enough to spam.** One keyword hit sends the message straight to Spam,
   with no requirement that it read like an actual scam. Generic finance words like `"invest"`
   therefore nuke real bank/investment messages.

The SBI and insurance threads do not hit a spam keyword under the *current* code, so they were
most likely classified as Spam by an **older** version of the classifier and are still stored
that way. They will only be corrected when messages are re-classified (see step 4 below).

## The fix

Make the spam heuristic match *scam phrases with word boundaries* instead of loose substrings,
and stop bare finance words from ever triggering spam.

### 1. `SmsClassifier.kt` — spam keyword list

Replace the broad single-word `SPAM_KEYWORDS` with phrase / whole-word scam indicators:

- Remove `"invest"` entirely (legit investment statements must never be spam).
- Keep genuinely scammy whole words: `"lottery"`, `"jackpot"`, `"casino"`, `"crypto"`,
  `"bitcoin"`, `"prize"`.
- Convert the ambiguous words into phrases: `"you won"`, `"you have won"`, `"won a prize"`,
  `"claim your prize"`, `"claim your reward"`, `"free bonus"`, `"congratulations you"`,
  `"account has been unlocked"`, etc.

### 2. `SmsClassifier.kt` — word-boundary matching

Add a small helper that matches a keyword either as a whole word (for single words like
`lottery`, `bitcoin`) or as a literal phrase, using a `\b`-anchored regex, instead of raw
`String.contains`. Use it for the spam check so `"invest"`-style substring hits can never happen
again. (Promo/money/reminder keyword matching is left as-is; this change is scoped to spam to
keep the fix small and low-risk.)

### 3. Keep the existing spam behaviour working

The two existing spam tests use bodies like `"CONGRATULATIONS! You won a prize, claim your
reward now!"` and `"... You won a lottery jackpot, claim it now!"`. The new phrase list
(`"you won"`, `"won a prize"`, `"claim your reward"`, `"lottery"`, `"jackpot"`,
`"congratulations you"`) still catches these, so those tests stay green.

### 4. Existing mis-stored messages

The classifier fix only changes *future* classification and the on-demand
`recategorizeAllMessages()` path. After the code fix, the user re-runs **"Re-categorize all"**
from Settings (already wired to `recategorizeAllMessages()` in
[SmsRepository.kt](app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt)) to move the
already-stored NPS / SBI / insurance messages out of Spam. No data migration code is needed.

### 5. Tests

Add regression tests in
[app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt](app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt):

- NPS "Investment value in Tier I (PRANXX7618) as on 31.12.24 is Rs 1,94,742.82 ..." → **not** Spam.
- SBI e-mandate / case-update body → **not** Spam.
- Insurance policy-expiry / renewal body → **not** Spam (should flag reminder instead).
- Keep an explicit assertion that a real "you won a lottery, claim your prize" body **is** Spam.

## Files to be changed

- `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt` — new spam keyword list +
  word-boundary/phrase matcher for the spam check.
- `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt` — regression tests.

## Not changing

- Promo / money / reminder keyword handling and finance/reminder extraction.
- The `isProbableSpamSender` + `"promo"` path (unaffected by these messages).
- No DB schema or migration changes.
