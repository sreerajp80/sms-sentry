package `in`.sreerajp.sms_sentry.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Encoding/decoding for the P2P pairing QR code. The host shows a QR carrying its IP, port and
 * one-time pairing code; the client scans it to fill the join form and connect.
 *
 * The code still travels **out-of-band** (read visually off the host's screen), never over the
 * socket — so putting it in a QR does not change the sync security model.
 *
 * Wire form: `smssentry://sync?v=1&ip=<ip>&port=<port>&code=<rawCode>` (values URL-encoded).
 * The `v` marker lets an older/foreign QR be rejected as the wire format evolves.
 */
data class SyncQrData(val ip: String, val port: Int, val code: String)

object SyncQr {

    private const val SCHEME_PREFIX = "smssentry://sync?"
    private const val QR_VERSION = "1"

    /** Build the QR content string. Pure JVM (no Android APIs) so it is unit-testable. */
    fun buildSyncQrContent(ip: String, port: Int, code: String): String {
        val e = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "${SCHEME_PREFIX}v=$QR_VERSION&ip=${e(ip)}&port=$port&code=${e(code)}"
    }

    /**
     * Parse a scanned string. Returns null when it is not a current Sentry sync QR (wrong scheme,
     * missing/mismatched version, missing/blank fields, or a non-numeric port) so the caller can
     * reject foreign or outdated QR codes. Pure JVM (no Android APIs).
     */
    fun parseSyncQrContent(text: String): SyncQrData? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(SCHEME_PREFIX)) return null
        val query = trimmed.substring(SCHEME_PREFIX.length)
        val params = HashMap<String, String>()
        for (pair in query.split("&")) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val key = pair.substring(0, eq)
            val value = try {
                URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
            } catch (e: Exception) {
                return null
            }
            params[key] = value
        }
        if (params["v"] != QR_VERSION) return null // reject a missing/older/foreign QR version
        val ip = params["ip"]?.trim().orEmpty()
        val code = params["code"]?.trim().orEmpty()
        val port = params["port"]?.toIntOrNull() ?: return null
        if (ip.isEmpty() || code.isEmpty() || port <= 0) return null
        return SyncQrData(ip, port, code)
    }

    /** Render [content] as a square QR bitmap of [sizePx] pixels. Requires Android/ZXing. */
    fun encodeQrBitmap(content: String, sizePx: Int): Bitmap =
        BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
}
