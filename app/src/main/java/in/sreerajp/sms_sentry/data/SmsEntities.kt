package `in`.sreerajp.sms_sentry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class SMSMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val category: String, // "Personal", "Promotions", "Others", "Spam"
    val simId: Int, // 1 for SIM 1, 2 for SIM 2
    val isBlocked: Boolean = false,
    val isRead: Boolean = false, // local-only: set true when the message is opened
    // --- System-provider sync (default-SMS-app mode) ---
    // The matching row id in the system SMS provider (content://sms) or MMS provider
    // (content://mms when isMms). Null for messages that never existed in the system DB
    // (demo seed, simulated, P2P/CSV import). Used to delete the system row in two-way sync.
    val systemId: Long? = null,
    // Provider thread_id when known; null falls back to grouping by normalized sender.
    val threadId: Long? = null,
    // Box: 1 = inbox/received, 2 = sent.
    val type: Int = TYPE_INBOX,
    // True when this row originated from an MMS (content://mms) instead of plain SMS.
    val isMms: Boolean = false,
    // For MMS: comma-joined content:// / file URIs of media parts (images, etc.). Null for SMS.
    val attachmentUri: String? = null,
    // Outgoing delivery state (see STATUS_* constants). 0 for received messages.
    val status: Int = STATUS_NONE
) {
    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2

        const val STATUS_NONE = 0      // received message, no send status
        const val STATUS_SENDING = 1
        const val STATUS_SENT = 2
        const val STATUS_DELIVERED = 3
        const val STATUS_FAILED = 4
    }
}

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "KEYWORD" or "CONTACT"
    val value: String, // The keyword or contact number
    val targetCategory: String // "Personal", "Promotions", "Others", "Spam" (or control: "Blocked"/"NotSpam")
)

@Entity(tableName = "finance_transactions")
data class FinanceTx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val bankName: String,
    val amount: Double,
    val isCredit: Boolean,
    val balance: Double,
    val timestamp: Long
)

@Entity(tableName = "reminders")
data class ReminderSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val sender: String,
    val title: String,
    val body: String,
    val dueDate: Long,
    val isSyncedToCalendar: Boolean = false
)
