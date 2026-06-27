# Change log: Always show character count in the compose window

Implements [plans/20260627_144715_composer-always-show-char-count.md](../plans/20260627_144715_composer-always-show-char-count.md).

## What changed

- **`util/SmsSegment.kt`** — added `composerLabel(body)`: returns the raw character count (e.g.
  `"45"`, `"0"` when empty) and appends segment info once the message splits (`"190 · 2 SMS"`).
- **`ui/SmsOrganizerUi.kt`** — the new-message composer's counter now renders **unconditionally**
  (via `SmsSegment.composerLabel(smsBodyInput)`) instead of only appearing when
  `SmsSegment.shouldShow(...)` was true. Styling unchanged (`Color(0xFF8C92AC)`, 10sp).

## Scope

- Only the compose window. The in-thread reply row keeps its "only when it matters" counter.
- Display-only; no change to sending/segmentation.

## Verification

- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` — BUILD SUCCESSFUL.
