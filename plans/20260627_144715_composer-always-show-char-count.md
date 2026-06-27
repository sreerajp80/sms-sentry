# Always show the character/segment count in the compose window

**Status:** completed

## What the issue is

The compose window's counter only appears once the message spans multiple SMS or nears a part
boundary — it is gated by `SmsSegment.shouldShow(...)`
([SmsOrganizerUi.kt:5876](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L5876)).
The user wants the character count visible **at all times** in the compose window (including for
short, single-part messages, and when the body is empty).

## Plan for the fix

- Add an always-on label helper to **`util/SmsSegment.kt`**:
  `composerLabel(body): String` →
  - empty body → `"0"` (just the count),
  - single part → `"<charCount>"` (e.g. `"45"`),
  - multi-part → `"<charCount> · <parts> SMS"` (e.g. `"190 · 2 SMS"`).
  (Char count is `body.length`; parts come from the existing `info()`.)
- In **`ui/SmsOrganizerUi.kt`** (composer action row, ~L5875), replace the
  `if (SmsSegment.shouldShow(composerSeg)) { Text(label) }` block with an **unconditional** small
  `Text(SmsSegment.composerLabel(smsBodyInput))`, keeping the existing styling
  (`Color(0xFF8C92AC)`, 10sp). A leading character icon / "chars" suffix is optional — kept minimal.

## Scope

- Only the **compose window** (the new-message `ComposeDialog`). The in-thread reply row keeps its
  current "only when it matters" behavior (`shouldShow`), since the request was specifically about
  the compose window. Say so if you'd like the reply row changed too.
- No behavior change to sending/segmentation — display only.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/util/SmsSegment.kt` — add `composerLabel`.
- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt` — always render the composer counter.

## Verification

- Open the compose window: counter shows `0` when empty, the live character count as you type,
  and `<n> · <parts> SMS` once it splits. `./gradlew :app:assembleDebug` succeeds.
