package `in`.sreerajp.sms_sentry.util

import android.telephony.SmsMessage

/**
 * SMS segmentation info for composer feedback. Wraps [SmsMessage.calculateLength] so the counter
 * uses the exact same GSM-7 vs UCS-2 logic the radio applies when splitting a long message into
 * multiple billed parts (a single emoji drops the per-part limit from 160 to 70).
 */
object SmsSegment {

    /**
     * @param parts number of SMS the body will be sent as (1 for short messages)
     * @param remainingInCurrent characters left in the current part
     * @param isUnicode true when UCS-2 encoding is in use (non-GSM characters present)
     */
    data class SegInfo(val parts: Int, val remainingInCurrent: Int, val isUnicode: Boolean)

    /** Compute segmentation for [body]. calculateLength returns [parts, used, remaining, encoding]. */
    fun info(body: String): SegInfo {
        if (body.isEmpty()) return SegInfo(parts = 0, remainingInCurrent = 0, isUnicode = false)
        val result = SmsMessage.calculateLength(body, false)
        // result[0] = message count, result[2] = remaining in current message, result[3] = encoding
        val isUnicode = result[3] == 3 // ENCODING_16BIT (UCS-2)
        return SegInfo(parts = result[0], remainingInCurrent = result[2], isUnicode = isUnicode)
    }

    /**
     * Only worth showing a counter once the message spills into multiple parts or is approaching a
     * part boundary — short single-part messages get no clutter.
     */
    fun shouldShow(seg: SegInfo): Boolean =
        seg.parts > 1 || (seg.parts == 1 && seg.remainingInCurrent <= 10)

    /** Compact label like "2 SMS · 18 left" (multi-part) or "12 left" (single part nearing limit). */
    fun label(seg: SegInfo): String =
        if (seg.parts > 1) "${seg.parts} SMS · ${seg.remainingInCurrent} left"
        else "${seg.remainingInCurrent} left"

    /**
     * Always-on character count for the composer: the raw character count, plus segment info once
     * the message splits into multiple SMS. Empty body shows "0".
     */
    fun composerLabel(body: String): String {
        val seg = info(body)
        return if (seg.parts > 1) "${body.length} · ${seg.parts} SMS" else "${body.length}"
    }
}
