package `in`.sreerajp.sms_sentry.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.util.ScheduledSmsScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms are cleared on reboot, so re-arm every pending scheduled SMS after boot.
 * Rows whose time has already passed are armed for "now" so they fire immediately.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        if (!ScheduledSmsScheduler.canScheduleExact(appContext)) {
            Log.w(TAG, "Exact alarms not permitted after boot; scheduled messages will not re-arm")
            return
        }
        val repository = SmsRepository(SmsDatabase.getDatabase(appContext).smsDao)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                for (row in repository.getAllScheduledOnce()) {
                    val triggerAt = row.scheduledTime.coerceAtLeast(now)
                    ScheduledSmsScheduler.schedule(appContext, row.id, triggerAt)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-arm scheduled messages after boot", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
