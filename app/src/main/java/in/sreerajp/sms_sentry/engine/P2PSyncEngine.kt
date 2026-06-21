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
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

sealed class SyncState {
    object Idle : SyncState()
    data class Hosting(val ipAddress: String, val port: Int, val pin: String) : SyncState()
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

    // Symmetric key encryption helper (AES or fallback custom XOR string cipher)
    // To ensure maximum safety and avoid keystore failures, we will write a highly stable 
    // symmetric cipher using standard SecretKeySpec & Cipher or XOR Base64 string cipher.
    // Let's implement AES encryption with a key padded from the secret PIN!
    private fun encrypt(data: String, pin: String): String {
        return try {
            val keyBytes = pin.padEnd(16, '0').take(16).toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            // Fallback Simple XOR cipher
            xorCipher(data, pin)
        }
    }

    private fun decrypt(encryptedData: String, pin: String): String {
        return try {
            val keyBytes = pin.padEnd(16, '0').take(16).toByteArray(Charsets_UTF_8())
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            String(cipher.doFinal(decodedBytes), Charsets_UTF_8())
        } catch (e: Exception) {
            // Fallback Simple XOR cipher
            try {
                xorCipher(encryptedData, pin)
            } catch (ex: Exception) {
                ""
            }
        }
    }

    private fun Charsets_UTF_8() = Charsets.UTF_8

    private fun xorCipher(text: String, key: String): String {
        val result = StringBuilder()
        for (i in text.indices) {
            result.append((text[i].code xor key[i % key.length].code).toChar())
        }
        return result.toString()
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

                val port = 8243
                val localIp = getLocalIpAddress()
                
                serverSocket = ServerSocket(port)
                _syncState.value = SyncState.Hosting(localIp, port, pin)

                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    _syncState.value = SyncState.Syncing
                    
                    // Handle single sync transaction in a new context
                    handleSyncTransaction(socket, pin, repository)
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Server error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun handleSyncTransaction(socket: Socket, pin: String, repository: SmsRepository) {
        withContext(Dispatchers.IO) {
            var reader: BufferedReader? = null
            var writer: PrintWriter? = null
            try {
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer = PrintWriter(socket.getOutputStream(), true)

                // 1. Read greeting & matching verificationPIN from client
                val clientMessage = reader.readLine() // encrypted: ClientGreeting:PIN
                if (clientMessage == null) {
                    _syncState.value = SyncState.Error("Handshake aborted by client")
                    return@withContext
                }

                val decryptedGreeting = decrypt(clientMessage, pin)
                if (!decryptedGreeting.startsWith("HELLO_SYNC")) {
                    writer.println(encrypt("DENIED", pin))
                    _syncState.value = SyncState.Error("Connection rejected: Invalid PIN challenge")
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
                val encryptedPayload = encrypt(serializedPayload, pin)
                
                writer.println(encrypt("ACCEPT_SYNC", pin))
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
                socket = Socket()
                val address = InetAddress.getByName(hostIp)
                socket.connect(java.net.InetSocketAddress(address, 8243), 6000) // 6s timeout

                writer = PrintWriter(socket.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Step 1: Send Pin validation message
                val greeting = "HELLO_SYNC:$pin"
                val encryptedGreeting = encrypt(greeting, pin)
                writer.println(encryptedGreeting)

                // Step 2: Read accept answer
                val rawChallengeText = reader.readLine()
                if (rawChallengeText == null) {
                    _syncState.value = SyncState.Error("Host closed connection immediately")
                    return@launch
                }

                val decryptedChallenge = decrypt(rawChallengeText, pin)
                if (decryptedChallenge != "ACCEPT_SYNC") {
                    _syncState.value = SyncState.Error("Sync access denied: incorrect PIN code")
                    return@launch
                }

                // Step 3: Parse Payload
                _syncState.value = SyncState.Syncing
                val encryptedPayload = reader.readLine()
                if (encryptedPayload == null) {
                    _syncState.value = SyncState.Error("Empty payload received from host")
                    return@launch
                }

                val decryptedPayload = decrypt(encryptedPayload, pin)
                if (decryptedPayload.isEmpty()) {
                    _syncState.value = SyncState.Error("Decryption failed. Please check the PIN matches")
                    return@launch
                }

                val jsonArr = JSONArray(decryptedPayload)
                var countMerged = 0

                // Import messages incrementally via Repository (runs Classifiers to categorize and update financial state!)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val sender = obj.getString("sender")
                    val body = obj.getString("body")
                    val timestamp = obj.getLong("timestamp")
                    val simId = obj.getInt("simId")

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
