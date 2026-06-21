package `in`.sreerajp.sms_sentry.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Helpers for checking and requesting the **default SMS app** role.
 *
 * Two-way sync with the system SMS database (writing incoming SMS, deleting, sending) only works
 * while this app holds the role. On API 29+ we request it via [RoleManager.ROLE_SMS]; on older
 * devices via the legacy [Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT] intent.
 */
object DefaultSmsAppManager {

    fun isDefault(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Authoritative on API 29+: ask RoleManager directly. The legacy
            // getDefaultSmsPackage() can disagree with the actual role holder on some
            // devices/OEMs, and we *request* the role via RoleManager — so verify the same way.
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val current = Telephony.Sms.getDefaultSmsPackage(context)
            current != null && current == context.packageName
        }
    }

    /** The package name the system currently reports as the default SMS app (may be null). */
    fun currentDefaultPackage(context: Context): String? =
        Telephony.Sms.getDefaultSmsPackage(context)

    /**
     * Build the intent that asks the user to make this app the default SMS app. The caller should
     * launch it with an ActivityResult launcher and re-check [isDefault] on return.
     */
    fun buildRequestIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            @Suppress("DEPRECATION")
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }
}
