# Reminder: parse Unicode-dash dates, re-date stale reminders, tap card to open message

**Status:** completed

## What the issue is

The PUCC reminder SMS still shows the wrong date. The card reads
**"Scheduled For: 30 Jun 2026"** while the message body says the PUCC
**"will expire on 16–Jun–2026"** and the SMS itself arrived on **10 Jun 2026**.

Three distinct problems, only the first of which the prior plan
([20260627_211708](20260627_211708_reminder-date-parse-and-message-anchor.md))
attempted:

### Problem 1 — the date separator is a Unicode dash, not an ASCII hyphen
The body is `16–Jun–2026`, where `–` is an **EN DASH (U+2013)**, not the ASCII
hyphen-minus `-` (U+002D). Government/MoRTH bulk SMS routinely use en/em dashes.
The prior fix widened `dateRegex2`'s separator class to `[\s./-]` and added a
`replace(Regex("[\\s./-]+"), " ")` normalizer in `parseAlphaDate` — but both only
cover ASCII `-`. So `16–Jun–2026` **still does not match** `dateRegex2`, no date
is extracted, and the code falls through to the 3-day fallback.

### Problem 2 — the stale 30-Jun reminder is never re-dated on re-classify
The 30-Jun value is the *old* computation: the message was processed on
27-Jun-2026, the date wasn't parsed, so it fell back to 27 + 3 = **30-Jun**.
Even after the prior fix, `recategorizeAllMessages`
([SmsRepository.kt:418-420](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt#L418))
only inserts a reminder when `message.id !in reminderMessageIds`, i.e. it **skips
messages that already have a reminder** and never updates their `dueDate`. So the
wrong stored 30-Jun card survives every re-categorize — hence "No impact."

### Problem 3 — tapping a reminder card does nothing
`ReminderRowItem` ([SmsOrganizerUi.kt:3937](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L3937))
has no click handler. The user wants tapping the card to open the underlying
message (the same `openMessageById` that finance/ledger rows already use).

Note: date anchoring to the message timestamp (arrival date) was already done in
the prior fix and is correct — the repository passes `referenceTime = message.timestamp`.
This plan keeps that and fixes the parsing + stale-data + navigation gaps.

## Files to change

1. `app/src/main/java/in/sreerajp/sms_sentry/engine/SmsClassifier.kt`
   - Define a shared dash/separator character set that includes ASCII hyphen
     **plus** the common Unicode dashes: en dash `–`, em dash `—`,
     figure dash `‒`, non-breaking hyphen `‑`, and minus sign `−`.
   - Use it in the separator classes of `isoDateRegex`, `dateRegex1`, and
     `dateRegex2`, and in the `parseAlphaDate` normalization regex, so
     `16–Jun–2026`, `16—Jun—2026`, `16-Jun-2026`, `16/Jun/2026`, `16.Jun.2026`
     all match and normalize to `16 Jun 2026`.

2. `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
   - In `recategorizeAllMessages`, when a message **already has** a reminder and
     the classifier still flags it as a reminder with a non-null `dueDate`,
     **update** the existing reminder's `dueDate` (via
     `smsDao.updateReminderDueDate(id, newDueDate)`) when it differs, instead of
     skipping. Load existing reminders once (`getAllRemindersOnce()`) into a
     `messageId -> ReminderSms` map to find the row id. This corrects historic
     mis-dated reminders on the next re-categorize. (New messages without a
     reminder continue to insert as before.)
   - This stays scoped to auto-derived reminders; the body of an auto reminder
     equals the message body. Manually added reminders
     (`addReminderForMessage`) keep their title/body but their `dueDate` will be
     recomputed too — acceptable since recompute uses the message's own date. I
     will flag this tradeoff at review; if undesirable we can gate on
     `reminder.body == message.body`.

3. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
   - Add an `onOpen: () -> Unit` parameter to `ReminderRowItem` and make the
     card `clickable { onOpen() }`.
   - In `RemindersScreen`, pass `onOpen = { viewModel.openMessageById(reminder.messageId) }`.

4. `app/src/test/java/in/sreerajp/sms_sentry/SmsClassifierTest.kt`
   - Add cases proving `16–Jun–2026` (en dash) and `16—Jun—2026` (em dash)
     extract to a 16-Jun due date, alongside the existing ASCII/`.`/`/` cases.

## Plan for the fix

1. Introduce a private `SEP` string constant (the bracketed char class body,
   e.g. `\\s./\\-\\u2011\\u2012\\u2013\\u2014\\u2212`) in `SmsClassifier` and
   build the three date patterns + the `parseAlphaDate` normalizer from it so the
   accepted separators stay in one place.
2. Rework the `recategorizeAllMessages` reminder branch to upsert: insert when no
   existing reminder, else update `dueDate` when it changed.
3. Thread `onOpen` through `RemindersScreen` → `ReminderRowItem` and wire the
   card click to `openMessageById`.
4. Add/extend unit tests; build and run the classifier test suite.

## How to apply the fix to the already-wrong card

The code change alone corrects newly processed messages. To fix the existing
30-Jun card the user must trigger a re-categorize (Problem 2 makes that update
the stored date). I'll confirm the user knows where that action lives, or we can
add a one-time migration if they prefer no manual step.

## Out of scope
- No change to which keywords flag a reminder, nor to finance extraction.
- Re-scheduling exact alarms after a `dueDate` change on re-categorize is not
  included here; the displayed card date is corrected, and alarms re-arm on the
  normal scheduling paths. Can be a follow-up if alarm timing must track the
  corrected date immediately.
