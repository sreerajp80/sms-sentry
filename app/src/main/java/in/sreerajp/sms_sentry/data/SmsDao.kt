package `in`.sreerajp.sms_sentry.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    // --- Messages ---
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<SMSMessage>>

    @Query("SELECT * FROM messages WHERE isBlocked = 0 ORDER BY timestamp DESC")
    fun getInboxMessages(): Flow<List<SMSMessage>>

    @Query("SELECT * FROM messages WHERE category = :category AND isBlocked = 0 ORDER BY timestamp DESC")
    fun getMessagesByCategory(category: String): Flow<List<SMSMessage>>

    @Query("SELECT * FROM messages WHERE isBlocked = 1 ORDER BY timestamp DESC")
    fun getSpamMessages(): Flow<List<SMSMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SMSMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<SMSMessage>)

    @Delete
    suspend fun deleteMessage(message: SMSMessage)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): SMSMessage?

    /** Id of an existing message with the exact same sender/body/timestamp, used to skip duplicate inserts. */
    @Query("SELECT id FROM messages WHERE sender = :sender AND body = :body AND timestamp = :timestamp ORDER BY id LIMIT 1")
    suspend fun findMessageId(sender: String, body: String, timestamp: Long): Long?

    /** One-shot read of every message, used by the Settings "Re-categorize all messages" action. */
    @Query("SELECT * FROM messages")
    suspend fun getAllMessagesOnce(): List<SMSMessage>

    /** System-provider row ids already imported, used to dedupe re-imports. */
    @Query("SELECT systemId FROM messages WHERE systemId IS NOT NULL AND isMms = :isMms")
    suspend fun getImportedSystemIds(isMms: Boolean): List<Long>

    /** Update the delivery status of a sent message (see SMSMessage.STATUS_*). */
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    @Query("DELETE FROM finance_transactions WHERE messageId = :id")
    suspend fun deleteTransactionByMessageId(id: Long)

    @Query("DELETE FROM reminders WHERE messageId = :id")
    suspend fun deleteReminderByMessageId(id: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("UPDATE messages SET category = :category WHERE id = :id")
    suspend fun updateMessageCategory(id: Long, category: String)

    @Query("UPDATE messages SET isBlocked = :isBlocked WHERE id = :id")
    suspend fun updateMessageBlockedState(id: Long, isBlocked: Boolean)

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id")
    suspend fun markMessageRead(id: Long)

    // --- Filter Rules ---
    @Query("SELECT * FROM filter_rules")
    fun getAllRules(): Flow<List<FilterRule>>

    /** One-shot read of every rule (used by the bulk re-categorization action). */
    @Query("SELECT * FROM filter_rules")
    suspend fun getAllRulesOnce(): List<FilterRule>

    @Query("UPDATE filter_rules SET targetCategory = :category WHERE id = :id")
    suspend fun updateRuleCategory(id: Long, category: String)

    /** Message ids that already have a reminder, so bulk re-categorization keeps manual ones. */
    @Query("SELECT messageId FROM reminders")
    suspend fun getReminderMessageIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FilterRule): Long

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("SELECT * FROM filter_rules WHERE type = 'KEYWORD'")
    suspend fun getKeywordRules(): List<FilterRule>

    @Query("SELECT * FROM filter_rules WHERE type = 'CONTACT'")
    suspend fun getContactRules(): List<FilterRule>

    // --- Finance Transactions ---
    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<FinanceTx>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceTx): Long

    @Query("DELETE FROM finance_transactions")
    suspend fun deleteAllTransactions()

    // --- Reminders ---
    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    fun getAllReminders(): Flow<List<ReminderSms>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderSms): Long

    @Query("UPDATE reminders SET isSyncedToCalendar = :isSynced WHERE id = :id")
    suspend fun updateReminderSyncState(id: Long, isSynced: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    // --- Scheduled (future-delivery) messages ---
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTime ASC")
    fun getAllScheduled(): Flow<List<ScheduledSms>>

    /** One-shot read used by the boot receiver to re-arm alarms after a reboot. */
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTime ASC")
    suspend fun getAllScheduledOnce(): List<ScheduledSms>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    suspend fun getScheduledById(id: Long): ScheduledSms?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduled(scheduled: ScheduledSms): Long

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteScheduledById(id: Long)
}
