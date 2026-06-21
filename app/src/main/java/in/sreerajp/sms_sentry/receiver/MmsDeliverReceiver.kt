package `in`.sreerajp.sms_sentry.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.data.SystemMmsStore
import `in`.sreerajp.sms_sentry.engine.MmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receives `WAP_PUSH_DELIVER` (MMS notification) when we are the default SMS app.
 *
 * Carriers send a WAP push that only *announces* the MMS; the actual content must be downloaded
 * from the carrier MMSC. Modern Android handles the download via the system MMS service once a
 * default app is set — the downloaded MMS then appears in `content://mms`. Here we wait briefly
 * for the row to materialize, parse its parts, and funnel the text through the classifier.
 *
 * MMS download/parsing is carrier- and network-dependent and hard to exercise on emulators;
 * failures are logged and swallowed rather than crashing the receiver.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val db = SmsDatabase.getDatabase(context)
        val repository = SmsRepository(db.smsDao)
        val mmsStore = SystemMmsStore(context)
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Give the system MMS service a moment to download and insert the message.
                var newest = mmsStore.readAll().firstOrNull { it.messageBox == 1 }
                var attempts = 0
                while (newest == null && attempts < 6) {
                    delay(1500)
                    newest = mmsStore.readAll().firstOrNull { it.messageBox == 1 }
                    attempts++
                }
                val row = newest ?: return@launch

                val parsed = MmsParser.parse(appContext, row.systemId)
                repository.importMessages(
                    listOf(
                        SmsRepository.ImportRow(
                            systemId = row.systemId,
                            sender = parsed.sender,
                            body = parsed.text.ifBlank { "[MMS]" },
                            timestamp = row.date * 1000L, // MMS date is in seconds
                            type = row.messageBox,
                            isRead = row.read,
                            threadId = row.threadId,
                            isMms = true,
                            attachmentUri = parsed.attachmentUris.joinToString(",").ifBlank { null }
                        )
                    )
                )
            } catch (e: Exception) {
                Log.e("MmsDeliverReceiver", "Error handling delivered MMS", e)
            }
        }
    }
}
