package `in`.sreerajp.sms_sentry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import `in`.sreerajp.sms_sentry.ui.SmsOrganizerApp
import `in`.sreerajp.sms_sentry.ui.SmsOrganizerViewModel
import `in`.sreerajp.sms_sentry.ui.theme.MyApplicationTheme
import `in`.sreerajp.sms_sentry.util.SmsNotificationHelper

class MainActivity : ComponentActivity() {
    
    private val viewModel: SmsOrganizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge support is mandatory
        enableEdgeToEdge()

        // If launched from an sms:/smsto: link (e.g. "Message" in Contacts/Dialer), open the composer.
        handleSendIntent(intent)
        // If launched by tapping a message notification, open that message.
        handleOpenMessageIntent(intent)

        setContent {
            // Observe the user-defined theme selections reactively from the ViewModel
            val themeStyle by viewModel.selectedTheme
            val systemThemeEnabled by viewModel.isSystemTheme
            val manualDarkTheme by viewModel.isDarkTheme
            
            val isDark = if (systemThemeEnabled) {
                isSystemInDarkTheme()
            } else {
                manualDarkTheme
            }

            MyApplicationTheme(
                themeStyle = themeStyle,
                darkTheme = isDark
            ) {
                // Surface helps handle background content colors smoothly
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    SmsOrganizerApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
        handleOpenMessageIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the default SMS app from system settings while away.
        viewModel.refreshDefaultStatus()
        // ...or granted READ_PHONE_STATE / swapped a SIM — re-enumerate for the SIM pickers.
        viewModel.refreshActiveSims()
    }

    /** Parse an sms:/smsto:/mms:/mmsto: ACTION_SENDTO/SEND/VIEW intent into a composer prefill. */
    private fun handleSendIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme !in setOf("sms", "smsto", "mms", "mmsto")) return

        // schemeSpecificPart looks like "+15551234" or "+15551234?body=hi" — strip any query.
        val recipient = data.schemeSpecificPart
            ?.substringBefore('?')
            ?.trim()
            .orEmpty()
        val body = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""
        viewModel.requestCompose(recipient, body)
    }

    /** Open the message a tapped notification refers to, if it carries an open-message extra. */
    private fun handleOpenMessageIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(SmsNotificationHelper.EXTRA_OPEN_REMINDERS, false)) {
            viewModel.openRemindersFromNotification()
            intent.removeExtra(SmsNotificationHelper.EXTRA_OPEN_REMINDERS)
        }

        val messageId = intent.getLongExtra(SmsNotificationHelper.EXTRA_OPEN_MESSAGE_ID, -1L)
        if (messageId <= 0) return
        viewModel.openMessageFromNotification(messageId)

        // The "Open" action button launches us directly (no trampoline), so it isn't covered
        // by setAutoCancel — dismiss its notification here if it asked us to.
        val cancelId = intent.getIntExtra(SmsNotificationHelper.EXTRA_CANCEL_NOTIFICATION_ID, -1)
        if (cancelId != -1) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(cancelId)
            intent.removeExtra(SmsNotificationHelper.EXTRA_CANCEL_NOTIFICATION_ID)
        }

        // Consume the extra so config changes / re-deliveries don't re-open it.
        intent.removeExtra(SmsNotificationHelper.EXTRA_OPEN_MESSAGE_ID)
    }
}
