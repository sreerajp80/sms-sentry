# Plan: Stronger unread cue + more compact message cards

**Status:** completed

## The issue

After adding read/unread status, the visual difference between read and unread cards is too
subtle, and each `MessageCard` is tall (shows up to 2 body lines), so few messages fit on
screen.

## The fix (single file: SmsOrganizerUi.kt → MessageCard)

1. **Thicker, clearer unread border.** Make the card `border` conditional:
   - unread → `BorderStroke(2.dp, categoryColor)` (thicker + category-colored).
   - read → `BorderStroke(1.dp, outline)` (current look).

2. **Reduce card height.**
   - Body `maxLines` 2 → 1.
   - Tighten spacing: outer `Column` padding 14.dp → 12.dp; spacer above body 11.dp → 8.dp;
     spacer above entity/OTP chip row 11.dp → 8.dp.

## Verification

- Recommend a local `gradle :app:assembleDebug` (no gradle in this env).
- Manual: unread cards show a noticeably thicker colored border; bodies truncate to one
  line; more cards fit per screen.
