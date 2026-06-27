# Plan: Read / Unread status for messages

**Status:** completed

## The issue

SMS Sentry has **no concept of read vs unread** anywhere — not in the data model
(`SMSMessage` has no such field), the DAO, the repository, the ViewModel, or the UI.
Every message looks identical regardless of whether the user has ever looked at it.

We want:

- Each message carries a **read/unread** flag.
- New messages (live broadcast, simulate, P2P/CSV/JSON import, demo seed) start **unread**.
- **Opening a message marks it read.** Opening happens in exactly one place:
  `MessageCard.onOpen` → `viewModel.openMessage(msg)` (see
  [SmsOrganizerUi.kt:979](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt#L979)).
- The inbox visually distinguishes unread from read.

## Design decisions

- Add `isRead: Boolean = false` to the `SMSMessage` entity. Default `false` means every
  existing constructor call (including `processAndInsertMessage`, P2P, CSV/JSON import,
  demo seed) produces an **unread** message with no code change — which is the behavior
  we want for newly ingested mail.
- **Schema migration:** the DB is currently `version = 1` with
  `fallbackToDestructiveMigration()`. Adding a column requires bumping to `version = 2`.
  I will add a real `Migration(1, 2)` that runs
  `ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0` so existing
  installs keep their messages (all become "unread", which is a sensible default).
  The destructive fallback stays as a safety net.
- **Mark-read on open** is done in the ViewModel's `openMessage()`, so it covers the one
  and only open path. The detail screen keeps showing the snapshot it was opened with;
  the reactive inbox `Flow` updates the card behind it.
- **Visual treatment** on `MessageCard`: unread = bold/full-strength sender text plus a
  small colored unread dot; read = current (slightly muted) styling. This is the minimal,
  conventional inbox cue and touches only the card composable.

## Files to change

1. **[app/.../data/SmsEntities.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt)**
   - Add `val isRead: Boolean = false` to `SMSMessage`.

2. **[app/.../data/SmsDao.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt)**
   - Add `@Query("UPDATE messages SET isRead = 1 WHERE id = :id") suspend fun markMessageRead(id: Long)`.
   - (Optional, for a future unread badge) `@Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isBlocked = 0") fun getUnreadCount(): Flow<Int>` — included only if we add the dashboard badge below.

3. **[app/.../data/SmsDatabase.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt)**
   - Bump `version = 2`.
   - Define `MIGRATION_1_2` (ALTER TABLE … ADD COLUMN isRead) and add `.addMigrations(MIGRATION_1_2)` to the builder.

4. **[app/.../data/SmsRepository.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt)**
   - Add `suspend fun markAsRead(id: Long) = smsDao.markMessageRead(id)`.

5. **[app/.../ui/SmsOrganizerViewModel.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt)**
   - In `openMessage(msg)`: keep `openedMessage.value = msg`, and additionally
     `viewModelScope.launch { repository.markAsRead(msg.id) }`.

6. **[app/.../ui/SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt)**
   - In `MessageCard`: derive `isUnread = !msg.isRead` and apply the unread visual cue
     (bold sender + unread dot). Read messages keep current styling.

## Scope notes / non-goals

- No change to notifications, P2P payload format, or CSV/JSON export columns — `isRead`
  is a local-only UI state, so import always yields unread (correct).
- The optional dashboard/tab **unread count badge** (DAO query in step 2) is included
  only if you want it; default plan adds the per-card cue and mark-on-open. Let me know
  if you'd like the badge too.

## Verification

- Build the app; confirm the Room migration compiles (no `exportSchema` errors).
- Manually: launch → demo messages appear unread → open one → it shows as read and stays
  read after navigating back.
