# Professional offline spam classifier (weighted scoring + sender trust)

**Status:** completed

## What the issue is

Spam classification produces heavy false positives. Legit messages land in **Spam**:

- NPS statements (`VA-PTNNPS` / `TM-PTNNPS`) — "**Investment** value in Tier I ..."
- SBI banking alerts (`VM-SBIBNK`) — case updates, e-mandate details
- New India insurance (`VM-NIALTD`) — policy expiry / renewal notices

Root cause in [SmsClassifier.kt](app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt):

1. **Substring matching on broad words** — `SPAM_KEYWORDS` is matched with
   `normalizedBody.contains(it)`. `"invest"` is a substring of `"Investment"`, so every NPS /
   mutual-fund statement is flagged. `"win"`, `"won"`, `"claim"` match inside ordinary words too.
2. **One weak word = instant Spam** — a single keyword hit routes straight to Spam with no
   sense of how scam-like the message actually is, and **no notion that the sender is a trusted
   institution** (SBI, NPS, insurer).

## The fix — a weighted, sender-aware scoring engine

Stay fully **offline and deterministic** (no LLM, no network, no bundled model — matches the
app's design). Replace the single-keyword trip with a small scoring engine: sum weighted
signals, and only mark Spam when the score clears a threshold. Every signal is word-boundary /
phrase matched, so substring accidents like `invest` → `investment` cannot happen.

### Decision flow (replaces lines ~181-189 of `SmsClassifier.kt`)

```
score = spamScore(sender, normalizedBody)
if (!allowlisted && score >= SPAM_THRESHOLD) -> Spam (blocked)
else -> continue to existing promo / money / reminder / services / personal logic
```

Custom-contact and custom-keyword rules (steps 1-2 of `classify`) are unchanged and still run
first, so user allow/block rules keep full priority.

### Signals and weights

**Positive (spammy) — push toward Spam:**

| Signal | Weight | Examples |
|---|---|---|
| Scam phrase / whole-word scam term | +3 each | `lottery`, `jackpot`, `casino`, `you won`, `you have won`, `won a prize`, `claim your prize`, `claim your reward`, `free bonus`, `lucky winner`, `guaranteed returns`, `double your money`, `work from home`, `earn money daily`, `loan approved`, `kyc suspended`, `crypto`, `bitcoin` |
| URL shortener / suspicious link | +1 each | `bit.ly`, `tinyurl`, `goo.gl`, `t.co`, `is.gd`, `cutt.ly`, `rb.gy`, `tiny.cc` |
| Urgency phrase | +1 each | `act now`, `hurry`, `last chance`, `expires today`, `don't miss`, `urgent`, `immediately` |

**Negative (legitimate) — pull away from Spam:**

| Signal | Weight | Notes |
|---|---|---|
| Trusted sender (known institution in DLT header) | -5 | banks + NPS/PFRDA + insurers + govt + telecom |
| Transactional / informational context | -2 | money movement, OTP, statement, policy, balance, account |

`SPAM_THRESHOLD = 3`. So one clear scam phrase (+3) with a neutral sender is Spam, while a
trusted institution (-5) is effectively immune unless the body is overwhelmingly scammy.

Note: a URL shortener or urgency word **alone** (+1) never reaches the threshold — important
because legit senders (e.g. NPS uses `gs.im` links, banks say "expires") must not be spam.

### Sender trust

Add a `senderTrust(sender)` helper. Clean the header to letters only (reuse the logic already in
`discoverBank`) and check it against a `TRUSTED_ENTITIES` list. Seed it from the existing bank
list plus the institutions in the screenshots and other common Indian bulk senders:

- Banks: existing `HDFC, SBI, ICICI, AXIS, HSBC, CITI, KOTAK, PNB, BOI, YESBK, INDUS`, + `SBIBNK`.
- NPS / pensions: `PTNNPS`, `NPSCRA`, `PROTEAN`, `NSDL`, `PFRDA`.
- Insurers: `NIALTD`, `NIACL`, `LICIND`, `HDFCLI`, `ICICIP`, `SBILIF`, `MAXLIF`, `BAJAJ`.
- Govt / utility / telecom: `MoRTH`, `ITDCPC`, `UIDAI`, `EPFO`, `IRCTC`, `AIRTEL`, `JIONET`, `VODAFON`, `BSNL`.

Matching is by whole-token containment on the cleaned header (e.g. `VM-SBIBNK` → `VMSBIBNK`
contains `SBIBNK` and `SBI`). A purely numeric sender (10-digit mobile) is **not** trusted — it
is a person, and body signals alone decide.

This list is a starting allowlist; users can still override any sender via the existing
CONTACT → Spam / NotSpam rules.

### Word-boundary / phrase matcher

Add a private helper `containsSignal(body, term)` that matches `term` with a `\b`-anchored,
`Pattern.quote`-escaped, case-insensitive regex (works for both single words and multi-word
phrases). Compile the signal lists into `Pattern`s once (mirrors the existing
`currencyRegex`/`movementRegex` style) so per-message classification stays cheap. Use this
matcher for the spam signals. Promo / money / reminder / services keyword matching stays on the
current `contains` behaviour — this change is scoped to spam to keep risk low.

### Interaction with existing custom-filtering safeguards (must be preserved)

`classify()` already runs user rules **before** any heuristic, and the scoring engine only
replaces the heuristic (step 3). The existing priority order is kept exactly:

1. **Custom CONTACT rules** (`classify` step 1) — unchanged, run first:
   - `Spam`/`Blocked` → return Spam immediately (so a user block still wins even over a
     "trusted" sender — the scoring engine never runs for that sender).
   - `NotSpam` → sets the `allowlisted` flag.
   - other category → force that bucket.
2. **Custom KEYWORD rules** (`classify` step 2) — unchanged.
3. **Scoring engine** (this change) — the offline fallback only.

Guarantees that must not regress:

- The `NotSpam` allowlist keeps full override power: the spam decision stays guarded by
  `!allowlisted`, so `restoreFromSpam()` (which injects `NotSpam`) still guarantees a message
  cannot be re-flagged as Spam.
- `restoreFromSpam()`, `moveMessageToCategory()`, and `recategorizeAllMessages()` all funnel
  through the same `classify()` with the same rule inputs, so they inherit the new engine with
  no changes needed in `SmsRepository.kt`.
- The built-in `TRUSTED_ENTITIES` list is only a **default** safeguard applied inside the
  scoring engine; it is strictly weaker than explicit user CONTACT rules (which short-circuit
  before scoring). A user can still block a trusted sender.

A test will assert a `CONTACT→Spam` rule still spams a trusted sender (user rule beats trust).

### Existing stored messages

The engine only changes new classification and the on-demand `recategorizeAllMessages()` path
in [SmsRepository.kt](app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt). After the
code change the user runs **"Re-categorize all"** from Settings to pull the already-stored NPS /
SBI / insurance messages out of Spam. No migration code needed.

## Files to be changed

- `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
  - Replace `SPAM_KEYWORDS` with curated `SCAM_PHRASES`, add `URL_SHORTENERS`,
    `URGENCY_PHRASES`, `TRUSTED_ENTITIES`, `LEGIT_CONTEXT_KEYWORDS`, `SPAM_THRESHOLD`.
  - Add `spamScore(...)`, `senderTrust(...)`, and `containsSignal(...)` helpers.
  - Swap the old spam block for the score-threshold decision.
- `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`
  - Regression tests (see below).

## Tests

- NPS "Investment value in Tier I (PRANXX7618) as on 31.12.24 is Rs 1,94,742.82 ..." → **not** Spam.
- SBI `VM-SBIBNK` e-mandate / case-update body → **not** Spam.
- Insurance `VM-NIALTD` policy-expiry body → **not** Spam (flags reminder instead).
- Trusted sender that also says a scam-ish word → still **not** Spam (trust dominates).
- Real scam "You won a lottery jackpot! Claim your prize at bit.ly/x, hurry!" → **Spam**.
- Lone URL shortener / lone urgency word from a neutral sender → **not** Spam (below threshold).
- The two existing spam tests (`you won a prize ... claim your reward`) stay **Spam**.

## Not changing

- Custom CONTACT / KEYWORD rule handling and their priority.
- Promo / money / reminder / services classification and finance/reminder extraction.
- DB schema; no migration.
