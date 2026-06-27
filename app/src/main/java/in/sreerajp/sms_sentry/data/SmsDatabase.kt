package `in`.sreerajp.sms_sentry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SMSMessage::class,
        FilterRule::class,
        FinanceTx::class,
        ReminderSms::class,
        ScheduledSms::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SmsDatabase : RoomDatabase() {
    abstract val smsDao: SmsDao

    companion object {
        @Volatile
        private var INSTANCE: SmsDatabase? = null

        // v1 -> v2: add read/unread flag to messages (existing rows default to unread).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v2 -> v3: add system-provider sync + MMS + delivery-status columns to messages.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN systemId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN threadId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN type INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE messages ADD COLUMN isMms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentUri TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v3 -> v4: add the scheduled_messages table for future-delivery SMS.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS scheduled_messages (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "recipient TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "simId INTEGER NOT NULL, " +
                        "scheduledTime INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        // The four-category consolidation (Personal/Promotions/Others/Spam) needs no schema or
        // data migration: categories are free-form strings and are re-derived on demand via the
        // Settings "Re-categorize all messages" action (repository.recategorizeAllMessages()).
        // Legacy values (Accounts/Reminder/Services) are normalized by SmsClassifier until then.

        // v4 -> v5: rename the "Finance" category to "Accounts" (money/ledger stays the same).
        // The new "Services" category needs no schema/data change — it only appears on newly
        // classified messages. Data-only relabel of existing messages and filter rules.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE messages SET category = 'Accounts' WHERE category = 'Finance'")
                db.execSQL("UPDATE filter_rules SET targetCategory = 'Accounts' WHERE targetCategory = 'Finance'")
            }
        }

        // v5 -> v6: add recurrence + in-app alert columns to reminders, for AlarmManager-driven
        // due alerts and repeating reminders. Existing reminders default to one-shot, alert-on.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN alertEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): SmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    "sms_organizer_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
