package `in`.sreerajp.sms_sentry.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.util.DefaultSmsAppManager
import `in`.sreerajp.sms_sentry.util.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            // When we are the default SMS app, SmsDeliverReceiver (SMS_DELIVER) owns ingestion and
            // also writes to the system provider. Bail out here to avoid double-inserting.
            if (DefaultSmsAppManager.isDefault(context)) return

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNotEmpty()) {
                // Group messages by sender in case they arrive fragmented
                val grouped = messages.groupBy { it.displayOriginatingAddress ?: "Unknown" }

                val db = SmsDatabase.getDatabase(context)
                val repository = SmsRepository(db.smsDao)

                for ((sender, mList) in grouped) {
                    val fullBody = mList.joinToString(separator = "") { it.displayMessageBody ?: "" }
                    val timestamp = mList.first().timestampMillis
                    val simId = 1 // Default Sim index

                    // Process and insert message into internal database asynchronously
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val msgId = repository.processAndInsertMessage(
                                sender = sender,
                                body = fullBody,
                                timestamp = timestamp,
                                simId = simId
                            )
                            // Display native system notification (automatically extracts OTP and adds "Copy OTP", "Open" and "Delete" actions)
                            SmsNotificationHelper.showNotification(context, sender, fullBody, simId, msgId)
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Error saving received SMS to database", e)
                        }
                    }
                }
            }
        }
    }
}
