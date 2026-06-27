package `in`.sreerajp.sms_sentry.data

import `in`.sreerajp.sms_sentry.engine.SmsClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*

class SmsRepository(private val smsDao: SmsDao) {

    // Streams
    val allMessages: Flow<List<SMSMessage>> = smsDao.getAllMessages()
    val inboxMessages: Flow<List<SMSMessage>> = smsDao.getInboxMessages()
    val spamMessages: Flow<List<SMSMessage>> = smsDao.getSpamMessages()
    val rules: Flow<List<FilterRule>> = smsDao.getAllRules()
    val transactions: Flow<List<FinanceTx>> = smsDao.getAllTransactions()
    val reminders: Flow<List<ReminderSms>> = smsDao.getAllReminders()
    val scheduledMessages: Flow<List<ScheduledSms>> = smsDao.getAllScheduled()

    fun getMessagesByCategory(category: String): Flow<List<SMSMessage>> {
        return smsDao.getMessagesByCategory(category)
    }

    // --- Scheduled (future-delivery) messages ---
    suspend fun insertScheduled(scheduled: ScheduledSms): Long = smsDao.insertScheduled(scheduled)
    suspend fun getScheduledById(id: Long): ScheduledSms? = smsDao.getScheduledById(id)
    suspend fun getAllScheduledOnce(): List<ScheduledSms> = smsDao.getAllScheduledOnce()
    suspend fun deleteScheduledById(id: Long) = smsDao.deleteScheduledById(id)

    // Insert customized message matching Rules
    suspend fun processAndInsertMessage(
        sender: String,
        body: String,
        timestamp: Long,
        simId: Int,
        systemId: Long? = null,
        threadId: Long? = null,
        type: Int = SMSMessage.TYPE_INBOX,
        isRead: Boolean = false,
        isMms: Boolean = false,
        attachmentUri: String? = null,
        status: Int = SMSMessage.STATUS_NONE
    ): Long {
        // 0. De-dup: an identical message (same sender/body/timestamp) is already stored — reuse it
        //    instead of inserting a second copy (and a second FinanceTx/ReminderSms). This sits in
        //    the one funnel, so every source (broadcast, deliver, import, P2P, seed) is covered.
        smsDao.findMessageId(sender, body, timestamp)?.let { return it }

        // 1. Load active DB custom rules in-memory for classification
        val allRules = smsDao.getAllRules().first()
        val customKeywords = allRules.filter { it.type == "KEYWORD" }.map { it.value to it.targetCategory }
        val customContacts = allRules.filter { it.type == "CONTACT" }.associate { it.value to it.targetCategory }

        // 2. Classify Message
        val classification = SmsClassifier.classify(
            sender = sender,
            body = body,
            customKeywords = customKeywords,
            customContacts = customContacts,
            referenceTime = timestamp
        )

        // 3. Create & insert SMSMessage
        val sms = SMSMessage(
            sender = sender,
            body = body,
            timestamp = timestamp,
            category = classification.category,
            simId = simId,
            isBlocked = classification.isBlocked,
            isRead = isRead,
            systemId = systemId,
            threadId = threadId,
            type = type,
            isMms = isMms,
            attachmentUri = attachmentUri,
            status = status
        )
        val msgId = smsDao.insertMessage(sms)

        // 4. Create supplemental entries based on content flags (independent of the now-collapsed
        //    category): a finance/reminder message lives under "Others" but still feeds the ledger.
        if (classification.isFinance && classification.amount != null) {
            val tx = FinanceTx(
                messageId = msgId,
                bankName = classification.bankName ?: "Unknown Bk",
                amount = classification.amount,
                isCredit = classification.isCredit ?: false,
                balance = classification.balance ?: 0.0,
                timestamp = timestamp
            )
            smsDao.insertTransaction(tx)
        }

        if (classification.isReminder && classification.dueDate != null) {
            val reminder = ReminderSms(
                messageId = msgId,
                sender = sender,
                title = classification.reminderTitle ?: "SMS Reminder",
                body = body,
                dueDate = classification.dueDate,
                isSyncedToCalendar = false
            )
            smsDao.insertReminder(reminder)
        }

        return msgId
    }

    // --- Rules Management ---
    suspend fun addRule(type: String, value: String, targetCategory: String): Long {
        val rule = FilterRule(type = type, value = value, targetCategory = targetCategory)
        return smsDao.insertRule(rule)
    }

    suspend fun deleteRule(id: Long) {
        smsDao.deleteRuleById(id)
    }

    // --- System SMS/MMS import ---
    /** A message read from the system provider, ready to be merged into Room. */
    data class ImportRow(
        val systemId: Long,
        val sender: String,
        val body: String,
        val timestamp: Long,
        val type: Int,
        val isRead: Boolean,
        val threadId: Long?,
        val isMms: Boolean,
        val attachmentUri: String?
    )

    /**
     * Merge messages read from the system SMS/MMS providers into Room, skipping any whose
     * [ImportRow.systemId] was already imported. Returns the number of new rows inserted.
     */
    suspend fun importMessages(rows: List<ImportRow>): Int {
        if (rows.isEmpty()) return 0
        val existingSms = smsDao.getImportedSystemIds(isMms = false).toHashSet()
        val existingMms = smsDao.getImportedSystemIds(isMms = true).toHashSet()
        var inserted = 0
        for (row in rows) {
            val seen = if (row.isMms) existingMms else existingSms
            if (row.systemId in seen) continue
            processAndInsertMessage(
                sender = row.sender,
                body = row.body,
                timestamp = row.timestamp,
                simId = 1,
                systemId = row.systemId,
                threadId = row.threadId,
                type = row.type,
                isRead = row.isRead,
                isMms = row.isMms,
                attachmentUri = row.attachmentUri,
                status = if (row.type == SMSMessage.TYPE_SENT) SMSMessage.STATUS_SENT else SMSMessage.STATUS_NONE
            )
            seen.add(row.systemId)
            inserted++
        }
        return inserted
    }

    // --- Message Management ---
    suspend fun deleteMessage(message: SMSMessage) {
        smsDao.deleteMessage(message)
        // Clean up any supplemental finance/reminder rows derived from this message.
        smsDao.deleteTransactionByMessageId(message.id)
        smsDao.deleteReminderByMessageId(message.id)
    }

    suspend fun updateMessageCategory(id: Long, category: String) {
        smsDao.updateMessageCategory(id, category)
    }

    suspend fun updateMessageBlockedState(id: Long, isBlocked: Boolean) {
        smsDao.updateMessageBlockedState(id, isBlocked)
    }

    /** Mark a message as read (called when the user opens it). */
    suspend fun markAsRead(id: Long) {
        smsDao.markMessageRead(id)
    }

    /** Mark a message as unread (manual override from the thread/inbox selection actions). */
    suspend fun markAsUnread(id: Long) {
        smsDao.markMessageUnread(id)
    }

    /** Look up the source SMS for a finance/reminder row by its messageId. */
    suspend fun getMessageById(id: Long): SMSMessage? = smsDao.getMessageById(id)

    /** Update an outgoing message's delivery status (see SMSMessage.STATUS_*). */
    suspend fun updateMessageStatus(id: Long, status: Int) {
        smsDao.updateStatus(id, status)
    }

    /** Mark a message as spam: move it to the Spam folder and flag it blocked. */
    suspend fun reportSpam(message: SMSMessage) {
        smsDao.updateMessageCategory(message.id, "Spam")
        smsDao.updateMessageBlockedState(message.id, true)
    }

    /**
     * Move a message out of Spam: re-classify it (treating the sender as allowlisted so spammy
     * keywords can't re-flag it), update its category, unblock it, and rebuild any finance /
     * reminder rows the reclassification produces. Reverse of [reportSpam].
     */
    suspend fun restoreFromSpam(message: SMSMessage) {
        val allRules = smsDao.getAllRules().first()
        val customKeywords = allRules.filter { it.type == "KEYWORD" }.map { it.value to it.targetCategory }
        // Drop any Spam/Blocked contact rule so it doesn't re-spam, then force this sender allowlisted.
        val customContacts = allRules
            .filter { it.type == "CONTACT" && it.targetCategory != "Spam" && it.targetCategory != "Blocked" }
            .associate { it.value to it.targetCategory }
            .toMutableMap()
        customContacts[message.sender] = "NotSpam"

        val classification = SmsClassifier.classify(
            sender = message.sender,
            body = message.body,
            customKeywords = customKeywords,
            customContacts = customContacts,
            referenceTime = message.timestamp
        )

        smsDao.updateMessageCategory(message.id, classification.category)
        smsDao.updateMessageBlockedState(message.id, false)

        // Rebuild supplemental rows (mirrors processAndInsertMessage step 4); clear first to avoid dupes.
        smsDao.deleteTransactionByMessageId(message.id)
        smsDao.deleteReminderByMessageId(message.id)
        if (classification.isFinance && classification.amount != null) {
            smsDao.insertTransaction(
                FinanceTx(
                    messageId = message.id,
                    bankName = classification.bankName ?: "Unknown Bk",
                    amount = classification.amount,
                    isCredit = classification.isCredit ?: false,
                    balance = classification.balance ?: 0.0,
                    timestamp = message.timestamp
                )
            )
        }
        if (classification.isReminder && classification.dueDate != null) {
            smsDao.insertReminder(
                ReminderSms(
                    messageId = message.id,
                    sender = message.sender,
                    title = classification.reminderTitle ?: "SMS Reminder",
                    body = message.body,
                    dueDate = classification.dueDate,
                    isSyncedToCalendar = false
                )
            )
        }
    }

    /**
     * Manually move a message into an explicit category (user override of the classifier).
     *
     * - Spam → delegate to [reportSpam] (category=Spam, blocked=true); the caller is expected to
     *   also add a CONTACT->Spam rule if it wants the sender blocked going forward.
     * - Otherwise force-classify into [targetCategory] (via a CONTACT rule for the sender, the
     *   same trick [restoreFromSpam] uses) so we still extract amount/balance (finance) or
     *   due-date (reminder) from the body, update the category, unblock, and rebuild the
     *   supplemental finance/reminder rows. Mirrors step 4 of `processAndInsertMessage`.
     */
    suspend fun moveMessageToCategory(message: SMSMessage, targetCategory: String) {
        if (targetCategory == "Spam") {
            reportSpam(message)
            // Drop any stale supplemental rows from a previous Accounts/Reminder classification.
            smsDao.deleteTransactionByMessageId(message.id)
            smsDao.deleteReminderByMessageId(message.id)
            return
        }

        val classification = SmsClassifier.classify(
            sender = message.sender,
            body = message.body,
            customKeywords = emptyList(),
            customContacts = mapOf(message.sender to targetCategory),
            referenceTime = message.timestamp
        )

        smsDao.updateMessageCategory(message.id, targetCategory)
        smsDao.updateMessageBlockedState(message.id, false)

        // Rebuild supplemental rows; clear first to avoid dupes.
        smsDao.deleteTransactionByMessageId(message.id)
        smsDao.deleteReminderByMessageId(message.id)
        if (classification.isFinance && classification.amount != null) {
            smsDao.insertTransaction(
                FinanceTx(
                    messageId = message.id,
                    bankName = classification.bankName ?: "Unknown Bk",
                    amount = classification.amount,
                    isCredit = classification.isCredit ?: false,
                    balance = classification.balance ?: 0.0,
                    timestamp = message.timestamp
                )
            )
        }
        if (classification.isReminder && classification.dueDate != null) {
            smsDao.insertReminder(
                ReminderSms(
                    messageId = message.id,
                    sender = message.sender,
                    title = classification.reminderTitle ?: "SMS Reminder",
                    body = message.body,
                    dueDate = classification.dueDate,
                    isSyncedToCalendar = false
                )
            )
        }
    }

    /**
     * Allowlist a sender so it is never auto-classified as Spam: remove any existing CONTACT
     * rule for the sender and add a CONTACT -> "NotSpam" rule. Reverse of the CONTACT -> Spam
     * rule added when reporting spam.
     */
    suspend fun allowlistSender(sender: String) {
        smsDao.getContactRules()
            .filter { it.value == sender }
            .forEach { smsDao.deleteRuleById(it.id) }
        addRule("CONTACT", sender, "NotSpam")
    }

    /** Create a reminder for an arbitrary message (used by the detail screen's "Add reminder"). */
    suspend fun addReminderForMessage(message: SMSMessage, title: String, dueDate: Long): Long {
        val reminder = ReminderSms(
            messageId = message.id,
            sender = message.sender,
            title = title,
            body = message.body,
            dueDate = dueDate,
            isSyncedToCalendar = false
        )
        return smsDao.insertReminder(reminder)
    }

    /**
     * Re-analyze every stored message and rewrite it onto the current four-category taxonomy
     * (Personal / Promotions / Others / Spam). Triggered on demand from Settings.
     *
     * Steps: (1) migrate any legacy filter-rule target categories (Accounts/Reminder/Services →
     * Others) so the classifier — which reads these rules — is stable; (2) re-classify each
     * message, updating its category + blocked state; (3) rebuild the derived finance ledger
     * rows (delete + recreate, they are purely derived) and add any missing reminder rows
     * (existing reminders are left intact so manually-added ones survive). Returns the count of
     * messages processed.
     */
    suspend fun recategorizeAllMessages(): Int {
        // (1) Normalize legacy rule target categories. Leave the control values used by the
        // classifier untouched (Spam/Blocked block the sender; NotSpam allowlists it).
        val preserved = setOf("Spam", "Blocked", "NotSpam")
        for (rule in smsDao.getAllRulesOnce()) {
            if (rule.targetCategory in preserved) continue
            val normalized = SmsClassifier.normalizeCategory(rule.targetCategory)
            if (normalized != rule.targetCategory) {
                smsDao.updateRuleCategory(rule.id, normalized)
            }
        }

        // Reload rules (now normalized) for classification.
        val allRules = smsDao.getAllRulesOnce()
        val customKeywords = allRules.filter { it.type == "KEYWORD" }.map { it.value to it.targetCategory }
        val customContacts = allRules.filter { it.type == "CONTACT" }.associate { it.value to it.targetCategory }

        // (1b) De-duplicate stored messages (same sender/body/timestamp): keep one survivor per
        // group — preferring a row backed by the system provider (systemId != null), else the
        // lowest id — and delete the rest along with their derived finance/reminder rows. This
        // cleans up duplicates that were inserted before ingestion-time de-dup existed.
        val groups = smsDao.getAllMessagesOnce().groupBy { Triple(it.sender, it.body, it.timestamp) }
        for ((_, group) in groups) {
            if (group.size <= 1) continue
            val survivor = group.firstOrNull { it.systemId != null } ?: group.minByOrNull { it.id }!!
            for (dup in group) {
                if (dup.id == survivor.id) continue
                smsDao.deleteTransactionByMessageId(dup.id)
                smsDao.deleteReminderByMessageId(dup.id)
                smsDao.deleteMessageById(dup.id)
            }
        }

        val messages = smsDao.getAllMessagesOnce()
        // Existing reminders keyed by message id, so a re-classify can *update* a stale due date
        // (e.g. a historic 3-day fallback) rather than skipping the message entirely.
        val remindersByMessageId = smsDao.getAllRemindersOnce().associateBy { it.messageId }

        for (message in messages) {
            val classification = SmsClassifier.classify(
                sender = message.sender,
                body = message.body,
                customKeywords = customKeywords,
                customContacts = customContacts,
                referenceTime = message.timestamp
            )

            smsDao.updateMessageCategory(message.id, classification.category)
            smsDao.updateMessageBlockedState(message.id, classification.isBlocked)

            // Finance rows are purely derived — safe to rebuild from scratch.
            smsDao.deleteTransactionByMessageId(message.id)
            if (classification.isFinance && classification.amount != null) {
                smsDao.insertTransaction(
                    FinanceTx(
                        messageId = message.id,
                        bankName = classification.bankName ?: "Unknown Bk",
                        amount = classification.amount,
                        isCredit = classification.isCredit ?: false,
                        balance = classification.balance ?: 0.0,
                        timestamp = message.timestamp
                    )
                )
            }

            // Reminders may be manual. Insert when the message is flagged and has none yet;
            // when one already exists, refresh its (possibly stale) due date in place so an
            // improved date parse — or a corrected fallback — takes effect on re-categorize.
            if (classification.isReminder && classification.dueDate != null) {
                val existing = remindersByMessageId[message.id]
                if (existing == null) {
                    smsDao.insertReminder(
                        ReminderSms(
                            messageId = message.id,
                            sender = message.sender,
                            title = classification.reminderTitle ?: "SMS Reminder",
                            body = message.body,
                            dueDate = classification.dueDate,
                            isSyncedToCalendar = false
                        )
                    )
                } else if (existing.dueDate != classification.dueDate) {
                    smsDao.updateReminderDueDate(existing.id, classification.dueDate)
                }
            }
        }

        // Drop any one-shot reminders whose due date has already passed, so re-categorization
        // ends in the same clean state as launch (it must not revive reminders the launch-time
        // purge already removed). Recurring reminders and ones due today are kept.
        purgeExpiredReminders()

        return messages.size
    }

    suspend fun clearAllData() {
        smsDao.deleteAllMessages()
        smsDao.deleteAllTransactions()
    }

    // --- Reminders ---
    suspend fun deleteReminder(id: Long) {
        smsDao.deleteReminder(id)
    }

    suspend fun updateReminderSyncState(id: Long, isSynced: Boolean) {
        smsDao.updateReminderSyncState(id, isSynced)
    }

    suspend fun getReminderById(id: Long): ReminderSms? = smsDao.getReminderById(id)

    /** One-shot snapshot of all reminders, used by boot-time alarm re-arming. */
    suspend fun getAllRemindersOnce(): List<ReminderSms> = smsDao.getAllRemindersOnce()

    suspend fun setReminderAlert(id: Long, enabled: Boolean) {
        smsDao.updateReminderAlert(id, enabled)
    }

    suspend fun setReminderRecurrence(id: Long, recurrence: String) {
        smsDao.updateReminderRecurrence(id, recurrence)
    }

    /** Move a reminder's due date forward (used when a recurring alarm fires). */
    suspend fun advanceReminderDueDate(id: Long, dueDate: Long) {
        smsDao.updateReminderDueDate(id, dueDate)
    }

    /**
     * Drop reminders whose due date is already in the past. The cutoff is the start of *today*
     * (local midnight) so a reminder due today is still kept and shown.
     */
    suspend fun purgeExpiredReminders() {
        val cutoff = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        smsDao.deleteExpiredReminders(cutoff)
    }

    // Bulk Seed for empty states/demo
}
