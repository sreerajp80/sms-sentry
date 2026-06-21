# Change log: Enable long-press multi-select in inbox search results

Implements plan
[plans/20260620_212835_search-result-longpress-select.md](../plans/20260620_212835_search-result-longpress-select.md).

## Problem

When a search was active in the inbox, search-result cards could not be long-pressed to
select, and the contextual selection toolbar never appeared. The normal (non-search) list
supported this, but search rendered the read-only `SearchResultCard`, which had no
long-press / selection wiring, so `selectionMode` was never set from the search view.

## Changes

All in `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`:

1. **`SearchResultCard` is now selectable.**
   - Added `@OptIn(ExperimentalFoundationApi::class)`.
   - New parameters: `selected: Boolean`, `selectionMode: Boolean`, `onLongClick: () -> Unit`.
   - Swapped `Modifier.clickable` for `Modifier.combinedClickable(onClick, onLongClick)`.
   - Added selection visuals mirroring `ConversationCard`: primary-tinted background and
     2.dp primary border when selected, and a leading `CheckCircle` /
     `RadioButtonUnchecked` icon in the header row while in selection mode.
   - Updated the KDoc (no longer "read-only"; documents the sender-level selection
     semantics).

2. **Wired the search-results call site** into the existing sender-based selection state:
   `selected = msg.sender in selectedSenders`, `selectionMode`, and `onClick`/`onLongClick`
   that call `toggle(msg.sender)` / enter `selectionMode` — matching the `ConversationCard`
   behavior. The existing contextual toolbar (gated on `selectionMode`) now appears
   automatically when selecting from search.

3. **Fixed the selection-pruning effect** (previously keyed on `filteredMessages`) to
   validate selected senders against `searchSource` instead. `searchSource` is a superset
   of `filteredMessages` in every folder, so existing behavior is preserved, but a Spam
   sender selected from an "All" search is no longer pruned immediately.

## Behavioral notes

- Selection/actions remain sender-level while search results are message-level, so
  selecting one result selects that sender's whole conversation; multiple result rows from
  the same sender all show as selected.
- While selecting, the search field is replaced by the contextual toolbar (same as the
  normal list); the query is preserved when selection is cleared.

## Verification

- `./gradlew :app:compileDebugKotlin` succeeds (exit 0).
- Manual UI verification of long-press selection in search results and Spam-sender
  selection persistence is recommended but not yet performed.
