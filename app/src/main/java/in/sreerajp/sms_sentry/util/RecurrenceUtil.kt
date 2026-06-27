package `in`.sreerajp.sms_sentry.util

import java.util.Calendar

/**
 * Recurrence cadence for reminders. The string values are what is persisted in the
 * `reminders.recurrence` column; keep them stable.
 */
object RecurrenceUtil {
    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"

    /** Ordered list for UI pickers. */
    val ALL = listOf(NONE, DAILY, WEEKLY, MONTHLY, YEARLY)

    /** Human label for a recurrence value. */
    fun label(recurrence: String): String = when (recurrence) {
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
        else -> "Does not repeat"
    }

    fun isRecurring(recurrence: String): Boolean = recurrence != NONE && recurrence in ALL

    /**
     * The next occurrence of [dueDate] for the given [recurrence]. For [NONE] (or any unknown
     * value) the input is returned unchanged. Uses [Calendar.add] so month/year roll-over and
     * DST are handled by the calendar (e.g. Jan 31 + 1 month -> Feb 28/29).
     */
    fun nextOccurrence(dueDate: Long, recurrence: String): Long {
        val field = when (recurrence) {
            DAILY -> Calendar.DAY_OF_YEAR
            WEEKLY -> Calendar.WEEK_OF_YEAR
            MONTHLY -> Calendar.MONTH
            YEARLY -> Calendar.YEAR
            else -> return dueDate
        }
        return Calendar.getInstance().apply {
            timeInMillis = dueDate
            add(field, 1)
        }.timeInMillis
    }

    /**
     * Advance [dueDate] by the [recurrence] cadence until it is strictly in the future relative to
     * [now]. Returns the input for non-recurring values. Used to skip past missed occurrences
     * (e.g. after the device was off for several days) without firing a burst of stale alerts.
     */
    fun nextFutureOccurrence(dueDate: Long, recurrence: String, now: Long): Long {
        if (!isRecurring(recurrence)) return dueDate
        var next = dueDate
        var guard = 0
        while (next <= now && guard < 10_000) {
            next = nextOccurrence(next, recurrence)
            guard++
        }
        return next
    }
}
