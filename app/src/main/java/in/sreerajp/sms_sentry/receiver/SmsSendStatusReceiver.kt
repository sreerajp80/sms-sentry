package `in`.sreerajp.sms_sentry.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the `sentIntent` / `deliveryIntent` callbacks fired by [android.telephony.SmsManager]
 * after a send, and advances the Room message's [SMSMessage.status]
 * (Sending → Sent / Failed, then → Delivered when the carrier confirms).
 *
 * Targeted explicitly by class via a PendingIntent, so it works as a manifest receiver.
 * Delivery reports are carrier-dependent — some networks never fire the delivery callback, so
 * a message may legitimately stay at "Sent".
 */
class SmsSendStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msgId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (msgId < 0) return

        val newStatus = when (intent.action) {
            ACTION_SENT -> if (resultCode == Activity.RESULT_OK) SMSMessage.STATUS_SENT else SMSMessage.STATUS_FAILED
            ACTION_DELIVERED -> SMSMessage.STATUS_DELIVERED
            else -> return
        }

        val dao = SmsDatabase.getDatabase(context).smsDao
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.updateStatus(msgId, newStatus)
            } catch (e: Exception) {
                Log.e("SmsSendStatus", "Failed to update status for msg=$msgId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "in.sreerajp.sms_sentry.SMS_SENT"
        const val ACTION_DELIVERED = "in.sreerajp.sms_sentry.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "in.sreerajp.sms_sentry.EXTRA_MESSAGE_ID"
    }
}
