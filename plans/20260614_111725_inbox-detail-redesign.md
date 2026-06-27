# Inbox & Message-Detail redesign — Direction A ("Secure Cards")

**Status:** completed

## Source of truth

`SMSSentry.zip` (Claude Design export, copied to `.design_ref/`). The lead design is
**Direction A · Secure Cards** in `SMS Sentry Inbox.dc.html`, because it is the only one that
matches the required interaction model:

- **Swipe left** on a card → reveal **Block** + **Delete** actions.
- **Tap** a card → open the **Message detail** screen.

The same file also defines the **Message detail** screen ("Direction A, in depth") and the
theme-settings screen. The 5 colour schemes (Lavender/High-Density/Sage/Cosmic-Slate/Slate-Blue)
with light+dark variants are **already implemented** in `ui/theme/Color.kt` + `Theme.kt` — they were
derived from this same design pack. So this task is about **layout/interaction**, not new colours.

## What the issue is

The current Inbox (`InboxScreen` / `MessageCard` in `SmsOrganizerUi.kt`) renders every message as a
fully-expanded card with the body text and Block/Delete/Copy-OTP buttons inline. There is **no swipe
gesture**, **no message-detail screen**, and **tapping a card does nothing**. The visual hierarchy,
spacing, avatar/badge treatment, filter tabs, and detail view do not match the new design.

## Decisions confirmed with the user

1. **Detail "Sender" menu** → implement **all four**: Mute notifications, Block sender, Report as
   spam, Delete conversation.
2. **Detail "Detected in this message" smart card** → show it with **both** buttons (Add reminder +
   Mark as paid) functional.
3. **Copy OTP** (existing feature, not in mockup) → **keep on the inbox card and in the detail screen**.
4. **Verified/Unknown trust badges + unread dots** → **omit both** (no data backing, no DB migration).

## Files to change

### Data / persistence
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt`
  - Add `@Query("UPDATE messages SET category=:category, isBlocked=:isBlocked WHERE id=:id")`
    helper is not needed — existing `updateMessageCategory` + `updateMessageBlockedState` suffice.
  - (No schema change. `isRead`/`verified` are **not** added per decision 4.)
- `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
  - Add `reportSpam(message)` = `updateMessageCategory(id,"Spam")` + `updateMessageBlockedState(id,true)`.
  - Add `addReminderFor(message, title, body, dueDate)` that inserts a `ReminderSms` (so the detail
    "Add reminder" works even for non-Reminder messages).

### ViewModel
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
  - **Navigation:** add `openedMessage = mutableStateOf<SMSMessage?>(null)` (+ `openMessage`/`closeMessage`)
    to drive the detail screen as an overlay on top of the tab content (no Navigation-Compose; consistent
    with the existing `activeTab` string-switch pattern).
  - **Mute:** persist a `Set<String>` of muted senders in the existing `theme_prefs`.
    Expose `mutedSenders` state + `toggleMute(sender)` / `isMuted(sender)`.
  - **Mark as paid:** persist a `Set<Long>` of paid message-ids in `theme_prefs`.
    Expose `paidMessageIds` state + `togglePaid(id)` / `isPaid(id)`.
  - **Report as spam:** `reportSpam(msg)` → repo call above + `addRule("CONTACT", msg.sender, "Spam")`.
  - **Add reminder:** `addReminderForMessage(msg, dueDate)` → repo insert, then reuse the existing
    `addEventToCalendar` path if the user taps through.
  - **Block** keeps the existing semantics (`addRule CONTACT→Spam` + `deleteMessage`), matching today's
    Inbox behaviour.

### Notifications
- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsNotificationHelper.kt`
  - At the top of `showNotification(...)`, read the muted-senders set from `theme_prefs` and **return
    early** (suppress) if `sender` is muted.

### UI (the bulk of the work) — `ui/SmsOrganizerUi.kt`
- **`InboxScreen`**
  - Keep the existing filter logic (All/Personal/Finance/Reminders/Spam) but restyle the filter tabs as
    pill chips with count badges, matching the design's `tabsA`.
  - Add the "Tap a card to reveal Block & Delete" hint row from the design (swipe hint).
- **`MessageCard` → restyle as the Direction-A "Secure Card":**
  - Compact card: rounded 20dp, avatar tile with category-tinted background + sender initial, sender
    name, time (right-aligned), SIM pill + category pill, **single-line preview** of the body
    (`maxLines = 2`, ellipsis), and a parsed-entity chip (amount/due-date) pulled from the linked
    `FinanceTx` / `ReminderSms` when present.
  - **Swipe-to-reveal:** wrap the card in a `Box` with a behind-layer of **Block** + **Delete** buttons
    (right-aligned, as in the design); the foreground card has a draggable horizontal offset
    (`anchoredDraggable`/`offset` + `pointerInput` drag) that snaps open (~ -132dp) on swipe-left and
    closed on swipe-right/tap-elsewhere.
  - **Tap** the card → `viewModel.openMessage(msg)`.
  - **Copy OTP:** if an OTP is detected, keep a small Copy-OTP chip on the card (decision 3).
- **New `MessageDetailScreen(viewModel, msg)`** (modelled on the design's "Message detail"):
  - Header: back button, category-tinted avatar, sender, "SIM n", 3-dot overflow.
  - Encryption banner ("End-to-end encrypted · stored only on this device").
  - Date divider + the message body shown as a left-aligned bubble (single real message — the mockup's
    second "earlier" bubble is mock data and is omitted).
  - **"Detected in this message" smart card** when the message has a linked `FinanceTx` (amount) and/or
    `ReminderSms` (due date): two stat tiles + **Add reminder** and **Mark as paid** buttons
    (Mark-as-paid toggles the persisted paid flag and shows a checked state).
  - **Copy OTP** action when an OTP is present.
  - **SENDER** action list: **Mute notifications** (toggle), **Block sender**, **Report as spam**,
    **Delete conversation** (red). Block/Report/Delete close the detail and return to the Inbox.
- **`SmsOrganizerApp`**
  - Render `MessageDetailScreen` as a full-screen overlay (inside the existing `Box`) when
    `viewModel.openedMessage != null`, with a back handler (`BackHandler`) that calls `closeMessage()`.

### Tests / screenshots
- Existing Roborazzi screenshot baselines for the Inbox/cards will change. After implementation, run the
  screenshot tests and **record** new baselines (documented in the change log; not auto-recorded without
  the user's go-ahead).

## Out of scope (explicitly)
- No changes to Dashboard/Finance/Reminders/Sync/Settings layouts beyond what's needed for the overlay.
- No DB migration (no `isRead`/`verified` columns).
- No new colour schemes (theme palette already matches the design).

## Risk / notes
- `SmsOrganizerUi.kt` is a single ~3.3k-line file; changes are localized to `InboxScreen`/`MessageCard`
  and the new detail composable + the `SmsOrganizerApp` overlay hook.
- Swipe gesture uses Compose Foundation (already a dependency); no new libraries.
- Muted-sender suppression is best-effort (reads SharedPreferences synchronously in the notification
  helper, same prefs file the VM writes).
