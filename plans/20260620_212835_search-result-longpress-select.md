# Plan: Enable long-press multi-select in inbox search results

## Issue

In the inbox, the normal (non-search) conversation list supports long-press to enter
multi-select mode, which shows the contextual selection toolbar (mark-read / move /
delete / block / mute). When a **search is active**, none of this works:

- Long-pressing a search result does nothing.
- The selection toolbar never appears.

### Root cause

When `searchActive` is true, the list renders `SearchResultCard`
(`SmsOrganizerUi.kt:1358-1363`) instead of `ConversationCard`. `SearchResultCard`
(`SmsOrganizerUi.kt:1510`) is intentionally **read-only**: it uses a plain
`Modifier.clickable(onClick = ...)` with no `onLongClick` and no wiring to the
selection state. Since nothing ever sets `selectionMode = true` from the search view,
the contextual toolbar (gated on `selectionMode` at `SmsOrganizerUi.kt:988`) is never
shown.

### Secondary issue (must fix together)

The selection model is **per-sender** (`selectedSenders`, `toggle(sender)`), and the
pruning effect at `SmsOrganizerUi.kt:977` drops any selected sender not present in
`filteredMessages`. For the "All" folder, `filteredMessages = inboxMessages` (no Spam),
but "All" search also searches Spam (`searchSource`, line 943-945). So a spam sender
selected from an "All" search would be pruned immediately and the selection would vanish.

## Design notes

- Search results are **message-level** (one card per matching message); selection and
  all toolbar actions are **sender-level**. Long-pressing a search result therefore
  selects that message's **sender** (the whole conversation). If several result rows
  share a sender, they will all show as selected — this is consistent with the existing
  sender-based action model and is the only coherent behavior given the toolbar actions.
- No new toolbar code is needed: the existing contextual toolbar already renders
  whenever `selectionMode` is true, and it sits above the search/results area in the
  same `Column`. Entering selection from search will show it automatically; the search
  field is replaced by the toolbar while selecting (same as the normal list) and the
  query is preserved when selection is cleared.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`

## Plan for the fix

1. **Make `SearchResultCard` selectable** (`SmsOrganizerUi.kt:1509-1559`):
   - Annotate with `@OptIn(ExperimentalFoundationApi::class)`.
   - Add parameters `selected: Boolean`, `selectionMode: Boolean`, and
     `onLongClick: () -> Unit`.
   - Replace `Modifier.clickable(onClick = onClick)` with
     `Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)`.
   - Mirror `ConversationCard`'s selection visuals:
     - Selected background tint + 2.dp primary border when `selected`.
     - Leading `CheckCircle` / `RadioButtonUnchecked` icon in the header row when
       `selectionMode` is true.

2. **Wire the call site** (`SmsOrganizerUi.kt:1358-1363`):
   - `selected = msg.sender in selectedSenders`
   - `selectionMode = selectionMode`
   - `onClick = { if (selectionMode) toggle(msg.sender) else viewModel.openThread(msg.sender) }`
   - `onLongClick = { if (!selectionMode) selectionMode = true; toggle(msg.sender) }`

3. **Fix the pruning effect** (`SmsOrganizerUi.kt:977-982`):
   - Validate selected senders against `searchSource` instead of `filteredMessages`
     (and key the `LaunchedEffect` on `searchSource`). `searchSource` is a superset of
     `filteredMessages` in every folder, so this preserves existing behavior while no
     longer pruning spam senders selected from an "All" search.

## Testing / verification

- Build the app (per docs/build-and-test.md).
- Manual: with a search active, long-press a result → selection mode + toolbar appear;
  tapping other results toggles them; back / close clears selection and restores the
  search field with the query intact.
- Manual: in "All" folder, search and select a Spam-folder sender → selection persists
  (no longer pruned).
- Existing test tags (`conversation_card_*`, `search_result_*`) are unchanged; consider
  whether an instrumented/Robolectric test for search-result selection is warranted.
