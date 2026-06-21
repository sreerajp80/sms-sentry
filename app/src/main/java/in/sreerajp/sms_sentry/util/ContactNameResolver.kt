package `in`.sreerajp.sms_sentry.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves SMS sender phone numbers to their saved address-book display name.
 *
 * Resolution happens at display time (names are never persisted), so later contact edits are
 * reflected after [clearCache]. Results are cached in-memory; a "no match" caches the original
 * sender as its own value so a miss is never re-queried.
 */
object ContactNameResolver {

    // sender -> resolved name. A miss stores the sender itself so it is never looked up again.
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Returns the contact display name for [sender], or [sender] unchanged when permission is
     * missing, the sender isn't phone-number-like, or no contact matches.
     */
    fun lookup(context: Context, sender: String): String {
        if (sender.isBlank()) return sender
        cache[sender]?.let { return it }

        // Alphanumeric sender IDs (HDFCBK, VM-AXISBK, short codes) never match PhoneLookup.
        if (!looksLikePhoneNumber(sender)) {
            cache[sender] = sender
            return sender
        }
        if (!hasPermission(context)) return sender

        val resolved = queryName(context, sender) ?: sender
        cache[sender] = resolved
        return resolved
    }

    /** Drop all cached resolutions so the next [lookup] re-queries (e.g. after a permission grant). */
    fun clearCache() = cache.clear()

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun queryName(context: Context, number: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True when [sender] is plausibly a dialable number (digits with optional + / spaces / dashes). */
    private fun looksLikePhoneNumber(sender: String): Boolean {
        var digits = 0
        for (ch in sender) {
            when {
                ch.isDigit() -> digits++
                ch == '+' || ch == ' ' || ch == '-' || ch == '(' || ch == ')' -> {}
                else -> return false
            }
        }
        return digits >= 3
    }
}
