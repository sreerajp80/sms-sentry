package `in`.sreerajp.sms_sentry.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import `in`.sreerajp.sms_sentry.receiver.ScheduledSmsReceiver

/**
 * Arms / cancels the exact alarms that drive scheduled SMS delivery.
 *
 * Exact-only by design: [canScheduleExact] must be true before [schedule] is called. On API 31+
 * this maps to the `SCHEDULE_EXACT_ALARM` permission; below 31 it is implicitly granted. The
 * alarm targets [ScheduledSmsReceiver], carrying the [ScheduledSms.id] both as the PendingIntent
 * request code (so it can be cancelled / replaced deterministically) and as an intent extra.
 */
object ScheduledSmsScheduler {

    /** True when the platform currently allows this app to set exact alarms. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java)
        return am?.canScheduleExactAlarms() == true
    }

    /** Arm an exact alarm for [id] at [triggerAtMillis]. Caller must ensure [canScheduleExact]. */
    fun schedule(context: Context, id: Long, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context, id, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    /** Cancel a previously-armed alarm for [id]. Safe to call if none exists. */
    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, id, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun pendingIntent(context: Context, id: Long, extraFlags: Int): PendingIntent {
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_FIRE
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
