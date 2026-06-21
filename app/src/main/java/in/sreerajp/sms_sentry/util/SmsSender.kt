package `in`.sreerajp.sms_sentry.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import `in`.sreerajp.sms_sentry.receiver.SmsSendStatusReceiver

/**
 * Shared low-level SMS send path. Extracted from the ViewModel so both the composer (immediate
 * send) and [in.sreerajp.sms_sentry.receiver.ScheduledSmsReceiver] (future delivery) use the
 * exact same real [SmsManager] dispatch, including multipart splitting and the sent/delivery
 * [PendingIntent]s that drive [SmsSendStatusReceiver].
 *
 * Caller is responsible for having already mirrored the message into the system Sent box and
 * inserted the Room row (so [msgId] identifies the row whose status these intents will update).
 */
object SmsSender {

    /**
     * Fires the radio send for [body] to [recipient], wiring sent/delivery callbacks to the Room
     * row [msgId]. Throws on failure so the caller can mark the row FAILED.
     */
    fun dispatch(context: Context, recipient: String, body: String, msgId: Long) {
        @Suppress("DEPRECATION")
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

        val sentIntent = statusPendingIntent(context, SmsSendStatusReceiver.ACTION_SENT, msgId)
        val deliveryIntent = statusPendingIntent(context, SmsSendStatusReceiver.ACTION_DELIVERED, msgId)

        val parts = smsManager.divideMessage(body)
        if (parts.size > 1) {
            val sentIntents = ArrayList<PendingIntent>(parts.size).apply {
                repeat(parts.size - 1) { add(noopPendingIntent(context)) }; add(sentIntent)
            }
            val deliveryIntents = ArrayList<PendingIntent>(parts.size).apply {
                repeat(parts.size - 1) { add(noopPendingIntent(context)) }; add(deliveryIntent)
            }
            smsManager.sendMultipartTextMessage(recipient, null, parts, sentIntents, deliveryIntents)
        } else {
            smsManager.sendTextMessage(recipient, null, body, sentIntent, deliveryIntent)
        }
    }

    private fun statusPendingIntent(context: Context, action: String, msgId: Long): PendingIntent {
        val intent = Intent(action).apply {
            setClass(context, SmsSendStatusReceiver::class.java)
            putExtra(SmsSendStatusReceiver.EXTRA_MESSAGE_ID, msgId)
        }
        val requestCode = (msgId.toInt() * 31) + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun noopPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent("in.sreerajp.sms_sentry.NOOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
