# Conversation date grouping + open-thread scroll-to-latest

## Issue

1. **No date context in the conversation list.** Each `ConversationCard` shows only a
   time (`hh:mm a`), so when scanning the inbox you cannot tell whether a card's last
   message is from today, yesterday, or weeks ago. The user wants the cards **grouped by
   date** using the conversation's **last message timestamp**, with date section headers.
   This must apply to the **All** folder and every other folder (Personal / Promotions /
   Others / Spam) — they all share one render path.

2. **Opening a conversation does not land on the latest message.** `ThreadScreen`'s
   `LazyColumn` (messages sorted oldest→newest) opens scrolled to the **top** (oldest
   message). The user must scroll to the bottom to see the most recent message. Opening a
   thread should land directly on the **last** message.

## Files to change

- `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt`
  - Add import `androidx.compose.foundation.lazy.rememberLazyListState`.
  - **InboxScreen conversation list** (~lines 1274–1318): group the already-sorted
    `conversations` by a date bucket derived from `conv.latest.timestamp`, and emit a date
    header before each group inside the existing `LazyColumn`.
  - **New helpers**: `conversationDateBucket(timestamp): String` (Today / Yesterday /
    `d MMM yyyy`) and a `DateSectionHeader(label)` composable.
  - **ThreadScreen** (~lines 1552–1831): add a `rememberLazyListState`, attach it to the
    chat `LazyColumn`, and add a `LaunchedEffect` that scrolls to the last message so the
    thread opens at the most recent message.

No data-layer, ViewModel, or test-tag changes required.

## Plan for the fix

### 1. Date-bucket helper (pure function)

Add near the `Conversation` data class:

```kotlin
/** Day-granularity section label for the conversation list, from a message timestamp. */
fun conversationDateBucket(timestamp: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(msgCal, today)     -> "Today"
        sameDay(msgCal, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
```

### 2. Date section header composable

A lightweight left-aligned subheader matching the existing muted-label style:

```kotlin
@Composable
private fun DateSectionHeader(label: String) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}
```

### 3. Emit grouped cards in the inbox LazyColumn

`conversations` is already `sortedByDescending { it.latest.timestamp }`, so a `groupBy`
(insertion-order-preserving) on the bucket yields buckets in newest→oldest order with cards
already ordered within each bucket. Replace the flat `items(conversations …)` with a loop:

```kotlin
val grouped = remember(conversations) {
    conversations.groupBy { conversationDateBucket(it.latest.timestamp) }
}
LazyColumn(...) {
    grouped.forEach { (bucket, convs) ->
        item(key = "header_$bucket") { DateSectionHeader(bucket) }
        items(convs, key = { it.sender }) { conv ->
            // existing ConversationCard(...) block unchanged
        }
    }
    item { Spacer(Modifier.height(30.dp)) }
}
```

The per-card `entityText`/`ConversationCard` body is unchanged — only the surrounding
iteration changes. Headers use a distinct key namespace so they never collide with sender
keys. This single path covers All + all category folders.

### 4. Scroll thread to the latest message on open

In `ThreadScreen`, where `threadMessages` is computed:

```kotlin
val listState = rememberLazyListState()
LaunchedEffect(sender, threadMessages.size) {
    if (threadMessages.isNotEmpty()) listState.scrollToItem(threadMessages.lastIndex)
}
```

Pass `state = listState` to the chat `LazyColumn` (~line 1808). `scrollToItem` (not
`animateScrollToItem`) makes the thread open already positioned on the last message.
Keying on `threadMessages.size` also keeps the view pinned to the newest message after a
sent reply or freshly received message — standard chat behavior.

## Risk / notes

- Pure additive UI change; no schema, persistence, or navigation changes.
- Existing `conversation_card_<sender>` test tags are preserved.
- Grouping relies on Kotlin `groupBy` preserving first-encounter order (it does), so the
  pre-existing descending sort is honored without re-sorting.
