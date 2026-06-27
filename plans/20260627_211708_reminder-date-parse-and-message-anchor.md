# Reminder date: parse hyphenated alpha dates + anchor to message date

**Status:** completed

## What the issue is

A PUCC reminder SMS — "…will expire on **16-Jun-2026**. Kindly renew…" — was
scheduled for **30-Jun-2026** instead of 16-Jun-2026. Two distinct defects:

### Defect 1 — hyphenated alpha-month dates are not parsed
In [SmsClassifier.kt](../app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt)
the three date extractors are:
- `isoDateRegex` — `yyyy-MM-dd` (year first). No match.
- `dateRegex1` — all-numeric `dd-mm-yyyy`. "Jun" isn't numeric → no match.
- `dateRegex2` — alpha month, but pattern is `\d{1,2}\s+(?:Jan|…)…\s*\d{0,4}`,
  which requires **whitespace** between the day and the month. `16-Jun-2026`
  uses **hyphens**, so it does not match.

With no date extracted, `candidates` is empty and the code falls back to
"3 days from now" (`SmsClassifier.kt:301`). The message was processed on
27-Jun-2026, so 27 + 3 = **30-Jun-2026** — exactly the wrong card date.

### Defect 2 — date logic is anchored to "now", not the message date
`runExtractions` uses `System.currentTimeMillis()` for:
- the future/past selection of candidate dates (`it >= now`, `SmsClassifier.kt:290-292`),
- the `tomorrow`/`today`/3-day fallback (`SmsClassifier.kt:295-303`),
- and `parseAlphaDate` defaults a year-less alpha date to the **current** year
  (`SmsClassifier.kt:429`).

For a freshly-received SMS "now" ≈ message time, so it's usually fine. But for
imported / P2P-synced / re-classified / demo-seeded messages, "now" can be far
from when the message was actually sent, which skews both the fallback and the
year guess. The reminder date should be reckoned **relative to the message's own
timestamp**, which is available at every call site.

## Files to change

1. `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
   - Widen `dateRegex2` so the day/month/year separators accept space, hyphen,
     dot, or slash (e.g. `16-Jun-2026`, `16.Jun.2026`, `16/Jun/2026`, `16 Jun 2026`).
   - Add hyphen/dot/slash alpha formats (`dd-MMM-yyyy`, `dd-MMM`, `d-MMM-yyyy`,
     etc.) to `parseAlphaDate` so the widened matches actually parse. Normalize
     the separators to spaces before parsing to keep the format list small.
   - Add a `referenceTime: Long` parameter to `classify(...)` (default
     `System.currentTimeMillis()` for callers/tests that don't supply one) and
     thread it through `runExtractions(...)` and `parseAlphaDate(...)`.
   - Use `referenceTime` instead of `System.currentTimeMillis()` for: the
     future/past candidate selection, the tomorrow/today/3-day fallback, and the
     year-less alpha-date year default.

2. `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
   - Pass the message timestamp as `referenceTime` at all four `classify` call
     sites: `processAndInsertMessage` (`timestamp`), `restoreFromSpam`,
     `moveMessageToCategory`, and the reclassify loop (`message.timestamp`).

3. `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`
   - Add cases: `16-Jun-2026` (and `16.Jun.2026` / `16/Jun/2026`) extract to the
     correct due date; a year-less alpha date resolves against `referenceTime`'s
     year; the 3-day fallback is measured from `referenceTime`, not wall-clock.

## Plan for the fix

1. Replace the literal `\s+` / `\s*` in `dateRegex2` with a separator class
   `[\s./-]+` / `[\s./-]*` so hyphen/dot/slash-delimited alpha dates match.
2. In `parseAlphaDate`, replace any `[-./]` in the captured string with a space
   and parse against the existing space-delimited format list (keeps the year
   default + 1970 fixup logic unchanged), but resolve the default year from the
   passed `referenceTime` rather than `Calendar.getInstance()`.
3. Add `referenceTime` param to `classify`/`runExtractions`; swap every
   `System.currentTimeMillis()` in the reminder block for it.
4. Update the four repository call sites to pass the message timestamp.
5. Add/extend unit tests; build and run the classifier test suite.

## Out of scope
- No change to which keywords flag a reminder, nor to finance extraction.
- Existing stored reminders are not retroactively re-dated here; a user can
  re-classify to recompute. (Can be a follow-up if desired.)
