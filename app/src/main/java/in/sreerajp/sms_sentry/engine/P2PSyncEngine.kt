package `in`.sreerajp.sms_sentry.engine

import android.util.Base64
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class SyncState {
    object Idle : SyncState()
    data class Hosting(val ipAddress: String, val port: Int, val code: String) : SyncState()
    object Connecting : SyncState()
    object Syncing : SyncState()
    data class Completed(val importedCount: Int, val exportedCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class P2PSyncEngine {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val secureRandom = SecureRandom()

    // Authenticated symmetric encryption for the sync channel.
    // The key is derived from a high-entropy pairing code with PBKDF2-HMAC-SHA256 over a
    // per-session random salt, then used with AES/GCM (fresh random IV per message). GCM's
    // auth tag gives both confidentiality and integrity: a wrong code yields a wrong key,
    // which fails tag verification and is rejected cleanly. There is intentionally NO
    // fallback cipher — any crypto failure surfaces as an error rather than a downgrade.
    //
    // The pairing code is ~320 bits (64 chars over a 31-symbol unambiguous alphabet),
    // generated fresh per hosting session and transferred out-of-band (shown on the host,
    // typed into the client) — it never travels over the socket. That high entropy is what
    // makes the scheme safe without a PAKE: an eavesdropper who captures the whole handshake
    // cannot brute-force the code offline, an active MITM cannot establish a session without
    // it, and because the code is per-session there is no long-term key to compromise.
    companion object {
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val GCM_TAG_BITS = 128
        const val PBKDF2_ITERATIONS = 120_000
        const val PBKDF2_KEY_BITS = 256

        // Pairing code: unambiguous alphabet (no 0/O/1/I/L) for reliable transcription.
        const val PAIRING_CODE_LEN = 64
        const val PAIRING_CODE_GROUP = 8
        private const val PAIRING_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        // Wire-protocol bounds: a malicious peer must not be able to OOM us with a giant line.
        private const val MAX_HANDSHAKE_LINE = 4096
        private const val MAX_PAYLOAD_LINE = 64 * 1024 * 1024 // 64 MB
        private const val SOCKET_TIMEOUT_MS = 30_000

        // Imported-payload caps (the peer is untrusted even after authentication).
        private const val MAX_MESSAGES = 100_000
        private const val MAX_FIELD_LEN = 100_000
    }

    // `internal` (not private) on the crypto primitives below is a deliberate test seam:
    // module-local unit tests verify round-trip, wrong-code rejection, and code normalization.
    internal fun newSalt(): ByteArray = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }

    // Generates a fresh high-entropy pairing code for a hosting session.
    fun generatePairingCode(): String {
        val sb = StringBuilder(PAIRING_CODE_LEN)
        repeat(PAIRING_CODE_LEN) {
            sb.append(PAIRING_ALPHABET[secureRandom.nextInt(PAIRING_ALPHABET.length)])
        }
        return sb.toString()
    }

    // Normalizes a code for key derivation: uppercase, then keep only alphabet symbols so a
    // user who types the displayed (hyphen-grouped) code still derives the same key. Both
    // host and client normalize identically, so a correctly-copied code always matches.
    internal fun normalizeCode(input: String): String =
        input.uppercase(Locale.ROOT).filter { it in PAIRING_ALPHABET }

    // Reads a single newline-terminated line, aborting if it exceeds maxLen. Replaces
    // BufferedReader.readLine(), whose buffering is unbounded.
    private fun readBoundedLine(reader: BufferedReader, maxLen: Int): String? {
        val sb = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) break
            if (c == '\r'.code) continue
            sb.append(c.toChar())
            if (sb.length > maxLen) throw java.io.IOException("Line exceeds maximum length")
        }
        return sb.toString()
    }

    internal fun deriveKey(code: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    // Encrypts to Base64.NO_WRAP(IV || ciphertext+tag) — a single line, safe for the
    // line-based (readLine/println) wire protocol.
    internal fun encrypt(data: String, key: SecretKeySpec): String {
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    // Decrypts and verifies the GCM tag. Throws on tamper / wrong key — callers treat a
    // throw as authentication failure (wrong PIN) and abort the sync.
    internal fun decrypt(encoded: String, key: SecretKeySpec): String {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, IV_LEN)
        val ct = all.copyOfRange(IV_LEN, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    // Helper: Obtain Local IPv4 Network address
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        if (sAddr != null) {
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) {
                                return sAddr
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            // ignore
        }
        return "127.0.0.1"
    }

    // Server-Side hosting of DB backup
    fun startHostServer(pin: String, repository: SmsRepository) {
        scope.launch {
            try {
                stopHostServer() // Close existing
                _syncState.value = SyncState.Syncing

                val code = normalizeCode(pin)
                val port = 8243
                val localIp = getLocalIpAddress()

                serverSocket = ServerSocket(port)
                _syncState.value = SyncState.Hosting(localIp, port, code)

                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    _syncState.value = SyncState.Syncing

                    // Handle single sync transaction in a new context
                    handleSyncTransaction(socket, code, repository)
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Server error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun handleSyncTransaction(socket: Socket, code: String, repository: SmsRepository) {
        withContext(Dispatchers.IO) {
            var reader: BufferedReader? = null
            var writer: PrintWriter? = null
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer = PrintWriter(socket.getOutputStream(), true)

                // 0. Generate a per-session salt and send it in the clear, then derive the key.
                val salt = newSalt()
                writer.println(Base64.encodeToString(salt, Base64.NO_WRAP))
                val key = deriveKey(code, salt)

                // 1. Read the client's authenticated greeting. A wrong code gives a wrong key,
                //    so GCM tag verification (decrypt) throws and we reject the connection.
                val clientMessage = readBoundedLine(reader, MAX_HANDSHAKE_LINE)
                if (clientMessage == null) {
                    _syncState.value = SyncState.Error("Handshake aborted by client")
                    return@withContext
                }

                val authenticated = try {
                    decrypt(clientMessage, key) == "HELLO_SYNC"
                } catch (e: Exception) {
                    false
                }
                if (!authenticated) {
                    try { writer.println(encrypt("DENIED", key)) } catch (e: Exception) {}
                    _syncState.value = SyncState.Error("Connection rejected: invalid pairing code")
                    return@withContext
                }

                // 2. Load and serialize data list
                val messages = repository.allMessages.first()
                val jsonArr = JSONArray()
                for (m in messages) {
                    val obj = JSONObject()
                    obj.put("sender", m.sender)
                    obj.put("body", m.body)
                    obj.put("timestamp", m.timestamp)
                    obj.put("category", m.category)
                    obj.put("simId", m.simId)
                    jsonArr.put(obj)
                }

                // Encrypt payload and send
                val serializedPayload = jsonArr.toString()
                val encryptedPayload = encrypt(serializedPayload, key)

                writer.println(encrypt("ACCEPT_SYNC", key))
                writer.println(encryptedPayload)

                _syncState.value = SyncState.Completed(0, messages.size)
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Sync exchange failed: ${e.localizedMessage}")
            } finally {
                try { reader?.close() } catch (ex: Exception) {}
                try { writer?.close() } catch (ex: Exception) {}
                try { socket.close() } catch (ex: Exception) {}
            }
        }
    }

    fun stopHostServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            serverSocket = null
            _syncState.value = SyncState.Idle
        }
    }

    // Client-Side connection and merging
    fun connectAndSync(hostIp: String, pin: String, repository: SmsRepository) {
        scope.launch {
            _syncState.value = SyncState.Connecting
            var socket: Socket? = null
            var writer: PrintWriter? = null
            var reader: BufferedReader? = null

            try {
                val code = normalizeCode(pin)
                socket = Socket()
                val address = InetAddress.getByName(hostIp)
                socket.connect(java.net.InetSocketAddress(address, 8243), 6000) // 6s timeout
                socket.soTimeout = SOCKET_TIMEOUT_MS

                writer = PrintWriter(socket.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Step 0: Read the host's session salt and derive the key.
                val saltLine = readBoundedLine(reader, MAX_HANDSHAKE_LINE)
                if (saltLine == null) {
                    _syncState.value = SyncState.Error("Host closed connection immediately")
                    return@launch
                }
                val key = deriveKey(code, Base64.decode(saltLine, Base64.NO_WRAP))

                // Step 1: Send the authenticated greeting (proves code knowledge via the GCM key).
                writer.println(encrypt("HELLO_SYNC", key))

                // Step 2: Read accept answer. A wrong code means decrypt() throws (tag mismatch).
                val rawChallengeText = readBoundedLine(reader, MAX_HANDSHAKE_LINE)
                if (rawChallengeText == null) {
                    _syncState.value = SyncState.Error("Host closed connection immediately")
                    return@launch
                }

                val accepted = try {
                    decrypt(rawChallengeText, key) == "ACCEPT_SYNC"
                } catch (e: Exception) {
                    false
                }
                if (!accepted) {
                    _syncState.value = SyncState.Error("Sync access denied: incorrect pairing code")
                    return@launch
                }

                // Step 3: Parse Payload
                _syncState.value = SyncState.Syncing
                val encryptedPayload = readBoundedLine(reader, MAX_PAYLOAD_LINE)
                if (encryptedPayload == null) {
                    _syncState.value = SyncState.Error("Empty payload received from host")
                    return@launch
                }

                val decryptedPayload = try {
                    decrypt(encryptedPayload, key)
                } catch (e: Exception) {
                    _syncState.value = SyncState.Error("Decryption failed. Please check the pairing code matches")
                    return@launch
                }

                val jsonArr = JSONArray(decryptedPayload)
                if (jsonArr.length() > MAX_MESSAGES) {
                    _syncState.value = SyncState.Error("Payload too large: ${jsonArr.length()} messages exceeds limit")
                    return@launch
                }
                var countMerged = 0

                // Import messages incrementally via Repository (runs Classifiers to categorize and
                // update financial state!). The peer is untrusted: validate every field before use.
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val sender = obj.getString("sender")
                    val body = obj.getString("body")
                    val timestamp = obj.getLong("timestamp")
                    val simId = obj.getInt("simId")

                    if (sender.length > MAX_FIELD_LEN || body.length > MAX_FIELD_LEN) {
                        _syncState.value = SyncState.Error("Rejected oversized message field from host")
                        return@launch
                    }
                    if (timestamp <= 0L) {
                        _syncState.value = SyncState.Error("Rejected message with invalid timestamp from host")
                        return@launch
                    }

                    repository.processAndInsertMessage(sender, body, timestamp, simId)
                    countMerged++
                }

                _syncState.value = SyncState.Completed(countMerged, 0)
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Sync failure: ${e.localizedMessage}")
            } finally {
                try { reader?.close() } catch (ex: Exception) {}
                try { writer?.close() } catch (ex: Exception) {}
                try { socket?.close() } catch (ex: Exception) {}
            }
        }
    }

    fun resetState() {
        _syncState.value = SyncState.Idle
    }
}
