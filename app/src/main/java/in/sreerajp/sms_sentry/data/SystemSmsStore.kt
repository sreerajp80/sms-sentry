package `in`.sreerajp.sms_sentry.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * Thin wrapper over the system SMS provider (`content://sms`).
 *
 * Reading requires the `READ_SMS` runtime permission. Writing/deleting only succeeds while the
 * app is the **default SMS app** — callers must gate those on [DefaultSmsAppManager.isDefault].
 *
 * MMS lives in a separate provider; see [SystemMmsStore].
 */
class SystemSmsStore(private val context: Context) {

    data class SystemSmsRow(
        val systemId: Long,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int,      // Telephony.Sms.MESSAGE_TYPE_* (1 = inbox, 2 = sent)
        val read: Boolean,
        val threadId: Long?
    )

    /**
     * Extra per-message fields that live only in the system provider — not persisted in our own
     * Room row. All are nullable: the column may be missing on old providers, or the whole read
     * may fail (no `READ_SMS`), in which case the field is `null` and the UI shows "Not available".
     *
     * [status] uses the provider's own codes: -1 none, 0 complete, 32 pending, 64 failed.
     */
    data class SystemSmsDetails(
        val dateReceived: Long?,
        val dateSent: Long?,
        val read: Boolean?,
        val seen: Boolean?,
        val threadId: Long?,
        val status: Int?,
        val subscriptionId: Int?,
        val serviceCenter: String?,
        val protocol: Int?,
        val errorCode: Int?
    )

    /**
     * Read the extended provider fields for a single SMS by its row id. Returns `null` when the
     * row is gone, `READ_SMS` is not granted, or the query fails. Uses non-throwing column lookups
     * because several of these columns are absent on older providers.
     */
    fun readDetails(systemId: Long): SystemSmsDetails? {
        val projection = arrayOf(
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.STATUS,
            Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.PROTOCOL,
            Telephony.Sms.ERROR_CODE
        )
        val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, systemId.toString())
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                fun longOrNull(col: String): Long? {
                    val i = c.getColumnIndex(col)
                    return if (i < 0 || c.isNull(i)) null else c.getLong(i)
                }
                fun intOrNull(col: String): Int? {
                    val i = c.getColumnIndex(col)
                    return if (i < 0 || c.isNull(i)) null else c.getInt(i)
                }
                fun stringOrNull(col: String): String? {
                    val i = c.getColumnIndex(col)
                    return if (i < 0 || c.isNull(i)) null else c.getString(i)
                }
                SystemSmsDetails(
                    dateReceived = longOrNull(Telephony.Sms.DATE),
                    dateSent = longOrNull(Telephony.Sms.DATE_SENT),
                    read = intOrNull(Telephony.Sms.READ)?.let { it == 1 },
                    seen = intOrNull(Telephony.Sms.SEEN)?.let { it == 1 },
                    threadId = longOrNull(Telephony.Sms.THREAD_ID),
                    status = intOrNull(Telephony.Sms.STATUS),
                    subscriptionId = intOrNull(Telephony.Sms.SUBSCRIPTION_ID),
                    serviceCenter = stringOrNull(Telephony.Sms.SERVICE_CENTER),
                    protocol = intOrNull(Telephony.Sms.PROTOCOL),
                    errorCode = intOrNull(Telephony.Sms.ERROR_CODE)
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_SMS not granted; cannot read SMS details", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read SMS details id=$systemId", e)
            null
        }
    }

    /** Read every SMS in the system provider (inbox + sent), newest first. */
    fun readAll(): List<SystemSmsRow> {
        val rows = mutableListOf<SystemSmsRow>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.THREAD_ID
        )
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                val threadIdx = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                while (c.moveToNext()) {
                    rows.add(
                        SystemSmsRow(
                            systemId = c.getLong(idIdx),
                            address = c.getString(addrIdx) ?: "Unknown",
                            body = c.getString(bodyIdx) ?: "",
                            date = c.getLong(dateIdx),
                            type = c.getInt(typeIdx),
                            read = c.getInt(readIdx) == 1,
                            threadId = if (c.isNull(threadIdx)) null else c.getLong(threadIdx)
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_SMS not granted; cannot read system SMS", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read system SMS", e)
        }
        return rows
    }

    /** Write an incoming SMS into the system Inbox. Only works when default. Returns the new row id. */
    fun writeInbox(address: String, body: String, date: Long, read: Boolean = false): Long? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        return insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }

    /** Write an outgoing SMS into the system Sent box. Only works when default. Returns the new row id. */
    fun writeSent(address: String, body: String, date: Long): Long? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        return insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    /** Delete one SMS from the system provider by its row id. Only works when default. */
    fun deleteById(systemId: Long): Boolean {
        return try {
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, systemId.toString())
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete system SMS id=$systemId", e)
            false
        }
    }

    private fun insert(uri: Uri, values: ContentValues): Long? {
        return try {
            val result = context.contentResolver.insert(uri, values) ?: return null
            result.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert into $uri", e)
            null
        }
    }

    companion object {
        private const val TAG = "SystemSmsStore"
    }
}
