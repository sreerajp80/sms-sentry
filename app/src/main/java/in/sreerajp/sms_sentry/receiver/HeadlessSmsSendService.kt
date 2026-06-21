package `in`.sreerajp.sms_sentry.receiver

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Handles `RESPOND_VIA_MESSAGE` — the "reply with a message" action shown when rejecting a call.
 * Declaring this service is **required** for the app to be selectable as the default SMS app.
 *
 * It sends the quick-reply text via [SmsManager]; the message is also captured into Room by the
 * normal send/receive plumbing when the app is default.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            try {
                val message = intent.getStringExtra(Intent.EXTRA_TEXT)
                val recipients = intent.data?.schemeSpecificPart
                if (!message.isNullOrBlank() && !recipients.isNullOrBlank()) {
                    @Suppress("DEPRECATION")
                    val smsManager = SmsManager.getDefault()
                    recipients.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { number ->
                        smsManager.sendTextMessage(number, null, message, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("HeadlessSmsSend", "Failed to send respond-via-message", e)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
