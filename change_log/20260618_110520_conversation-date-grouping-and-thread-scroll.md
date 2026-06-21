# Conversation date grouping + open-thread scroll-to-latest

Implements plan `plans/20260618_105150_conversation-date-grouping-and-thread-scroll.md`
(approved 2026-06-18).

## What changed

All changes in `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`.

1. **Added import** `androidx.compose.foundation.lazy.rememberLazyListState`.

2. **Date-grouped conversation list (InboxScreen).** The inbox `LazyColumn` previously
   rendered a flat `items(conversations)`. It now groups the (already newest-first)
   conversations into day buckets keyed by each conversation's last-message timestamp and
   emits a `DateSectionHeader` before each bucket. Because this is the single shared render
   path, grouping applies to the **All** folder and every category folder (Personal /
   Promotions / Others / Spam). Header items use a `"header_<bucket>"` key namespace so they
   never collide with sender keys; the `ConversationCard` block and `conversation_card_<sender>`
   test tags are unchanged.

3. **New helpers:**
   - `conversationDateBucket(timestamp: Long): String` — returns `Today`, `Yesterday`, or a
     `d MMM yyyy` date label.
   - `DateSectionHeader(label)` — left-aligned muted subheader composable.

4. **Thread opens on the latest message (ThreadScreen).** Added a `rememberLazyListState`
   wired into the chat `LazyColumn` plus a `LaunchedEffect(sender, threadMessages.size)`
   that `scrollToItem(threadMessages.lastIndex)`. Opening a conversation now lands directly
   on the most recent message, and the view re-pins to the latest after a sent reply or a
   freshly received message.

## Verification

- `./gradlew :app:compileDebugKotlin` succeeds (only Gradle native-access warnings; no
  Kotlin errors).

## Notes / scope

- Pure additive UI change — no data-layer, ViewModel, navigation, or schema changes.
- Date label format confirmed with the user as `Today / Yesterday / d MMM yyyy`.
