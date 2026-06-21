package `in`.sreerajp.sms_sentry

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.ui.MessageCard
import `in`.sreerajp.sms_sentry.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SMS Sentry", appName)
  }

  @Test
  fun `test OTP detection and showing copy button`() {
    val otpMessage = SMSMessage(
        id = 15,
        sender = "BANK-OTP",
        body = "Your verification OTP code is 987152 for security verification.",
        timestamp = System.currentTimeMillis(),
        category = "Others",
        simId = 1,
        isBlocked = false
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MessageCard(
            msg = otpMessage,
            entityText = null,
            onOpen = {},
            onDelete = {},
            onBlock = {}
        )
      }
    }

    // Verify copy button for the correct OTP 987152 is rendered & displayed
    composeTestRule.onNodeWithTag("copy_otp_button_15").assertIsDisplayed()
    composeTestRule.onNodeWithText("Copy OTP (987152)").assertIsDisplayed()
  }

  @Test
  fun `test normal message does not show copy button`() {
    val normalMessage = SMSMessage(
        id = 16,
        sender = "Friend",
        body = "Hey there, let's meet up today evening!",
        timestamp = System.currentTimeMillis(),
        category = "Personal",
        simId = 1,
        isBlocked = false
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MessageCard(
            msg = normalMessage,
            entityText = null,
            onOpen = {},
            onDelete = {},
            onBlock = {}
        )
      }
    }

    // Copy OTP button should not exist
    composeTestRule.onNodeWithTag("copy_otp_button_16").assertDoesNotExist()
  }
}
