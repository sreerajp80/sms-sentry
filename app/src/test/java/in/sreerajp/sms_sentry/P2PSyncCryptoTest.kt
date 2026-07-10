package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.data.SyncFinance
import `in`.sreerajp.sms_sentry.data.SyncMessage
import `in`.sreerajp.sms_sentry.data.SyncReminder
import `in`.sreerajp.sms_sentry.data.SyncScheduled
import `in`.sreerajp.sms_sentry.data.SyncRule
import `in`.sreerajp.sms_sentry.data.SyncSnapshot
import `in`.sreerajp.sms_sentry.engine.P2PSyncEngine
import `in`.sreerajp.sms_sentry.engine.SyncSelection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Security-relevant unit tests for the P2P sync crypto. Runs under Robolectric because the
 * engine encodes the wire format with `android.util.Base64`. Exercises the [P2PSyncEngine]
 * `internal` test seam (deriveKey / encrypt / decrypt / normalizeCode / newSalt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class P2PSyncCryptoTest {

    private val engine = P2PSyncEngine()

    // Characters that must never appear in a generated code (ambiguous on screen).
    private val forbidden = setOf('0', 'O', '1', 'I', 'L')

    @Test
    fun `generated pairing code has expected length and alphabet`() {
        val code = engine.generatePairingCode()
        assertEquals(P2PSyncEngine.PAIRING_CODE_LEN, code.length)
        for (c in code) {
            assertTrue("unexpected char '$c'", c.isDigit() || c in 'A'..'Z')
            assertTrue("ambiguous char '$c' present", c !in forbidden)
        }
    }

    @Test
    fun `generated pairing codes are not repeated`() {
        val codes = (1..50).map { engine.generatePairingCode() }.toSet()
        // SecureRandom over ~320 bits: collisions are effectively impossible.
        assertEquals(50, codes.size)
    }

    @Test
    fun `encrypt decrypt round-trips under the same code`() {
        val code = engine.generatePairingCode()
        val salt = engine.newSalt()
        val key = engine.deriveKey(code, salt)

        val plaintext = """[{"sender":"BANK","body":"Your OTP is 123456","timestamp":1719500000000}]"""
        val cipher = engine.encrypt(plaintext, key)

        assertNotEquals("ciphertext must not equal plaintext", plaintext, cipher)
        assertEquals(plaintext, engine.decrypt(cipher, key))
    }

    @Test
    fun `each encryption uses a fresh IV so ciphertexts differ`() {
        val key = engine.deriveKey(engine.generatePairingCode(), engine.newSalt())
        val a = engine.encrypt("HELLO_SYNC", key)
        val b = engine.encrypt("HELLO_SYNC", key)
        assertNotEquals("GCM IV reuse — ciphertexts identical", a, b)
        assertEquals("HELLO_SYNC", engine.decrypt(a, key))
        assertEquals("HELLO_SYNC", engine.decrypt(b, key))
    }

    @Test
    fun `wrong code fails GCM tag verification`() {
        // Same salt, different code -> different key -> tag mismatch on decrypt. This is the
        // authentication mechanism: a wrong pairing code is rejected by a thrown exception,
        // never a successful-but-garbage decrypt.
        val salt = engine.newSalt()
        val rightKey = engine.deriveKey("RIGHTCODE", salt)
        val wrongKey = engine.deriveKey("WRONGCODE", salt)

        val cipher = engine.encrypt("ACCEPT_SYNC", rightKey)
        assertThrows(Exception::class.java) { engine.decrypt(cipher, wrongKey) }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val key = engine.deriveKey(engine.generatePairingCode(), engine.newSalt())
        val cipher = engine.encrypt("ACCEPT_SYNC", key)
        // Flip a character in the Base64 body to simulate a network-layer tamper.
        val idx = cipher.length - 2
        val flipped = cipher.substring(0, idx) +
            (if (cipher[idx] == 'A') 'B' else 'A') + cipher.substring(idx + 1)
        assertThrows(Exception::class.java) { engine.decrypt(flipped, key) }
    }

    @Test
    fun `normalizeCode strips separators and uppercases so a typed code matches`() {
        val raw = engine.generatePairingCode()
        val grouped = raw.chunked(P2PSyncEngine.PAIRING_CODE_GROUP).joinToString("-")
        // A user who types the displayed grouped code (with hyphens) in lower case must derive
        // the same normalized secret as the host's raw code.
        assertEquals(raw, engine.normalizeCode(grouped.lowercase()))
        assertEquals(raw, engine.normalizeCode("  $grouped  "))
    }

    private fun sampleSnapshot() = SyncSnapshot(
        messages = listOf(
            SyncMessage("BANK", "debited 500", 1_000L, "Others", 1, false, true, 1, false, 0),
            SyncMessage("MOM", "call me", 2_000L, "Personal", 2, false, false, 2, false, 0)
        ),
        rules = listOf(SyncRule("KEYWORD", "otp", "Personal")),
        finance = listOf(SyncFinance(msgIdx = 0, bankName = "HDFC", amount = 500.0, isCredit = false, balance = 1234.5, timestamp = 1_000L)),
        reminders = listOf(SyncReminder(msgIdx = 1, sender = "MOM", title = "Call", body = "call me", dueDate = 9_000L, isSyncedToCalendar = false, recurrence = "WEEKLY", alertEnabled = true)),
        scheduled = listOf(SyncScheduled("999", "later", 1, 5_000L, 100L))
    )

    @Test
    fun `buildSyncPayload emits a v3 full-sync object that round-trips with linked finance and reminders`() {
        val root = JSONObject(
            engine.buildSyncPayload(sampleSnapshot(), SyncSelection.full(), P2PSyncEngine.SYNC_MODE_FULL, null)
        )

        assertEquals(P2PSyncEngine.PAYLOAD_VERSION, root.getInt("v"))
        assertEquals(P2PSyncEngine.SYNC_MODE_FULL, root.getString("syncMode"))
        val messages = root.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("BANK", messages.getJSONObject(0).getString("sender"))
        assertTrue(messages.getJSONObject(0).getBoolean("isRead"))
        // finance/reminder links reference the message index, not a device-specific row id.
        assertEquals(0, root.getJSONArray("finance").getJSONObject(0).getInt("msgIdx"))
        assertEquals("HDFC", root.getJSONArray("finance").getJSONObject(0).getString("bankName"))
        assertEquals(1, root.getJSONArray("reminders").getJSONObject(0).getInt("msgIdx"))
        assertEquals("WEEKLY", root.getJSONArray("reminders").getJSONObject(0).getString("recurrence"))
        assertEquals("999", root.getJSONArray("scheduled").getJSONObject(0).getString("recipient"))
        // Device-specific columns are never serialized.
        assertFalse(messages.getJSONObject(0).has("systemId"))
        assertFalse(messages.getJSONObject(0).has("attachmentUri"))
    }

    @Test
    fun `buildSyncPayload omits unselected categories but keeps messages when finance is selected`() {
        // Only finance selected: finance travels, messages ride along (they link by index),
        // and everything else is absent so the receiver can tell "not included" from "0 sent".
        val selection = SyncSelection(
            messages = false, rules = false, finance = true,
            reminders = false, scheduled = false, settings = false
        )
        val root = JSONObject(
            engine.buildSyncPayload(sampleSnapshot(), selection, P2PSyncEngine.SYNC_MODE_INCREMENTAL, null)
        )

        assertEquals(P2PSyncEngine.SYNC_MODE_INCREMENTAL, root.getString("syncMode"))
        assertTrue("finance selected -> messages must ride along", root.has("messages"))
        assertTrue(root.has("finance"))
        assertFalse(root.has("rules"))
        assertFalse(root.has("reminders"))
        assertFalse(root.has("scheduled"))
        assertFalse(root.has("settings"))
    }

    @Test
    fun `decrypt rejects a ciphertext shorter than the IV`() {
        val key = engine.deriveKey(engine.generatePairingCode(), engine.newSalt())
        // 4 raw bytes -> shorter than the 12-byte IV; must throw, not IndexOutOfBounds silently.
        val tooShort = android.util.Base64.encodeToString(ByteArray(4), android.util.Base64.NO_WRAP)
        assertThrows(Exception::class.java) { engine.decrypt(tooShort, key) }
    }

    @Test
    fun `host and client derive the same key from the displayed code`() {
        val raw = engine.generatePairingCode()
        val grouped = raw.chunked(P2PSyncEngine.PAIRING_CODE_GROUP).joinToString("-")
        val salt = engine.newSalt()

        val hostKey = engine.deriveKey(engine.normalizeCode(raw), salt)
        val clientKey = engine.deriveKey(engine.normalizeCode(grouped), salt)

        val cipher = engine.encrypt("HELLO_SYNC", hostKey)
        assertEquals("HELLO_SYNC", engine.decrypt(cipher, clientKey))
    }
}
