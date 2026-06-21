# Change log: Read / Unread status for messages

Implements [plans/20260614_121226_read-unread-status.md](../plans/20260614_121226_read-unread-status.md).
Scope confirmed with user: per-card unread cue (no count badge) + a proper Room migration
(no destructive wipe).

## What changed

1. **[app/.../data/SmsEntities.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsEntities.kt)**
   - Added `val isRead: Boolean = false` to `SMSMessage`. The default keeps every existing
     constructor / ingestion path producing **unread** messages with no other change.

2. **[app/.../data/SmsDao.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDao.kt)**
   - Added `markMessageRead(id: Long)` — `UPDATE messages SET isRead = 1 WHERE id = :id`.

3. **[app/.../data/SmsDatabase.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsDatabase.kt)**
   - Bumped `version` 1 → 2.
   - Added `MIGRATION_1_2` (`ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0`)
     and registered it via `.addMigrations(MIGRATION_1_2)`. Existing installs keep their
     messages; pre-existing rows become unread. `fallbackToDestructiveMigration()` retained
     as a safety net.
   - Added imports `androidx.room.migration.Migration` and `androidx.sqlite.db.SupportSQLiteDatabase`.

4. **[app/.../data/SmsRepository.kt](../app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt)**
   - Added `suspend fun markAsRead(id: Long)` delegating to the new DAO query.

5. **[app/.../ui/SmsOrganizerViewModel.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt)**
   - `openMessage(msg)` now also marks the message read (`repository.markAsRead(msg.id)` in
     `viewModelScope`, only when currently unread). This is the single open path, so it covers
     card taps and any other caller. The detail screen still shows the opened snapshot; the
     reactive inbox `Flow` repaints the card.

6. **[app/.../ui/SmsOrganizerUi.kt](../app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerUi.kt)**
   - `MessageCard`: added `isUnread = !msg.isRead`. Unread cards show a small category-colored
     dot before the sender and a full-strength ExtraBold sender; read cards drop the dot and
     use Medium weight at 65% alpha.

## Notes / not done

- No build run: this environment has no `gradle` and no Gradle wrapper is checked in
  (per docs, builds run from Android Studio / local gradle). Changes are compile-reviewed
  only — recommend a local `gradle :app:assembleDebug` to confirm.
- No changes to notifications, P2P payload, or CSV/JSON export — `isRead` is local-only UI
  state; imported messages correctly arrive unread.
- Manual check to perform: launch → demo messages show unread (dot + bold) → open one →
  it renders as read and stays read after pressing back.
