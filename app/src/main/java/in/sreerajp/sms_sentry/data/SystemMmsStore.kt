package `in`.sreerajp.sms_sentry.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * Thin wrapper over the system MMS provider (`content://mms` + `content://mms/part`).
 *
 * Reading requires `READ_SMS`; deleting only works while the app is the default SMS app.
 * Actual part/text/media extraction is done by [`in`.sreerajp.sms_sentry.engine.MmsParser].
 */
class SystemMmsStore(private val context: Context) {

    data class SystemMmsRow(
        val systemId: Long,
        val threadId: Long?,
        val date: Long,        // seconds in the MMS provider
        val messageBox: Int,   // 1 = inbox, 2 = sent
        val read: Boolean
    )

    /** Read the MMS header rows (no parts), newest first. */
    fun readAll(): List<SystemMmsRow> {
        val rows = mutableListOf<SystemMmsRow>()
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.READ
        )
        try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Mms.DATE} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Mms._ID)
                val threadIdx = c.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val boxIdx = c.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
                val readIdx = c.getColumnIndexOrThrow(Telephony.Mms.READ)
                while (c.moveToNext()) {
                    rows.add(
                        SystemMmsRow(
                            systemId = c.getLong(idIdx),
                            threadId = if (c.isNull(threadIdx)) null else c.getLong(threadIdx),
                            date = c.getLong(dateIdx),
                            messageBox = c.getInt(boxIdx),
                            read = c.getInt(readIdx) == 1
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_SMS not granted; cannot read system MMS", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read system MMS", e)
        }
        return rows
    }

    /** Delete one MMS (and the provider cascades its parts) by its row id. Only works when default. */
    fun deleteById(systemId: Long): Boolean {
        return try {
            val uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, systemId.toString())
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete system MMS id=$systemId", e)
            false
        }
    }

    companion object {
        private const val TAG = "SystemMmsStore"
    }
}
