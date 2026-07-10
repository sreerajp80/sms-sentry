package `in`.sreerajp.sms_sentry.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.sreerajp.sms_sentry.data.SmsDatabase
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.util.RecurrenceUtil
import `in`.sreerajp.sms_sentry.util.ReminderAlarmScheduler
import `in`.sreerajp.sms_sentry.util.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a reminder's exact alarm goes off ([ACTION_FIRE]): posts the due-alert notification
 * and, for recurring reminders, advances the due date to the next future occurrence and re-arms.
 * One-shot reminders are left in place — the next expiry purge clears them.
 *
 * Also handles the notification "Done" button ([ACTION_DONE]): delete the reminder, cancel any
 * armed alarm, and dismiss the notification. Targeted explicitly by class, so it works as a
 * manifest receiver.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return
        val repository = SmsRepository(SmsDatabase.getDatabase(appContext).smsDao)

        when (intent.action) {
            ACTION_FIRE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminder = repository.getReminderById(reminderId) ?: return@launch
                        if (!reminder.alertEnabled) return@launch

                        SmsNotificationHelper.showReminderNotification(appContext, reminder)

                        // Re-arm the next alert. Within the advance-notice window this is the next
                        // daily tick; once the due-date alert has fired the window is exhausted
                        // (nextTriggerAfter == null), at which point a recurring reminder rolls to
                        // its next occurrence and a one-shot goes quiet (left for the expiry purge).
                        val now = System.currentTimeMillis()
                        val lead = ReminderAlarmScheduler.leadDays(appContext)
                        var dueDate = reminder.dueDate
                        var next = ReminderAlarmScheduler.nextTriggerAfter(dueDate, lead, now)
                        if (next == null && RecurrenceUtil.isRecurring(reminder.recurrence)) {
                            dueDate = RecurrenceUtil.nextFutureOccurrence(dueDate, reminder.recurrence, now)
                            repository.advanceReminderDueDate(reminderId, dueDate)
                            next = ReminderAlarmScheduler.nextTriggerAfter(dueDate, lead, now)
                        }
                        if (next != null) {
                            ReminderAlarmScheduler.scheduleAt(appContext, reminderId, next)
                        } else {
                            ReminderAlarmScheduler.cancel(appContext, reminderId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error firing reminder id=$reminderId", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_DONE -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notificationId >= 0) {
                    (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .cancel(notificationId)
                }
                ReminderAlarmScheduler.cancel(appContext, reminderId)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repository.deleteReminder(reminderId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error completing reminder id=$reminderId", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val ACTION_FIRE = "in.sreerajp.sms_sentry.REMINDER_FIRE"
        const val ACTION_DONE = "in.sreerajp.sms_sentry.REMINDER_DONE"
        const val EXTRA_REMINDER_ID = "in.sreerajp.sms_sentry.EXTRA_REMINDER_ID"
        const val EXTRA_NOTIFICATION_ID = "in.sreerajp.sms_sentry.EXTRA_REMINDER_NOTIFICATION_ID"
    }
}
