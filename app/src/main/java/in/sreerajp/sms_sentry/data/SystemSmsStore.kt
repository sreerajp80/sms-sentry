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
