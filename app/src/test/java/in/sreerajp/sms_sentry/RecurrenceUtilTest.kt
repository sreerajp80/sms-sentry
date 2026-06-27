package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.util.RecurrenceUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurrenceUtilTest {

    private fun millis(year: Int, month0: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month0, day, hour, 0, 0)
        }.timeInMillis

    private fun parts(millis: Long): Triple<Int, Int, Int> =
        Calendar.getInstance().apply { timeInMillis = millis }.let {
            Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
        }

    @Test
    fun none_returnsInputUnchanged() {
        val due = millis(2026, Calendar.JUNE, 27)
        assertEquals(due, RecurrenceUtil.nextOccurrence(due, RecurrenceUtil.NONE))
        assertFalse(RecurrenceUtil.isRecurring(RecurrenceUtil.NONE))
    }

    @Test
    fun daily_advancesOneDay() {
        val due = millis(2026, Calendar.JUNE, 27)
        assertEquals(Triple(2026, Calendar.JUNE, 28), parts(RecurrenceUtil.nextOccurrence(due, RecurrenceUtil.DAILY)))
    }

    @Test
    fun weekly_advancesSevenDays() {
        val due = millis(2026, Calendar.JUNE, 27)
        assertEquals(Triple(2026, Calendar.JULY, 4), parts(RecurrenceUtil.nextOccurrence(due, RecurrenceUtil.WEEKLY)))
    }

    @Test
    fun monthly_rollsOverMonthEnd() {
        // Jan 31 + 1 month -> Feb 28 (2026 is not a leap year), handled by Calendar.
        val due = millis(2026, Calendar.JANUARY, 31)
        assertEquals(Triple(2026, Calendar.FEBRUARY, 28), parts(RecurrenceUtil.nextOccurrence(due, RecurrenceUtil.MONTHLY)))
    }

    @Test
    fun yearly_advancesOneYear() {
        val due = millis(2026, Calendar.JUNE, 27)
        assertEquals(Triple(2027, Calendar.JUNE, 27), parts(RecurrenceUtil.nextOccurrence(due, RecurrenceUtil.YEARLY)))
    }

    @Test
    fun nextFutureOccurrence_skipsMissedOccurrences() {
        val due = millis(2026, Calendar.JUNE, 1)
        val now = millis(2026, Calendar.JUNE, 27)
        val next = RecurrenceUtil.nextFutureOccurrence(due, RecurrenceUtil.DAILY, now)
        assertTrue("next ($next) must be after now ($now)", next > now)
        // First daily occurrence strictly after Jun 27 is Jun 28.
        assertEquals(Triple(2026, Calendar.JUNE, 28), parts(next))
    }

    @Test
    fun nextFutureOccurrence_nonRecurringUnchanged() {
        val due = millis(2026, Calendar.JUNE, 1)
        val now = millis(2026, Calendar.JUNE, 27)
        assertEquals(due, RecurrenceUtil.nextFutureOccurrence(due, RecurrenceUtil.NONE, now))
    }
}
