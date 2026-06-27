# Fix: Block/Delete swipe panel bleeds through blocked cards (Spam)

**Status:** completed

## Issue

In the Spam section (and any tab showing blocked senders), the behind-the-card
**Block / Delete** swipe-action panel is visible even when the card has **not** been
swiped, and the message text / blocked icon appear painted on top of it (it looks like
the buttons are "overlapped by messages").

### Root cause

Each card is a `Box` with two layers:
- a behind layer (`Row` with `matchParentSize()`) holding the Block/Delete `SwipeAction`s,
- a foreground `Card` (`fillMaxWidth`, slides left via `offset` on swipe) that is meant to
  fully cover the behind layer at rest.

For **blocked** messages the foreground card uses a **semi-transparent** background:

```kotlin
containerColor = if (msg.isBlocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                 else MaterialTheme.colorScheme.surface
```

Because the card fill is only 60% opaque, the behind Block/Delete layer shows *through*
the card at rest. The dimming was intended only to visually mute blocked rows, not to make
them transparent. Spam senders are frequently blocked, so this is most visible in the Spam tab.

Locations:
- [ConversationCard](app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L1141) — line 1141
- [MessageCard](app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L1967) — line 1967

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

## Plan for the fix

Keep the muted/dimmed look for blocked rows but make the card background **opaque** so the
swipe layer can never bleed through. Composite the dim color over `surface`:

```kotlin
containerColor = if (msg.isBlocked)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    else MaterialTheme.colorScheme.surface
```

`compositeOver` (from `androidx.compose.ui.graphics`) blends the 60%-alpha dim color onto the
opaque surface, yielding the same visual dim tone but with full alpha — so the foreground card
is opaque at rest and during the swipe, and the Block/Delete panel is only ever seen in the
gap exposed by the slide.

Apply to both `ConversationCard` (line 1141) and `MessageCard` (line 1967).

### Steps
1. Add `import androidx.compose.ui.graphics.compositeOver` (if not already present).
2. Update the `containerColor` expression in `ConversationCard`.
3. Update the identical `containerColor` expression in `MessageCard`.

## Verification

- Build the app.
- Open the Spam tab: blocked-sender cards appear dimmed but the Block/Delete panel is no
  longer visible until the card is swiped left; swipe-left still reveals it cleanly; tap still
  resets/opens.
