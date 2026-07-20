package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.engine.SmsClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Covers the CONTACT -> "NotSpam" allowlist behaviour added for the "Not spam" action. */
class SmsClassifierTest {

    private val noRules = emptyList<Pair<String, String>>()

    /** Local midnight epoch millis for the given calendar date (mirrors SimpleDateFormat parse). */
    private fun dateMillis(spec: String): Long =
        SimpleDateFormat("dd MMM yyyy", Locale.ROOT).parse(spec)!!.time

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
        // Allowlist only suppresses Spam; an alphanumeric header is not Personal, so it
        // falls back to Others.
        assertEquals("Others", result.category)
    }

    @Test
    fun `alphanumeric header with no keywords is Others, not Personal`() {
        val result = SmsClassifier.classify(
            sender = "AD-ITDCPC-S",
            body = "Dear LALITHAMBIKA LATHIKA, ITR:4 of 2026 for AFXXXXX has been processed.",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertEquals("Others", result.category)
    }

    @Test
    fun `mobile number sender with no keywords is Personal`() {
        val result = SmsClassifier.classify(
            sender = "+919876543210",
            body = "Amit will call you",
            customKeywords = noRules,
            customContacts = emptyMap()
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
    fun `hyphenated alpha date in a reminder is parsed as the due date`() {
        val ref = dateMillis("01 Jan 2026") // before the expiry, so the date is a future deadline
        val result = SmsClassifier.classify(
            sender = "VM-MoRTH",
            body = "Pollution Under Control Certificate (PUCC) of Vehicle No. KL05BD2314 will " +
                "expire on 16-Jun-2026. Kindly renew the certificate before the expiry date.",
            customKeywords = noRules,
            customContacts = emptyMap(),
            referenceTime = ref
        )
        assertTrue("renew/expiry text should flag a reminder", result.isReminder)
        assertEquals(dateMillis("16 Jun 2026"), result.dueDate)
    }

    @Test
    fun `dot and slash separated alpha dates parse identically`() {
        val ref = dateMillis("01 Jan 2026")
        val expected = dateMillis("16 Jun 2026")
        for (sep in listOf("16.Jun.2026", "16/Jun/2026", "16 Jun 2026")) {
            val result = SmsClassifier.classify(
                sender = "VM-MoRTH",
                body = "PUCC will expire on $sep. Kindly renew before expiry.",
                customKeywords = noRules,
                customContacts = emptyMap(),
                referenceTime = ref
            )
            assertEquals("separator '$sep' should parse", expected, result.dueDate)
        }
    }

    @Test
    fun `unicode dash separated alpha dates parse identically`() {
        // MoRTH/government bulk SMS use en/em dashes, not ASCII hyphens. "16–Jun–2026"
        // is the real-world body that previously fell through to the 3-day fallback.
        val ref = dateMillis("01 Jan 2026")
        val expected = dateMillis("16 Jun 2026")
        for (sep in listOf("16–Jun–2026", "16—Jun—2026", "16−Jun−2026")) {
            val result = SmsClassifier.classify(
                sender = "VM-MoRTH",
                body = "PUCC will expire on $sep. Kindly renew before expiry.",
                customKeywords = noRules,
                customContacts = emptyMap(),
                referenceTime = ref
            )
            assertEquals("separator '$sep' should parse", expected, result.dueDate)
        }
    }

    @Test
    fun `year-less alpha date resolves against the message year`() {
        val ref = dateMillis("01 Mar 2024")
        val result = SmsClassifier.classify(
            sender = "VM-XYZ",
            body = "Your subscription is due on 25 May. Kindly pay before then.",
            customKeywords = noRules,
            customContacts = emptyMap(),
            referenceTime = ref
        )
        assertTrue(result.isReminder)
        val cal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(2024, cal.get(Calendar.YEAR))
    }

    @Test
    fun `reminder with no date falls back to three days from the message time`() {
        val ref = dateMillis("10 Jun 2026")
        val result = SmsClassifier.classify(
            sender = "VM-XYZ",
            body = "Kindly pay your outstanding amount at the earliest.",
            customKeywords = noRules,
            customContacts = emptyMap(),
            referenceTime = ref
        )
        assertTrue(result.isReminder)
        assertEquals(ref + 3 * 24 * 3600 * 1000L, result.dueDate)
    }

    // ---- Spam scoring engine (weighted, sender-aware) --------------------------------------

    @Test
    fun `NPS investment statement is not Spam`() {
        val result = SmsClassifier.classify(
            sender = "VA-PTNNPS",
            body = "Investment value in Tier I (PRANXX7618) as on 31.12.24 is Rs 1,94,742.82. " +
                "For Benefits of e-statement click https://gs.im/PTNNPS/e/gVX8SoGLMYC - Protean",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        // "invest" must never match "Investment" as a substring, and PTNNPS is a trusted sender.
        assertFalse("NPS statement must not be Spam", result.category == "Spam")
    }

    @Test
    fun `SBI e-mandate alert is not Spam`() {
        val result = SmsClassifier.classify(
            sender = "VM-SBIBNK",
            body = "Dear Customer, your e-mandate on SBI Debit card ending 8238 is active. " +
                "Merchant: Amazon, Amount (Rs): 2.00, Frequency: annual.",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertFalse("SBI alert must not be Spam", result.category == "Spam")
    }

    @Test
    fun `insurance policy expiry is a reminder, not Spam`() {
        val result = SmsClassifier.classify(
            sender = "VM-NIALTD",
            body = "Dear Customer, Your MOTOR Policy No. 7601 will expire on 21/06/2026. " +
                "You can renew the policy before then. Please ignore if already renewed. NIACL",
            customKeywords = noRules,
            customContacts = emptyMap(),
            referenceTime = dateMillis("01 Jan 2026")
        )
        assertFalse("insurance renewal must not be Spam", result.category == "Spam")
        assertTrue("renew/expire text should flag a reminder", result.isReminder)
    }

    @Test
    fun `a real prize scam with link and urgency is Spam`() {
        val result = SmsClassifier.classify(
            sender = "VK-WINBIG",
            body = "You won a lottery jackpot! Claim your prize at bit.ly/x now, hurry!",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertEquals("Spam", result.category)
    }

    @Test
    fun `a trusted sender is not Spam over a single scam-ish word`() {
        // One scam word (+3) is outweighed by the trusted-sender penalty (-5).
        val result = SmsClassifier.classify(
            sender = "VM-SBIBNK",
            body = "Learn about crypto options with your SBI account.",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertFalse("trusted sender should dominate a lone scam word", result.category == "Spam")
    }

    @Test
    fun `a lone shortened link is below the spam threshold`() {
        val result = SmsClassifier.classify(
            sender = "VM-XYZAB",
            body = "Please update your details at bit.ly/abc to continue.",
            customKeywords = noRules,
            customContacts = emptyMap()
        )
        assertFalse("a single weak signal must not be Spam", result.category == "Spam")
    }

    @Test
    fun `a user CONTACT to Spam rule still spams a trusted sender`() {
        val result = SmsClassifier.classify(
            sender = "VM-SBIBNK",
            body = "Your account statement is ready.",
            customKeywords = noRules,
            customContacts = mapOf("VM-SBIBNK" to "Spam")
        )
        // Explicit user block rule outranks built-in sender trust.
        assertEquals("Spam", result.category)
        assertTrue(result.isBlocked)
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
