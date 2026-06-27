# Fix: "Host Sync Server" button label wrapping to a second line

**Status:** completed

## Issue

On the Sync page, the **Host Sync Server** button (left of the two action
buttons) has its label wrap onto a second line, which gets clipped by the fixed
48.dp button height (the screenshot shows "Host Sync" / "Server").

Both buttons share the row width equally (`weight(1f)` each), so the left button
only gets ~half the width. "Host Sync Server" is too long to fit on one line at
that width, while "Connect to Peer" fits.

Location: [app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt:2358](app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L2358)

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

## Plan for the fix

Shorten the label from `"Host Sync Server"` to `"Host Server"` so it parallels
the adjacent `"Connect to Peer"` button and fits on one line at half-row width.
This also matches the host configuration panel title ("Host Configuration").

As a safety net against future truncation, add `maxLines = 1` to both action
buttons' `Text` so neither can silently wrap/clip again.

No behavior change — only the visible label text and a maxLines guard.

## Verification

Visually confirm the button reads on one line (rebuild / screenshot the Sync page).
