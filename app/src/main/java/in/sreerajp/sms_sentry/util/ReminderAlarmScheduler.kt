package `in`.sreerajp.sms_sentry.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import `in`.sreerajp.sms_sentry.data.ReminderSms
import `in`.sreerajp.sms_sentry.receiver.ReminderAlarmReceiver
import java.util.Calendar

/**
 * Arms / cancels the exact alarms that drive in-app reminder due-alerts. Mirrors
 * [ScheduledSmsScheduler] but targets [ReminderAlarmReceiver], so the two use independent
 * PendingIntent namespaces (different target component) even when ids collide.
 *
 * A reminder is armed only when alerts are globally enabled ([alertsGloballyEnabled]), the
 * reminder's own [ReminderSms.alertEnabled] is true, exact alarms are permitted, and the
 * computed trigger time is in the future. Arming is idempotent via FLAG_UPDATE_CURRENT.
 */
object ReminderAlarmScheduler {

    /** SharedPreferences (the app's "theme_prefs") key for the global reminder-alert toggle. */
    const val PREF_ALERTS_ENABLED = "reminder_alerts_enabled"

    /** Hour-of-day (local) used when a reminder's due timestamp is at midnight (date-only). */
    private const val DEFAULT_ALERT_HOUR = 9

    /** True when the platform currently allows this app to set exact alarms. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java)
        return am?.canScheduleExactAlarms() == true
    }

    /** Whether the global "Reminder due alerts" switch is on (default on). */
    fun alertsGloballyEnabled(context: Context): Boolean =
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_ALERTS_ENABLED, true)

    /**
     * The wall-clock time the alert should fire for a reminder due at [dueDate]. Date-only due
     * dates (time-of-day at local midnight) are bumped to [DEFAULT_ALERT_HOUR]:00 so the user is
     * not woken at 00:00; due dates that already carry a time-of-day are used as-is.
     */
    fun triggerTimeFor(dueDate: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
        val isMidnight = cal.get(Calendar.HOUR_OF_DAY) == 0 &&
            cal.get(Calendar.MINUTE) == 0 &&
            cal.get(Calendar.SECOND) == 0
        if (isMidnight) {
            cal.set(Calendar.HOUR_OF_DAY, DEFAULT_ALERT_HOUR)
        }
        return cal.timeInMillis
    }

    /** Arm an exact alarm for [reminderId] firing at the trigger time derived from [dueDate]. */
    fun schedule(context: Context, reminderId: Long, dueDate: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeFor(dueDate),
            pendingIntent(context, reminderId, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    /** Cancel a previously-armed alarm for [reminderId]. Safe to call if none exists. */
    fun cancel(context: Context, reminderId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, reminderId, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    /**
     * Reconcile alarms against the given [reminders]: arm each one whose alert is enabled and
     * whose trigger time is still in the future; cancel the rest. This is the single arming
     * funnel — the ViewModel calls it on every reminders-Flow emission and BootReceiver calls it
     * from a one-shot snapshot. Idempotent.
     */
    fun reconcile(context: Context, reminders: List<ReminderSms>) {
        val globallyOn = alertsGloballyEnabled(context) && canScheduleExact(context)
        val now = System.currentTimeMillis()
        for (r in reminders) {
            val shouldArm = globallyOn && r.alertEnabled && triggerTimeFor(r.dueDate) > now
            if (shouldArm) schedule(context, r.id, r.dueDate) else cancel(context, r.id)
        }
    }

    private fun pendingIntent(context: Context, reminderId: Long, extraFlags: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
