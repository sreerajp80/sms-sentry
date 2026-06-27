# Reminder date: parse hyphenated alpha dates + anchor to message date

Implements [plans/20260627_211708_reminder-date-parse-and-message-anchor.md](../plans/20260627_211708_reminder-date-parse-and-message-anchor.md).

## Problem

A PUCC reminder SMS ("…will expire on **16-Jun-2026**. Kindly renew…") was scheduled
for **30-Jun-2026** (3 days after the processing date) instead of the actual 16-Jun-2026:

1. The alpha-month date extractor (`dateRegex2`) only matched **space-separated** dates
   (`16 Jun 2026`), so the hyphenated `16-Jun-2026` was never parsed. With no date found,
   the reminder fell back to "3 days from now".
2. All reminder date logic was anchored to `System.currentTimeMillis()` (wall-clock at
   processing time) rather than the message's own timestamp, skewing the future/past
   selection, the year-less alpha-date year, and the fallback for imported / P2P-synced /
   re-classified / seeded messages.

## Changes

### `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
- `dateRegex2`: separators between day/month/year now accept space, hyphen, dot, or slash
  (`[\s./-]+` / `[\s./-]*`), so `16-Jun-2026`, `16.Jun.2026`, `16/Jun/2026`, `16 Jun 2026`
  all match.
- `parseAlphaDate(dateStr, referenceTime)`: normalizes any `[\s./-]+` separators to single
  spaces before parsing against the existing format list, and resolves the year-less default
  year from `referenceTime` instead of the current calendar year.
- `classify(...)` gained a `referenceTime: Long = System.currentTimeMillis()` parameter,
  threaded through `runExtractions(...)` and into `parseAlphaDate(...)`. The reminder block
  now uses `referenceTime` for the future/past candidate selection and the
  tomorrow/today/3-day fallback.

### `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
- All four `SmsClassifier.classify(...)` call sites now pass the message timestamp as
  `referenceTime`: `processAndInsertMessage` (`timestamp`), `restoreFromSpam`,
  `moveMessageToCategory`, and the reclassify loop (`message.timestamp`).

### `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`
- Added tests: hyphenated `16-Jun-2026` parses to the correct due date; dot/slash/space
  separators parse identically; a year-less alpha date resolves to the message's year;
  the 3-day fallback is measured from `referenceTime`.

## Verification
- `./gradlew :app:testDebugUnitTest --tests "in.sreerajp.sms_sentry.SmsClassifierTest"` — BUILD SUCCESSFUL.

## Notes / scope
- Fixes apply to messages classified going forward. Reminders already stored from earlier
  SMS are not retroactively re-dated; a user can re-classify the message to recompute. A
  one-time backfill could be a follow-up if desired.
