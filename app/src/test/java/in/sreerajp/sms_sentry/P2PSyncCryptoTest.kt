package `in`.sreerajp.sms_sentry

import `in`.sreerajp.sms_sentry.engine.P2PSyncEngine
import org.junit.Assert.assertEquals
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
