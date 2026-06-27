package `in`.sreerajp.sms_sentry.receiver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import `in`.sreerajp.sms_sentry.util.SimManager
import `in`.sreerajp.sms_sentry.util.SmsSender

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
                    // Send on the user's preferred default SIM (slot -> subscriptionId); falls back
                    // to the system default SIM when unset or unresolvable.
                    val subId = SimManager.subscriptionIdForSlot(this, defaultOutgoingSlot())
                    val smsManager = SmsSender.smsManagerFor(this, subId)
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

    /** Read the persisted default-outgoing-SIM setting as a 1-based slot (falls back to slot 1). */
    private fun defaultOutgoingSlot(): Int {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        return if (prefs.getString("default_sms_sim", "Ask Every Time") == "SIM 2") 2 else 1
    }
}
