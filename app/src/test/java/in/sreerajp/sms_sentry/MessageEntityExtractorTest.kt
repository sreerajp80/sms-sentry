package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.engine.EntityType
import `in`.sreerajp.sms_sentry.engine.HighlightTier
import `in`.sreerajp.sms_sentry.engine.MessageEntityExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEntityExtractorTest {

    @Test
    fun testOrderDispatchScreenshot() {
        val body = "Hi, Your order from Bombay Shaving Company is on its way! " +
                "You can track it using tracking No.5001241978590 here https://kcsms.co/BOMSHV/sYFQP7z. " +
                "Thank you for choosing us!"

        val entities = MessageEntityExtractor.extractEntities(body)

        // Must find Tracking ID (Primary) and URL (Secondary)
        val tracking = entities.find { it.type == EntityType.TRACKING_ID }
        assertNotNull("Tracking ID must be found", tracking)
        assertEquals("5001241978590", tracking?.value)
        assertEquals(HighlightTier.PRIMARY, tracking?.tier)

        val url = entities.find { it.type == EntityType.URL }
        assertNotNull("URL must be found", url)
        assertEquals("https://kcsms.co/BOMSHV/sYFQP7z", url?.value)
        assertEquals(HighlightTier.SECONDARY, url?.tier)
    }

    @Test
    fun testAtmWithdrawalScreenshot() {
        val body = "1149 is OTP for your ATM Cash Withdrawal from ATMID S1BM015787004 for Rs.15000 from A/c x0731. " +
                "If not initiated, send BLOCK <last 4 digit of card> to 567676 or call 1800111109 or 09449112211 " +
                "to block your card. Do not share this OTP with anyone. -SBI"

        val entities = MessageEntityExtractor.extractEntities(body)

        // OTP (Primary)
        val otp = entities.find { it.type == EntityType.OTP }
        assertNotNull("OTP must be found", otp)
        assertEquals("1149", otp?.value)
        assertEquals(HighlightTier.PRIMARY, otp?.tier)

        // Amount (Primary)
        val amount = entities.find { it.type == EntityType.AMOUNT }
        assertNotNull("Amount must be found", amount)
        assertTrue(amount!!.value.contains("15000"))
        assertEquals(HighlightTier.PRIMARY, amount.tier)

        // Account (Secondary)
        val account = entities.find { it.type == EntityType.ACCOUNT_NUMBER }
        assertNotNull("Account number must be found", account)
        assertTrue(account!!.value.contains("0731"))
        assertEquals(HighlightTier.SECONDARY, account.tier)

        // Phone numbers / Shortcodes (Secondary)
        val phones = entities.filter { it.type == EntityType.PHONE_NUMBER }
        val phoneValues = phones.map { it.value }
        assertTrue("Must detect toll-free number", phoneValues.contains("1800111109"))
        assertTrue("Must detect mobile number", phoneValues.contains("09449112211"))
        assertTrue("Must detect shortcode", phoneValues.contains("567676"))
        assertTrue(phones.all { it.tier == HighlightTier.SECONDARY })
    }

    @Test
    fun testAwbAndCourierTracking() {
        val body = "Your shipment with AWB 9876543210 has been dispatched. Track at https://track.courier.in/awb"
        val entities = MessageEntityExtractor.extractEntities(body)

        val tracking = entities.find { it.type == EntityType.TRACKING_ID }
        assertNotNull(tracking)
        assertEquals("9876543210", tracking?.value)
        assertEquals(HighlightTier.PRIMARY, tracking?.tier)

        val url = entities.find { it.type == EntityType.URL }
        assertNotNull(url)
        assertEquals("https://track.courier.in/awb", url?.value)
        assertEquals(HighlightTier.SECONDARY, url?.tier)
    }

    @Test
    fun testNonOverlappingSpans() {
        val body = "Order 12345678 dispatched. Link: https://example.com/track/12345678. Contact 1800123456."
        val entities = MessageEntityExtractor.extractEntities(body)

        // Check that ranges do not overlap
        for (i in 0 until entities.size - 1) {
            val curr = entities[i]
            val next = entities[i + 1]
            assertTrue("Spans must not overlap: ${curr.value} vs ${next.value}", curr.end <= next.start)
        }
    }

    @Test
    fun testAnnotatedStringBuilder() {
        val body = "Your OTP is 884213 for Rs. 500 at https://shop.example.com"
        val annotated = MessageEntityExtractor.buildAnnotatedMessageBody(body, isDarkTheme = true)

        assertEquals(body, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
