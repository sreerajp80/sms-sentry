package `in`.sreerajp.sms_sentry.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.RemoteInput
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.data.SystemSmsStore
import `in`.sreerajp.sms_sentry.util.DefaultSmsAppManager
import `in`.sreerajp.sms_sentry.util.SimManager
import `in`.sreerajp.sms_sentry.util.SmsNotificationHelper
import `in`.sreerajp.sms_sentry.util.SmsSender
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
            SmsNotificationHelper.ACTION_REPLY -> {
                val sender = intent.getStringExtra("sender")
                val simId = intent.getIntExtra("sim_id", 1)
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(SmsNotificationHelper.KEY_REPLY_TEXT)?.toString()?.trim()
                val appContext = context.applicationContext

                if (sender.isNullOrBlank() || replyText.isNullOrEmpty()) {
                    // Nothing to send (e.g. empty submission); the trailing block dismisses the notif.
                } else if (!DefaultSmsAppManager.isDefault(appContext)) {
                    Toast.makeText(appContext, "Set SMS Sentry as default to reply.", Toast.LENGTH_SHORT).show()
                } else {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repository = SmsRepository(SmsDatabase.getDatabase(appContext).smsDao)
                            val systemSmsStore = SystemSmsStore(appContext)
                            val now = System.currentTimeMillis()
                            // Mirror to the system Sent box + persist as a Sending row, same as the composer.
                            val systemId = systemSmsStore.writeSent(sender, replyText, now)
                            val msgId = repository.processAndInsertMessage(
                                sender = sender,
                                body = replyText,
                                timestamp = now,
                                simId = simId,
                                systemId = systemId,
                                type = SMSMessage.TYPE_SENT,
                                isRead = true,
                                status = SMSMessage.STATUS_SENDING
                            )
                            try {
                                val subId = SimManager.subscriptionIdForSlot(appContext, simId)
                                SmsSender.dispatch(appContext, sender, replyText, msgId, subId)
                            } catch (e: Exception) {
                                repository.updateMessageStatus(msgId, SMSMessage.STATUS_FAILED)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            "in.sreerajp.sms_sentry.ACTION_DELETE_MESSAGE" -> {
                val messageId = intent.getLongExtra("message_id", -1L)
                if (messageId != -1L) {
                    val appContext = context.applicationContext
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        var deleted = false
                        try {
                            val db = SmsDatabase.getDatabase(appContext)
                            db.smsDao.deleteMessageById(messageId)
                            db.smsDao.deleteTransactionByMessageId(messageId)
                            db.smsDao.deleteReminderByMessageId(messageId)
                            deleted = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            // Toast on the main thread, only after the delete actually ran.
                            Handler(Looper.getMainLooper()).post {
                                val msg = if (deleted) "SMS Deleted" else "Couldn't delete SMS"
                                Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
                            }
                            pendingResult.finish()
                        }
                    }
                }
            }
        }

        if (notificationId != -1 && action != "in.sreerajp.sms_sentry.ACTION_COPY_OTP") {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}
