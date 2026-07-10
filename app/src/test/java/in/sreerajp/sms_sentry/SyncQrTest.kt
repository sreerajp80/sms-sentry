package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.util.SyncQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the pairing QR content codec ([SyncQr.buildSyncQrContent] /
 * [SyncQr.parseSyncQrContent]). Bitmap encoding needs Android and is not covered here.
 */
class SyncQrTest {

    @Test
    fun `build then parse round-trips ip port and code`() {
        val ip = "192.168.1.45"
        val port = 8243
        val code = "A3KF7QMP239XZ" // subset of the real alphabet; verbatim round-trip is what matters

        val parsed = SyncQr.parseSyncQrContent(SyncQr.buildSyncQrContent(ip, port, code))

        assertEquals(ip, parsed?.ip)
        assertEquals(port, parsed?.port)
        assertEquals(code, parsed?.code)
    }

    @Test
    fun `built QR carries the version marker`() {
        val content = SyncQr.buildSyncQrContent("1.2.3.4", 8243, "ABC")
        assertTrue("QR must carry v=1", content.contains("v=1"))
    }

    @Test
    fun `foreign or malformed strings are rejected`() {
        assertNull(SyncQr.parseSyncQrContent("https://example.com"))
        assertNull(SyncQr.parseSyncQrContent("just some text"))
        assertNull(SyncQr.parseSyncQrContent(""))
        // Right scheme but a non-numeric port is rejected.
        assertNull(SyncQr.parseSyncQrContent("smssentry://sync?v=1&ip=1.2.3.4&port=abc&code=XYZ"))
        // Right scheme but missing code is rejected.
        assertNull(SyncQr.parseSyncQrContent("smssentry://sync?v=1&ip=1.2.3.4&port=8243"))
        // Right scheme + fields but missing/old version is rejected (foreign or outdated QR).
        assertNull(SyncQr.parseSyncQrContent("smssentry://sync?ip=1.2.3.4&port=8243&code=XYZ"))
        assertNull(SyncQr.parseSyncQrContent("smssentry://sync?v=2&ip=1.2.3.4&port=8243&code=XYZ"))
    }
}
