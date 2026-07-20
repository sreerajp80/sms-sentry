package `in`.sreerajp.sms_sentry

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.data.SystemSmsStore
import `in`.sreerajp.sms_sentry.ui.MessageInfo
import `in`.sreerajp.sms_sentry.ui.MessageInfoDialog
import `in`.sreerajp.sms_sentry.ui.SmsOrganizerViewModel
import `in`.sreerajp.sms_sentry.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Covers the "Message info" sheet: the details model, its provider-unavailable path, and rendering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessageInfoTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val message = SMSMessage(
      id = 7,
      sender = "+15551234567",
      body = "Your OTP is 123456",
      timestamp = 1_700_000_000_000L,
      category = "Personal",
      simId = 1,
      isRead = true,
      threadId = 99L,
      // No system-provider row: provider-only fields must come back unavailable.
      systemId = null
  )

  @Test
  fun `loadMessageDetails fills entity fields and marks provider unavailable when no system row`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = SmsOrganizerViewModel(app)

    var result: MessageInfo? = null
    viewModel.loadMessageDetails(message) { result = it }
    // The details load runs on viewModelScope (main dispatcher) — drain it.
    shadowOf(Looper.getMainLooper()).idle()

    assertNotNull(result)
    val info = result!!
    assertFalse("no system row → provider read is unavailable", info.providerAvailable)
    assertEquals("+15551234567", info.sender)
    assertEquals(1_700_000_000_000L, info.dateReceived)
    assertEquals(true, info.read)
    assertEquals(99L, info.threadId)
    assertEquals("None", info.statusLabel)
    assertEquals("SIM 1", info.simLabel)
    // Provider-only fields are all unavailable.
    assertNull(info.dateSent)
    assertNull(info.seen)
    assertNull(info.serviceCenter)
    assertNull(info.protocol)
    assertNull(info.errorCode)
  }

  @Test
  fun `readDetails returns null instead of crashing when the row is absent`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    // Nothing was written to the system provider, so this id has no row.
    assertNull(SystemSmsStore(app).readDetails(123456L))
  }

  @Test
  fun `dialog shows Not available for provider-only fields`() {
    val info = MessageInfo(
        sender = "+15551234567",
        simLabel = "SIM 1",
        dateReceived = 1_700_000_000_000L,
        dateSent = null,
        read = true,
        seen = null,
        threadId = 99L,
        statusLabel = "None",
        subscriptionId = null,
        serviceCenter = null,
        protocol = null,
        errorCode = null,
        providerAvailable = false
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MessageInfoDialog(info = info, onDismiss = {})
      }
    }

    composeTestRule.onNodeWithTag("message_info_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Message info").assertIsDisplayed()
    // The five provider-only fields all render the unavailable placeholder.
    composeTestRule.onAllNodesWithText("Not available").assertCountEquals(5)
  }
}
