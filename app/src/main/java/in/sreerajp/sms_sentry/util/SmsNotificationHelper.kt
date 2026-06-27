package `in`.sreerajp.sms_sentry.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import `in`.sreerajp.sms_sentry.MainActivity
import `in`.sreerajp.sms_sentry.R
import `in`.sreerajp.sms_sentry.data.ReminderSms
import `in`.sreerajp.sms_sentry.receiver.NotificationActionReceiver
import `in`.sreerajp.sms_sentry.receiver.ReminderAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsNotificationHelper {
    private const val CHANNEL_ID = "sms_sentry_messages"
    private const val CHANNEL_NAME = "SMS Sentry Messages"
    private const val CHANNEL_DESC = "Receive categorized SMS notifications and OTP actions."

    private const val REMINDER_CHANNEL_ID = "sms_sentry_reminders"
    private const val REMINDER_CHANNEL_NAME = "Reminder Alerts"
    private const val REMINDER_CHANNEL_DESC = "Due-date alerts for reminders."

    /** Extra on the MainActivity launch intent telling the app to open the Reminders tab. */
    const val EXTRA_OPEN_REMINDERS = "in.sreerajp.sms_sentry.OPEN_REMINDERS"

    /** Action + RemoteInput key for inline notification quick-reply. */
    const val ACTION_REPLY = "in.sreerajp.sms_sentry.ACTION_REPLY"
    const val KEY_REPLY_TEXT = "in.sreerajp.sms_sentry.KEY_REPLY_TEXT"

    /** Extra on the MainActivity launch intent telling the app which message to open on tap. */
    const val EXTRA_OPEN_MESSAGE_ID = "in.sreerajp.sms_sentry.OPEN_MESSAGE_ID"

    /**
     * Extra carrying the notification id to dismiss once MainActivity handles the open. Used by
     * the "Open" action button, which launches the Activity directly (no trampoline) and so is
     * not covered by setAutoCancel (that only fires for the content/body tap).
     */
    const val EXTRA_CANCEL_NOTIFICATION_ID = "in.sreerajp.sms_sentry.CANCEL_NOTIFICATION_ID"

    private class ThemeColors(
        val primaryColor: Int,
        val textColorPrimary: Int,
        val textColorSecondary: Int,
        val backgroundColor: Int,
        val circleOutlineResId: Int
    )

    private fun getThemeColors(theme: `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle, isDark: Boolean): ThemeColors {
        return when (theme) {
            `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle.LAVENDER -> {
                if (isDark) {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#D3BBFF"),
                        textColorPrimary = android.graphics.Color.parseColor("#E6E1E9"),
                        textColorSecondary = android.graphics.Color.parseColor("#BDB8C1"),
                        backgroundColor = android.graphics.Color.parseColor("#1F1B2C"),
                        circleOutlineResId = R.drawable.circle_lavender_outline
                    )
                } else {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#6F53A4"),
                        textColorPrimary = android.graphics.Color.parseColor("#1B1A1E"),
                        textColorSecondary = android.graphics.Color.parseColor("#636066"),
                        backgroundColor = android.graphics.Color.parseColor("#F1EDF7"),
                        circleOutlineResId = R.drawable.circle_lavender_outline
                    )
                }
            }
            `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle.SAGE -> {
                if (isDark) {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#A5D6B7"),
                        textColorPrimary = android.graphics.Color.parseColor("#E1E3DF"),
                        textColorSecondary = android.graphics.Color.parseColor("#B8BAB6"),
                        backgroundColor = android.graphics.Color.parseColor("#1B2420"),
                        circleOutlineResId = R.drawable.circle_sage_outline
                    )
                } else {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#386B52"),
                        textColorPrimary = android.graphics.Color.parseColor("#111412"),
                        textColorSecondary = android.graphics.Color.parseColor("#5B5F5D"),
                        backgroundColor = android.graphics.Color.parseColor("#EBEFEA"),
                        circleOutlineResId = R.drawable.circle_sage_outline
                    )
                }
            }
            `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle.SLATE -> {
                if (isDark) {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#96BBD4"),
                        textColorPrimary = android.graphics.Color.parseColor("#E2E6E9"),
                        textColorSecondary = android.graphics.Color.parseColor("#B7BBBE"),
                        backgroundColor = android.graphics.Color.parseColor("#192026"),
                        circleOutlineResId = R.drawable.circle_slate_outline
                    )
                } else {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#32536A"),
                        textColorPrimary = android.graphics.Color.parseColor("#0F1113"),
                        textColorSecondary = android.graphics.Color.parseColor("#5A5D5F"),
                        backgroundColor = android.graphics.Color.parseColor("#E2E9EC"),
                        circleOutlineResId = R.drawable.circle_slate_outline
                    )
                }
            }
            `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle.BLUE -> {
                if (isDark) {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#A6C8E0"),
                        textColorPrimary = android.graphics.Color.parseColor("#E1E6EB"),
                        textColorSecondary = android.graphics.Color.parseColor("#B7BCC1"),
                        backgroundColor = android.graphics.Color.parseColor("#152635"),
                        circleOutlineResId = R.drawable.circle_blue_outline
                    )
                } else {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#2B5EA0"),
                        textColorPrimary = android.graphics.Color.parseColor("#0B1015"),
                        textColorSecondary = android.graphics.Color.parseColor("#575C61"),
                        backgroundColor = android.graphics.Color.parseColor("#E1EBF5"),
                        circleOutlineResId = R.drawable.circle_blue_outline
                    )
                }
            }
            `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle.HIGH_DENSITY -> {
                if (isDark) {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#D3E4FF"),
                        textColorPrimary = android.graphics.Color.parseColor("#E2E2E9"),
                        textColorSecondary = android.graphics.Color.parseColor("#B8B8C0"),
                        backgroundColor = android.graphics.Color.parseColor("#1E2025"),
                        circleOutlineResId = R.drawable.circle_high_density_outline
                    )
                } else {
                    ThemeColors(
                        primaryColor = android.graphics.Color.parseColor("#0061A4"),
                        textColorPrimary = android.graphics.Color.parseColor("#1D1B20"),
                        textColorSecondary = android.graphics.Color.parseColor("#605D64"),
                        backgroundColor = android.graphics.Color.parseColor("#FFFFFF"),
                        circleOutlineResId = R.drawable.circle_high_density_outline
                    )
                }
            }
        }
    }

    private fun getCurrentThemeColors(context: Context): ThemeColors {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isSystemTheme = prefs.getBoolean("is_system_theme", true)
        val isDark = if (isSystemTheme) {
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            prefs.getBoolean("is_dark_theme", false)
        }
        val themeStr = prefs.getString("selected_theme", "LAVENDER") ?: "LAVENDER"
        val theme = try {
            `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle.valueOf(themeStr)
        } catch (e: Exception) {
            `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle.LAVENDER
        }
        return getThemeColors(theme, isDark)
    }

    fun showNotification(context: Context, sender: String, body: String, simId: Int, messageId: Long) {
        // Respect muted senders (set persisted by the ViewModel in theme_prefs).
        val mutedSenders = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .getStringSet("muted_senders", emptySet()) ?: emptySet()
        if (mutedSenders.contains(sender)) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel if on Android O or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Try to extract OTP code (4-8 digits in a text representing verification)
        val containsOtpKeywords = listOf("otp", "verification", "code", "passcode", "pin", "password", "security", "one-time", "secret").any {
            body.lowercase(Locale.getDefault()).contains(it)
        }
        val otp = if (containsOtpKeywords) {
            val regex = Regex("\\b\\d{4,8}\\b")
            regex.find(body)?.value
        } else {
            null
        }

        val notificationId = System.currentTimeMillis().toInt()

        // Content intent: Opens MainActivity (and the tapped message) when notification is tapped
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (messageId > 0) putExtra(EXTRA_OPEN_MESSAGE_ID, messageId)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isOtp = otp != null
        val displayTitle = if (isOtp) "OTP from $sender" else "💬 $sender (SIM $simId)"
        val displayBody = if (isOtp) "$otp" else body

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingContentIntent)
            .setAutoCancel(true)

        if (otp != null) {
            // Set accessibility and wearable fallback contents
            builder.setContentTitle(otp)
            builder.setContentText("OTP from $sender")

            // Custom SMS Organizer style notification layout with RemoteViews
            val remoteViews = RemoteViews(context.packageName, R.layout.notification_otp)
            remoteViews.setTextViewText(R.id.otp_title, otp)
            remoteViews.setTextViewText(R.id.otp_subtitle, "OTP from $sender")

            // Grab and apply theme colors
            val themeColors = getCurrentThemeColors(context)
            remoteViews.setImageViewResource(R.id.circle_background, themeColors.circleOutlineResId)
            
            val iconText = if (sender.trim().isNotEmpty()) {
                sender.trim().take(1).uppercase(Locale.getDefault())
            } else {
                "💬"
            }
            remoteViews.setTextViewText(R.id.circle_icon_text, iconText)
            remoteViews.setTextColor(R.id.circle_icon_text, themeColors.primaryColor)
            remoteViews.setTextColor(R.id.otp_title, themeColors.textColorPrimary)
            remoteViews.setTextColor(R.id.otp_subtitle, themeColors.textColorSecondary)
            
            // Format dynamic action buttons
            remoteViews.setTextColor(R.id.txt_copy, themeColors.primaryColor)
            remoteViews.setTextColor(R.id.txt_delete, themeColors.primaryColor)

            // 1. "Copy" Action Intent
            val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "in.sreerajp.sms_sentry.ACTION_COPY_OTP"
                putExtra("otp_code", otp)
                putExtra("notification_id", notificationId)
                putExtra("sender", sender)
                putExtra("sim_id", simId)
                putExtra("message_id", messageId)
            }
            val pendingCopyIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.btn_copy, pendingCopyIntent)

            // 2. "Delete" Action Intent
            val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "in.sreerajp.sms_sentry.ACTION_DELETE_MESSAGE"
                putExtra("message_id", messageId)
                putExtra("notification_id", notificationId)
            }
            val pendingDeleteIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.btn_delete, pendingDeleteIntent)

            // Inject the Custom RemoteViews both as the normal and big view content
            builder.setCustomContentView(remoteViews)
            builder.setCustomBigContentView(remoteViews)
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        } else {
            builder.setContentTitle(displayTitle)
            builder.setContentText(displayBody)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))

            // Non-OTP default action options
            val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "in.sreerajp.sms_sentry.ACTION_DELETE_MESSAGE"
                putExtra("message_id", messageId)
                putExtra("notification_id", notificationId)
            }
            val pendingDeleteIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Delete", pendingDeleteIntent)

            // "Open" must launch the Activity directly. Routing it through a BroadcastReceiver
            // that then calls startActivity() is a notification trampoline, which Android 12+
            // (targetSdk 31+) silently blocks — so we use getActivity() like the tap intent.
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (messageId > 0) putExtra(EXTRA_OPEN_MESSAGE_ID, messageId)
                putExtra(EXTRA_CANCEL_NOTIFICATION_ID, notificationId)
            }
            val pendingOpenIntent = PendingIntent.getActivity(
                context,
                notificationId + 2,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Open", pendingOpenIntent)

            // Inline quick-reply — only when we can actually send (default SMS app). The reply is
            // sent + persisted by NotificationActionReceiver; the RemoteInput needs a MUTABLE intent.
            if (DefaultSmsAppManager.isDefault(context)) {
                val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_REPLY
                    putExtra("sender", sender)
                    putExtra("sim_id", simId)
                    putExtra("message_id", messageId)
                    putExtra("notification_id", notificationId)
                }
                val pendingReplyIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + 4,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT).setLabel("Reply").build()
                val replyAction = NotificationCompat.Action.Builder(0, "Reply", pendingReplyIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .build()
                builder.addAction(replyAction)
            }
        }

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Post a due-date alert for [reminder] on the dedicated reminders channel. Tapping opens the
     * Reminders tab; "Done" routes to [ReminderAlarmReceiver] to delete the reminder and dismiss.
     */
    fun showReminderNotification(context: Context, reminder: ReminderSms) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = REMINDER_CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Distinct, stable per-reminder id so a re-fire of the same reminder replaces its banner.
        val notificationId = (reminder.id and 0x7FFFFFFF).toInt()

        val dueText = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
            .format(Date(reminder.dueDate))
        val body = buildString {
            append("Due $dueText")
            if (reminder.body.isNotBlank()) {
                append(" • ")
                append(reminder.body.trim())
            }
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_REMINDERS, true)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_DONE
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderAlarmReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pendingDoneIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle("⏰ ${reminder.title}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingContentIntent)
            .setAutoCancel(true)
            .addAction(0, "Done", pendingDoneIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    fun updateNotificationWithCopiedState(
        context: Context,
        sender: String,
        otp: String,
        simId: Int,
        messageId: Long,
        notificationId: Int
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (messageId > 0) putExtra(EXTRA_OPEN_MESSAGE_ID, messageId)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingContentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        // Custom SMS Organizer style notification layout with RemoteViews (Copied Status)
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_otp)
        remoteViews.setTextViewText(R.id.otp_title, otp)
        remoteViews.setTextViewText(R.id.otp_subtitle, "OTP from $sender")
        remoteViews.setTextViewText(R.id.txt_copy, "Copied")

        val themeColors = getCurrentThemeColors(context)
        val greenColor = android.graphics.Color.parseColor("#FF4CAF50")

        remoteViews.setImageViewResource(R.id.circle_background, themeColors.circleOutlineResId)
        
        val iconText = if (sender.trim().isNotEmpty()) {
            sender.trim().take(1).uppercase(Locale.getDefault())
        } else {
            "💬"
        }
        remoteViews.setTextViewText(R.id.circle_icon_text, iconText)
        remoteViews.setTextColor(R.id.circle_icon_text, themeColors.primaryColor)
        remoteViews.setTextColor(R.id.otp_title, themeColors.textColorPrimary)
        remoteViews.setTextColor(R.id.otp_subtitle, themeColors.textColorSecondary)
        
        // Match the design format, using green highlight for Copied and theme-primary for rest
        remoteViews.setTextColor(R.id.txt_copy, greenColor)
        remoteViews.setTextColor(R.id.txt_delete, themeColors.primaryColor)

        val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "in.sreerajp.sms_sentry.ACTION_COPY_OTP"
            putExtra("otp_code", otp)
            putExtra("notification_id", notificationId)
            putExtra("sender", sender)
            putExtra("sim_id", simId)
            putExtra("message_id", messageId)
        }
        val pendingCopyIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.btn_copy, pendingCopyIntent)

        val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "in.sreerajp.sms_sentry.ACTION_DELETE_MESSAGE"
            putExtra("message_id", messageId)
            putExtra("notification_id", notificationId)
        }
        val pendingDeleteIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.btn_delete, pendingDeleteIntent)

        builder.setCustomContentView(remoteViews)
        builder.setCustomBigContentView(remoteViews)
        builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())

        builder.setContentTitle(otp)
        builder.setContentText("OTP from $sender (Copied ✓)")

        notificationManager.notify(notificationId, builder.build())
    }
}
