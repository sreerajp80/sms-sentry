package `in`.sreerajp.sms_sentry.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import `in`.sreerajp.sms_sentry.MainActivity
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.util.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notification_id", -1)

        when (action) {
            "in.sreerajp.sms_sentry.ACTION_COPY_OTP" -> {
                val otp = intent.getStringExtra("otp_code")
                val sender = intent.getStringExtra("sender") ?: "Unknown"
                val simId = intent.getIntExtra("sim_id", 1)
                val messageId = intent.getLongExtra("message_id", -1L)

                if (!otp.isNullOrEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP Code", otp)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "OTP $otp copied to clipboard!", Toast.LENGTH_SHORT).show()

                    if (notificationId != -1) {
                        try {
                            SmsNotificationHelper.updateNotificationWithCopiedState(
                                context = context,
                                sender = sender,
                                otp = otp,
                                simId = simId,
                                messageId = messageId,
                                notificationId = notificationId
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            "in.sreerajp.sms_sentry.ACTION_DELETE_MESSAGE" -> {
                val messageId = intent.getLongExtra("message_id", -1L)
                if (messageId != -1L) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = SmsDatabase.getDatabase(context)
                            db.smsDao.deleteMessageById(messageId)
                            db.smsDao.deleteTransactionByMessageId(messageId)
                            db.smsDao.deleteReminderByMessageId(messageId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    Toast.makeText(context, "SMS Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            "in.sreerajp.sms_sentry.ACTION_OPEN_APP" -> {
                val messageId = intent.getLongExtra("message_id", -1L)
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    if (messageId > 0) {
                        putExtra(SmsNotificationHelper.EXTRA_OPEN_MESSAGE_ID, messageId)
                    }
                }
                context.startActivity(openIntent)
            }
        }

        if (notificationId != -1 && action != "in.sreerajp.sms_sentry.ACTION_COPY_OTP") {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}
