package `in`.sreerajp.sms_sentry

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.ui.MessageDetailScreen
import `in`.sreerajp.sms_sentry.ui.SmsOrganizerViewModel
import `in`.sreerajp.sms_sentry.ui.shareText
import `in`.sreerajp.sms_sentry.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Covers the "Copy message" and "Share" actions on the message detail screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CopyMessageTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val message = SMSMessage(
      id = 42,
      sender = "Friend",
      body = "Dinner at 8, the place near the station.",
      timestamp = System.currentTimeMillis(),
      category = "Personal",
      simId = 1,
      isBlocked = false
  )

  @Test
  fun `copy message button puts the whole body on the clipboard`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = SmsOrganizerViewModel(app)

    composeTestRule.setContent {
      MyApplicationTheme {
        MessageDetailScreen(viewModel = viewModel, msg = message)
      }
    }

    composeTestRule.onNodeWithTag("copy_message_button_42").assertIsDisplayed().performClick()

    val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString()
    assertEquals(message.body, copied)
  }

  @Test
  fun `share button is shown on the detail screen`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = SmsOrganizerViewModel(app)

    composeTestRule.setContent {
      MyApplicationTheme {
        MessageDetailScreen(viewModel = viewModel, msg = message)
      }
    }

    composeTestRule.onNodeWithTag("share_message_button_42").assertIsDisplayed()
  }

  @Test
  fun `shareText sends the body through a chooser`() {
    val app = ApplicationProvider.getApplicationContext<Application>()

    shareText(app, message.body, "Share message")

    val chooser = shadowOf(app).nextStartedActivity
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("text/plain", send.type)
    assertEquals(message.body, send.getStringExtra(Intent.EXTRA_TEXT))
  }
}
