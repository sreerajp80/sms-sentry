package `in`.sreerajp.sms_sentry.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.data.SystemSmsStore
import `in`.sreerajp.sms_sentry.util.DefaultSmsAppManager
import `in`.sreerajp.sms_sentry.util.SimManager
import `in`.sreerajp.sms_sentry.util.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a scheduled SMS's alarm goes off. Loads the [in.sreerajp.sms_sentry.data.ScheduledSms]
 * row, sends it through the same real path the composer uses (mirror to the system Sent box +
 * insert a `messages` row as STATUS_SENDING, then [SmsSender.dispatch]), and deletes the scheduled
 * row. Targeted explicitly by class via a PendingIntent, so it works as a manifest receiver.
 *
 * If the app is no longer the default SMS app at fire time, the inserted message is marked FAILED
 * (mirroring the composer's send guard) and the scheduled row is still cleared.
 */
class ScheduledSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val scheduledId = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (scheduledId < 0) return

        val appContext = context.applicationContext
        val repository = SmsRepository(SmsDatabase.getDatabase(appContext).smsDao)
        val systemSmsStore = SystemSmsStore(appContext)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val row = repository.getScheduledById(scheduledId) ?: return@launch
                val now = System.currentTimeMillis()

                val isDefault = DefaultSmsAppManager.isDefault(appContext)
                val systemId = if (isDefault) systemSmsStore.writeSent(row.recipient, row.body, now) else null
                val msgId = repository.processAndInsertMessage(
                    sender = row.recipient,
                    body = row.body,
                    timestamp = now,
                    simId = row.simId,
                    systemId = systemId,
                    type = SMSMessage.TYPE_SENT,
                    isRead = true,
                    status = if (isDefault) SMSMessage.STATUS_SENDING else SMSMessage.STATUS_FAILED
                )

                if (isDefault) {
                    try {
                        val subId = SimManager.subscriptionIdForSlot(appContext, row.simId)
                        SmsSender.dispatch(appContext, row.recipient, row.body, msgId, subId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Scheduled send failed for id=$scheduledId", e)
                        repository.updateMessageStatus(msgId, SMSMessage.STATUS_FAILED)
                    }
                } else {
                    Log.w(TAG, "Not default SMS app; scheduled message id=$scheduledId marked FAILED")
                }

                repository.deleteScheduledById(scheduledId)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling scheduled SMS id=$scheduledId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledSmsReceiver"
        const val ACTION_FIRE = "in.sreerajp.sms_sentry.SCHEDULED_SMS_FIRE"
        const val EXTRA_SCHEDULED_ID = "in.sreerajp.sms_sentry.EXTRA_SCHEDULED_ID"
    }
}
