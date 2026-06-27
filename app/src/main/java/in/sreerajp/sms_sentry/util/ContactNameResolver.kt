package `in`.sreerajp.sms_sentry.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves SMS sender phone numbers to their saved address-book display name (and photo).
 *
 * Resolution happens at display time (names/photos are never persisted), so later contact edits
 * are reflected after [clearCache]. Results are cached in-memory; a "no match" caches a result
 * whose name is the original sender so a miss is never re-queried.
 */
object ContactNameResolver {

    /** A contact lookup result. [name] falls back to the raw sender on a miss; [photoUri] is null then. */
    data class ContactInfo(val name: String, val photoUri: Uri?)

    // sender -> info. A miss stores ContactInfo(sender, null) so it is never looked up again.
    private val cache = ConcurrentHashMap<String, ContactInfo>()

    /**
     * Returns the contact display name for [sender], or [sender] unchanged when permission is
     * missing, the sender isn't phone-number-like, or no contact matches.
     */
    fun lookup(context: Context, sender: String): String = resolve(context, sender).name

    /** Returns the saved contact photo for [sender], or null when there's no match / no photo. */
    fun photoUri(context: Context, sender: String): Uri? = resolve(context, sender).photoUri

    /** Full name + photo lookup; both come from a single PhoneLookup query (then cached). */
    fun resolve(context: Context, sender: String): ContactInfo {
        if (sender.isBlank()) return ContactInfo(sender, null)
        cache[sender]?.let { return it }

        // Alphanumeric sender IDs (HDFCBK, VM-AXISBK, short codes) never match PhoneLookup.
        if (!isPhoneNumberLike(sender)) {
            return ContactInfo(sender, null).also { cache[sender] = it }
        }
        if (!hasPermission(context)) return ContactInfo(sender, null)

        val info = queryContact(context, sender) ?: ContactInfo(sender, null)
        cache[sender] = info
        return info
    }

    /** Drop all cached resolutions so the next lookup re-queries (e.g. after a permission grant). */
    fun clearCache() = cache.clear()

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun queryContact(context: Context, number: String): ContactInfo? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: return null
                    val photo = c.getString(1)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    ContactInfo(name, photo)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True when [sender] is plausibly a dialable number (digits with optional + / spaces / dashes). */
    fun isPhoneNumberLike(sender: String): Boolean {
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
