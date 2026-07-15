package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.ui.detectOtp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpDetectorTest {

    @Test
    fun testDetectOtpNormal() {
        // Standard OTP messages
        assertEquals("987152", detectOtp("Your verification OTP code is 987152 for security verification."))
        assertEquals("884213", detectOtp("Your OTP for login is 884213. Do not share it with anyone."))
        assertEquals("1234", detectOtp("Your verification pin is 1234."))
        assertEquals("8842", detectOtp("Enter passcode 8842 to access your account."))
    }

    @Test
    fun testDetectOtpFalsePositives() {
        // User's first screenshot message (No OTP in it, "5000" should not be detected since it's followed by "GB")
        val promoMessage = "Show that you care! Recharge your family member's Jio number 9074579178 with Rs.899 & " +
                "get Exclusive Offer! JioHotstar + Free AI benefits from Google Gemini & 5000 GB storage + " +
                "Unlimited 5G data + 2 GB/day & 20GB, Unlimited Voice, 90 Days. Use Paytm app & code: JIOPAYTM " +
                "to get upto Rs.50 back. T&CA. https://p.paytm.me/xCTH/j6"
        assertNull(detectOtp(promoMessage))

        // Quantity metrics
        assertNull(detectOtp("Verification alert: storage quota is 10000 mAh left."))
        assertNull(detectOtp("Your one-time limit is 50000 Rs."))
        assertNull(detectOtp("Use security code. High speed plan offers 20000 points."))
    }

    @Test
    fun testDetectOtpWrongSelection() {
        // User's second screenshot message
        val epfoMessage = "Dear Member (UAN : XXXX XXXX 8184), OTP to login to Member Interface is 469279 OTP-ID 8817. Do not share with anyone. -EPFO"
        assertEquals("469279", detectOtp(epfoMessage))

        // Masked card suffix and correct OTP
        val cardMessage = "Your transaction of Rs. 100 at Amazon using card ending in 4321 was successful. OTP for transaction is 556677."
        assertEquals("556677", detectOtp(cardMessage))

        // Masked account suffix and correct OTP
        val accountMessage = "OTP to link A/c XXXXXXXX5432 to UPI is 998877."
        assertEquals("998877", detectOtp(accountMessage))
    }

    @Test
    fun testDetectOtpNoKeywords() {
        // Message with numbers but no OTP keywords should return null
        assertNull(detectOtp("Please call me at 987654 or visit room 1024."))
        assertNull(detectOtp("The total cost is 5000 dollars."))
    }
}
