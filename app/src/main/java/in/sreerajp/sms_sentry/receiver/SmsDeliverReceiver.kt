package `in`.sreerajp.sms_sentry.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.data.SystemSmsStore
import `in`.sreerajp.sms_sentry.util.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives `SMS_DELIVER` — the broadcast delivered only to the **default SMS app**. Unlike
 * `SMS_RECEIVED` (passive, handled by [SmsReceiver]), when we are default the OS no longer writes
 * the message to the system provider for us, so we do it here, then funnel into Room and notify.
 *
 * If we are somehow not the default app, this receiver does nothing — [SmsReceiver] handles the
 * passive path so the message is not double-inserted.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val grouped = messages.groupBy { it.displayOriginatingAddress ?: "Unknown" }

        val db = SmsDatabase.getDatabase(context)
        val repository = SmsRepository(db.smsDao)
        val systemStore = SystemSmsStore(context)
        val appContext = context.applicationContext

        for ((sender, mList) in grouped) {
            val fullBody = mList.joinToString(separator = "") { it.displayMessageBody ?: "" }
            val timestamp = mList.first().timestampMillis
            val simId = 1

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // As the default app we own the system provider — persist the incoming SMS there.
                    val systemId = systemStore.writeInbox(sender, fullBody, timestamp)
                    val msgId = repository.processAndInsertMessage(
                        sender = sender,
                        body = fullBody,
                        timestamp = timestamp,
                        simId = simId,
                        systemId = systemId
                    )
                    SmsNotificationHelper.showNotification(appContext, sender, fullBody, simId, msgId)
                } catch (e: Exception) {
                    Log.e("SmsDeliverReceiver", "Error handling delivered SMS", e)
                }
            }
        }
    }
}
