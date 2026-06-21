package `in`.sreerajp.sms_sentry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A text SMS the user has queued for future delivery via the composer's "Schedule" action.
 *
 * Rows live here (not in [SMSMessage]) until they actually fire, so scheduled messages do not
 * pollute conversation threads. When the alarm fires, the message is sent through the normal
 * real-send path (mirror to system Sent box + insert into `messages` as STATUS_SENDING) and the
 * scheduled row is deleted. The row id doubles as the AlarmManager request code / PendingIntent
 * extra so a pending alarm can be cancelled or re-armed (after reboot) deterministically.
 */
@Entity(tableName = "scheduled_messages")
data class ScheduledSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipient: String,
    val body: String,
    val simId: Int,
    val scheduledTime: Long, // epoch ms when the message should be sent
    val createdAt: Long
)
