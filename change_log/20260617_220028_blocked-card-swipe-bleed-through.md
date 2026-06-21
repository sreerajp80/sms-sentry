# Change log: Fix Block/Delete swipe panel bleeding through blocked cards

Implements [plans/20260617_215753_blocked-card-swipe-bleed-through.md](../plans/20260617_215753_blocked-card-swipe-bleed-through.md).

## Problem

In the Spam tab, the behind-the-card Block/Delete swipe panel was visible at rest (before
swiping), with the message text/blocked icon appearing painted over it. Cause: blocked-sender
cards used a semi-transparent background (`surfaceVariant.copy(alpha = 0.6f)`), so the foreground
card was only 60% opaque and the behind swipe layer showed through.

## Changes

`app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`:

1. Added import `androidx.compose.ui.graphics.compositeOver`.
2. `ConversationCard` — blocked `containerColor` now composites the dim color over `surface`
   (`surfaceVariant.copy(alpha = 0.6f).compositeOver(surface)`), keeping the muted look but
   making the card opaque.
3. `MessageCard` — same change applied to the identical `containerColor` expression.

## Result

Blocked-sender cards still appear dimmed, but the card is now opaque, so the Block/Delete panel
is only visible in the gap exposed by swiping left — it no longer bleeds through at rest.
