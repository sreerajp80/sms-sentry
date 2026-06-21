package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.data.FinanceTx
import `in`.sreerajp.sms_sentry.ui.latestParsedBalance
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the Dashboard/Accounts "latest parsed balance" against the re-drift that let the
 * two cards disagree (dashboard showed 0.00 while accounts showed the real balance).
 */
class LatestParsedBalanceTest {

    private fun tx(balance: Double, timestamp: Long) =
        FinanceTx(messageId = 0, bankName = "TEST", amount = 0.0,
            isCredit = true, balance = balance, timestamp = timestamp)

    @Test
    fun `skips newest zero-balance row and returns most recent real balance`() {
        // newest-first: a 0.0 (unparsed) row on top, real balance just below it
        val txns = listOf(tx(0.0, 300), tx(85175.0, 200), tx(50000.0, 100))
        assertEquals(85175.0, latestParsedBalance(txns), 0.0)
    }

    @Test
    fun `returns first balance when newest row already carries one`() {
        val txns = listOf(tx(85175.0, 300), tx(50000.0, 200))
        assertEquals(85175.0, latestParsedBalance(txns), 0.0)
    }

    @Test
    fun `returns zero when no row carries a balance`() {
        val txns = listOf(tx(0.0, 200), tx(0.0, 100))
        assertEquals(0.0, latestParsedBalance(txns), 0.0)
    }

    @Test
    fun `returns zero for empty list`() {
        assertEquals(0.0, latestParsedBalance(emptyList()), 0.0)
    }
}
