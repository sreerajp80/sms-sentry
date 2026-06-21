package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.engine.SmsClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the CONTACT -> "NotSpam" allowlist behaviour added for the "Not spam" action. */
class SmsClassifierTest {

    private val noRules = emptyList<Pair<String, String>>()

    @Test
    fun `spammy text without allowlist is classified as Spam`() {
        val result = SmsClassifier.classify(
            sender = "PROMO-XY",
            body = "CONGRATULATIONS! You won a prize, claim your reward now!",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertEquals("Spam", result.category)
    }

    @Test
    fun `allowlisted sender with spammy text is not Spam`() {
        val result = SmsClassifier.classify(
            sender = "PROMO-XY",
            body = "CONGRATULATIONS! You won a lottery jackpot, claim it now!",
            customKeywords = noRules,
            customContacts = mapOf("PROMO-XY" to "NotSpam")
        )
        assertEquals("Personal", result.category)
    }

    @Test
    fun `money text surfaces under Others but is flagged as finance`() {
        val result = SmsClassifier.classify(
            sender = "VK-HDFCBK",
            body = "Rs.1495.00 credited to your a/c. Avail bal Rs.5000.00",
            customKeywords = noRules,
            customContacts = mapOf("VK-HDFCBK" to "NotSpam")
        )
        assertEquals("Others", result.category)
        assertTrue("money message should be flagged as finance", result.isFinance)
    }

    @Test
    fun `OTP message classifies as Others`() {
        val result = SmsClassifier.classify(
            sender = "AMZNIN",
            body = "Your OTP for login is 884213. Do not share it with anyone.",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertEquals("Others", result.category)
    }

    @Test
    fun `delivery message classifies as Others`() {
        val result = SmsClassifier.classify(
            sender = "BLUDRT",
            body = "Your order #IN8842 has been shipped and is out for delivery today.",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertEquals("Others", result.category)
    }

    @Test
    fun `marketing message classifies as Promotions`() {
        val result = SmsClassifier.classify(
            sender = "MYNTRA",
            body = "Flat 50% OFF on the End of Season Sale. Shop now with your exclusive coupon!",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertEquals("Promotions", result.category)
    }

    @Test
    fun `coupon saying credited is Promotions and not finance`() {
        val result = SmsClassifier.classify(
            sender = "VM-CLOVIA",
            body = "Rs.300 credited! Your Clovia coupon GK574A8794EDFE gives you flat Rs 300 Off. " +
                "Valid till 11.59 PM only! Shop at http://u3.mnge.co/CLVLNG/5WErQzQ",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertEquals("Promotions", result.category)
        assertFalse("a coupon must not create a ledger entry", result.isFinance)
    }

    @Test
    fun `payment received for a service is a debit, not a credit`() {
        val result = SmsClassifier.classify(
            sender = "VM-MoRTH",
            body = "Your PUC certificate validity is 2027-06-12 and payment received for " +
                "certificate is Rs.100 (excluding GST). (MoRTH)",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertTrue("a payment for a service is still a finance entry", result.isFinance)
        assertEquals(false, result.isCredit)
        assertEquals(100.0, result.amount!!, 0.001)
    }

    @Test
    fun `plain bank credit is still a finance credit`() {
        val result = SmsClassifier.classify(
            sender = "VK-HDFCBK",
            body = "Rs.1495.00 credited to your a/c. Avail bal Rs.5000.00",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertTrue(result.isFinance)
        assertEquals(true, result.isCredit)
    }

    @Test
    fun `legacy rule target category is normalized to Others`() {
        val result = SmsClassifier.classify(
            sender = "SOMEBANK",
            body = "Plain informational text with no keywords",
            customKeywords = listOf("informational" to "Services"),
            customContacts = emptyMap()
        )
        assertEquals("Others", result.category)
    }
}
