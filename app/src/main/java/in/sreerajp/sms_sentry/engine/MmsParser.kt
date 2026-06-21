package `in`.sreerajp.sms_sentry.engine

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * Reads the text + media parts of a single MMS from `content://mms/part` and resolves the
 * sender address from `content://mms/<id>/addr`. Used both when importing existing MMS and when
 * a freshly downloaded MMS lands in the provider.
 */
object MmsParser {

    data class ParsedMms(
        val sender: String,
        val text: String,
        val attachmentUris: List<String>
    )

    private const val TAG = "MmsParser"
    private val PART_URI: Uri = Uri.parse("content://mms/part")

    fun parse(context: Context, mmsId: Long): ParsedMms {
        val textBuilder = StringBuilder()
        val attachments = mutableListOf<String>()

        try {
            context.contentResolver.query(
                PART_URI,
                arrayOf(
                    Telephony.Mms.Part._ID,
                    Telephony.Mms.Part.CONTENT_TYPE,
                    Telephony.Mms.Part.TEXT
                ),
                "${Telephony.Mms.Part.MSG_ID}=?",
                arrayOf(mmsId.toString()),
                null
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Mms.Part._ID)
                val typeIdx = c.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
                val textIdx = c.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)
                while (c.moveToNext()) {
                    val contentType = c.getString(typeIdx) ?: continue
                    when {
                        contentType == "text/plain" -> {
                            val inline = c.getString(textIdx)
                            if (!inline.isNullOrEmpty()) {
                                textBuilder.append(inline)
                            } else {
                                textBuilder.append(readTextPart(context, c.getLong(idIdx)))
                            }
                        }
                        contentType.startsWith("application/smil") -> {
                            // Presentation layout; ignore.
                        }
                        else -> {
                            // Media part (image/video/audio/etc.) — keep a content:// reference.
                            attachments.add("$PART_URI/${c.getLong(idIdx)}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MMS parts for id=$mmsId", e)
        }

        return ParsedMms(
            sender = readSender(context, mmsId),
            text = textBuilder.toString().trim(),
            attachmentUris = attachments
        )
    }

    private fun readTextPart(context: Context, partId: Long): String {
        return try {
            context.contentResolver.openInputStream(Uri.parse("$PART_URI/$partId"))?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MMS text part id=$partId", e)
            ""
        }
    }

    /** The sender of an inbound MMS is the addr row whose type is FROM (137). */
    private fun readSender(context: Context, mmsId: Long): String {
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        return try {
            context.contentResolver.query(
                addrUri,
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                "${Telephony.Mms.Addr.MSG_ID}=?",
                arrayOf(mmsId.toString()),
                null
            )?.use { c ->
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
                val typeIdx = c.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
                while (c.moveToNext()) {
                    val type = c.getInt(typeIdx)
                    val address = c.getString(addrIdx)
                    // 137 = PduHeaders.FROM
                    if (type == 137 && !address.isNullOrBlank() && address != "insert-address-token") {
                        return address
                    }
                }
                null
            } ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MMS sender for id=$mmsId", e)
            "Unknown"
        }
    }
}
