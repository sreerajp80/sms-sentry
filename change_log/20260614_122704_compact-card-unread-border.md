# Change log: Stronger unread cue + more compact message cards

Implements [plans/20260614_122704_compact-card-unread-border.md](../plans/20260614_122704_compact-card-unread-border.md).
Follow-up to the read/unread feature based on user feedback (read/unread looked too similar;
cards too tall).

## What changed — [SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt) `MessageCard`

- **Unread border** is now `BorderStroke(2.dp, categoryColor)` (thicker + category-colored);
  read cards keep `BorderStroke(1.dp, outline)`. Combined with the existing dot + bold sender,
  unread is now clearly distinct.
- **Compact card:** body `maxLines` 2 → 1; outer `Column` padding 14.dp → 12.dp; spacer above
  body 11.dp → 8.dp; spacer above the entity/OTP chip row 11.dp → 8.dp. More messages fit
  per screen.

## Notes

- No build run (no gradle in this environment) — recommend `gradle :app:assembleDebug` and a
  visual check.
