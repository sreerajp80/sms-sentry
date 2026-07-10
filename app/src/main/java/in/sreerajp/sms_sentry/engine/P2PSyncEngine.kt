package `in`.sreerajp.sms_sentry.engine

import android.content.Context
import android.util.Base64
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.data.SmsRepository
import `in`.sreerajp.sms_sentry.data.SyncFinance
import `in`.sreerajp.sms_sentry.data.SyncMessage
import `in`.sreerajp.sms_sentry.data.SyncReminder
import `in`.sreerajp.sms_sentry.data.SyncRule
import `in`.sreerajp.sms_sentry.data.ScheduledSms
import `in`.sreerajp.sms_sentry.util.ScheduledSmsScheduler
import `in`.sreerajp.sms_sentry.util.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
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

    /**
     * Host is listening. [clientConnected] flips true once a peer has authenticated and the
     * connection is held open waiting for the sender to choose what to share; [sending] is true
     * while the chosen payload is being pushed.
     */
    data class Hosting(
        val ipAddress: String,
        val port: Int,
        val code: String,
        val clientConnected: Boolean = false,
        val sending: Boolean = false
    ) : SyncState()

    object Connecting : SyncState()

    /** Client has authenticated and is waiting for the sender to pick and push the payload. */
    object WaitingForSender : SyncState()

    object Syncing : SyncState()

    /**
     * [exportedCount] is set on the host (messages shared); [addedCount] / [skippedCount] are
     * set on the client (rows newly added vs. kept because they already existed). [importedCount]
     * mirrors [addedCount] for backward compatibility with the existing UI.
     */
    data class Completed(
        val importedCount: Int,
        val exportedCount: Int,
        val addedCount: Int = 0,
        val skippedCount: Int = 0
    ) : SyncState()

    data class Error(val message: String) : SyncState()
}

/**
 * Which categories the host chooses to push in a selective sync. Finance and reminder rows link
 * to a message by index, so selecting either implies messages are carried too (see
 * [includesMessages]).
 */
data class SyncSelection(
    val messages: Boolean,
    val rules: Boolean,
    val finance: Boolean,
    val reminders: Boolean,
    val scheduled: Boolean,
    val settings: Boolean
) {
    /** Messages must travel whenever finance/reminders do, since those link to a message by index. */
    val includesMessages: Boolean get() = messages || finance || reminders

    /** True when at least one category is selected (used to enable the "Send selected" action). */
    val hasAny: Boolean get() = messages || rules || finance || reminders || scheduled || settings

    companion object {
        fun full() = SyncSelection(
            messages = true, rules = true, finance = true,
            reminders = true, scheduled = true, settings = true
        )
    }
}

class P2PSyncEngine {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val scope = CoroutineScope(Dispatchers.IO)
    private val secureRandom = SecureRandom()

    // --- Held-open host session state (see connect-then-choose below) ---
    private var serverSocket: ServerSocket? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var activeWriter: PrintWriter? = null
    @Volatile private var activeKey: SecretKeySpec? = null
    @Volatile private var authenticated = false
    @Volatile private var stopped = false
    private var idleJob: Job? = null
    private var dropJob: Job? = null
    private var hostIp: String = "127.0.0.1"
    private var hostPort: Int = 0
    private var hostCode: String = ""

    // Authenticated symmetric encryption for the sync channel.
    // The key is derived from a high-entropy pairing code with PBKDF2-HMAC-SHA256 over a
    // per-session random salt, then used with AES/GCM (fresh random IV per message). GCM's
    // auth tag gives both confidentiality and integrity: a wrong code yields a wrong key,
    // which fails tag verification and is rejected cleanly. There is intentionally NO
    // fallback cipher — any crypto failure surfaces as an error rather than a downgrade.
    //
    // The pairing code is ~320 bits (64 chars over a 31-symbol unambiguous alphabet),
    // generated fresh per hosting session and transferred out-of-band (shown on the host,
    // typed or scanned into the client) — it never travels over the socket. That high entropy
    // is what makes the scheme safe without a PAKE.
    companion object {
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val GCM_TAG_BITS = 128
        const val PBKDF2_ITERATIONS = 300_000
        const val PBKDF2_KEY_BITS = 256

        // Pairing code: unambiguous alphabet (no 0/O/1/I/L) for reliable transcription.
        const val PAIRING_CODE_LEN = 64
        const val PAIRING_CODE_GROUP = 8
        private const val PAIRING_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        // Wire-protocol bounds: a malicious peer must not be able to OOM us with a giant line.
        private const val MAX_HANDSHAKE_LINE = 4096
        private const val MAX_PAYLOAD_LINE = 64 * 1024 * 1024 // 64 MB
        private const val SOCKET_TIMEOUT_MS = 30_000

        // After ACCEPT the sender is choosing what to share, so the client waits much longer for
        // the payload than for a single handshake line.
        private const val PAYLOAD_WAIT_MS = 600_000 // 10 min

        // Host auto-stops if no peer authenticates within this window.
        private const val HOST_IDLE_TIMEOUT_MS = 120_000L // 2 min

        // Connect-then-choose full-clone wire format version. v:3 holds the connection open after
        // ACCEPT and pushes a selective, add-only payload. Incompatible with v:2 (immediate-send)
        // and with the old 120k KDF — both phones must run this build.
        const val PAYLOAD_VERSION = 3

        // Imported-payload caps (the peer is untrusted even after authentication).
        private const val MAX_MESSAGES = 100_000
        private const val MAX_RULES = 10_000
        private const val MAX_FINANCE = 100_000
        private const val MAX_REMINDERS = 100_000
        private const val MAX_SCHEDULED = 10_000
        private const val MAX_FIELD_LEN = 100_000

        // Handshake literals (sent ENCRYPTED, never in clear).
        private const val MSG_HELLO = "HELLO_SYNC"
        private const val MSG_ACCEPT = "ACCEPT_SYNC"
        private const val MSG_DENIED = "DENIED"

        const val SYNC_MODE_FULL = "full"
        const val SYNC_MODE_INCREMENTAL = "incremental"
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
    // throw as authentication failure (wrong PIN) and abort the sync. The length guard makes a
    // truncated ciphertext fail cleanly instead of throwing IndexOutOfBounds in copyOfRange.
    internal fun decrypt(encoded: String, key: SecretKeySpec): String {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        if (all.size <= IV_LEN) throw java.io.IOException("Malformed ciphertext")
        val iv = all.copyOfRange(0, IV_LEN)
        val ct = all.copyOfRange(IV_LEN, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    // Serializes the selected parts of a snapshot to the v:3 JSON payload. A category key is
    // present only when it is selected, so the receiver can tell "0 sent" from "not included".
    // `internal` as a test seam so a module-local test can round-trip build → parse.
    internal fun buildSyncPayload(
        snapshot: `in`.sreerajp.sms_sentry.data.SyncSnapshot,
        selection: SyncSelection,
        syncMode: String,
        settings: JSONObject?
    ): String {
        val root = JSONObject()
        root.put("v", PAYLOAD_VERSION)
        root.put("syncMode", syncMode)

        // Messages must be present whenever finance/reminders are, since those link by index.
        if (selection.includesMessages) {
            val msgArr = JSONArray()
            snapshot.messages.forEachIndexed { idx, m ->
                msgArr.put(JSONObject().apply {
                    put("idx", idx)
                    put("sender", m.sender)
                    put("body", m.body)
                    put("timestamp", m.timestamp)
                    put("category", m.category)
                    put("simId", m.simId)
                    put("isBlocked", m.isBlocked)
                    put("isRead", m.isRead)
                    put("type", m.type)
                    put("isMms", m.isMms)
                    put("status", m.status)
                })
            }
            root.put("messages", msgArr)
        }

        if (selection.rules) {
            val ruleArr = JSONArray()
            for (r in snapshot.rules) {
                ruleArr.put(JSONObject().apply {
                    put("type", r.type)
                    put("value", r.value)
                    put("targetCategory", r.targetCategory)
                })
            }
            root.put("rules", ruleArr)
        }

        if (selection.finance) {
            val finArr = JSONArray()
            for (f in snapshot.finance) {
                finArr.put(JSONObject().apply {
                    put("msgIdx", f.msgIdx)
                    put("bankName", f.bankName)
                    put("amount", f.amount)
                    put("isCredit", f.isCredit)
                    put("balance", f.balance)
                    put("timestamp", f.timestamp)
                })
            }
            root.put("finance", finArr)
        }

        if (selection.reminders) {
            val remArr = JSONArray()
            for (r in snapshot.reminders) {
                remArr.put(JSONObject().apply {
                    put("msgIdx", r.msgIdx)
                    put("sender", r.sender)
                    put("title", r.title)
                    put("body", r.body)
                    put("dueDate", r.dueDate)
                    put("isSyncedToCalendar", r.isSyncedToCalendar)
                    put("recurrence", r.recurrence)
                    put("alertEnabled", r.alertEnabled)
                })
            }
            root.put("reminders", remArr)
        }

        if (selection.scheduled) {
            val schArr = JSONArray()
            for (s in snapshot.scheduled) {
                schArr.put(JSONObject().apply {
                    put("recipient", s.recipient)
                    put("body", s.body)
                    put("simId", s.simId)
                    put("scheduledTime", s.scheduledTime)
                    put("createdAt", s.createdAt)
                })
            }
            root.put("scheduled", schArr)
        }

        if (selection.settings && settings != null) {
            root.put("settings", settings)
        }

        return root.toString()
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

    private fun hostingState(clientConnected: Boolean, sending: Boolean = false) =
        SyncState.Hosting(hostIp, hostPort, hostCode, clientConnected, sending)

    // --- Host side: bind a random port, authenticate one client, hold the connection open ---
    fun startHostServer(pin: String, repository: SmsRepository) {
        scope.launch {
            try {
                stopHostServer() // close any prior session (also resets state to Idle)
                stopped = false
                authenticated = false

                hostCode = normalizeCode(pin)
                hostIp = getLocalIpAddress()
                val server = ServerSocket(0) // random OS-assigned port (conflict avoidance, not security)
                serverSocket = server
                hostPort = server.localPort
                _syncState.value = hostingState(clientConnected = false)

                // Idle auto-stop: if nobody authenticates in time, shut the server down.
                idleJob = scope.launch {
                    delay(HOST_IDLE_TIMEOUT_MS)
                    if (!authenticated && !stopped) {
                        _syncState.value = SyncState.Error("No device connected in time")
                        teardown()
                    }
                }

                while (true) {
                    val socket = server.accept() ?: break
                    // Single client at a time: close any extra connection while one is held.
                    if (activeSocket != null) {
                        try { socket.close() } catch (e: Exception) {}
                        continue
                    }
                    handleConnection(socket, hostCode)
                }
            } catch (e: Exception) {
                if (!stopped) _syncState.value = SyncState.Error("Server error: ${e.localizedMessage}")
            }
        }
    }

    // Authenticate one client, ACK immediately, then hold the socket open for the later payload push.
    private suspend fun handleConnection(socket: Socket, code: String) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS

            // 0. Per-session salt in the clear, then derive the key.
            val salt = newSalt()
            writer.println(Base64.encodeToString(salt, Base64.NO_WRAP))
            val key = deriveKey(code, salt)

            // 1. Authenticated greeting. Wrong code → wrong key → decrypt throws → reject, keep listening.
            val clientMessage = readBoundedLine(reader, MAX_HANDSHAKE_LINE)
            if (clientMessage == null) {
                try { socket.close() } catch (e: Exception) {}
                return@withContext
            }
            val ok = try { decrypt(clientMessage, key) == MSG_HELLO } catch (e: Exception) { false }
            if (!ok) {
                try { writer.println(encrypt(MSG_DENIED, key)) } catch (e: Exception) {}
                try { socket.close() } catch (e: Exception) {}
                return@withContext // idle timer still active; wait for the right peer
            }

            // 2. Authenticated: acknowledge NOW, cancel the idle timer, and hold the connection open.
            authenticated = true
            idleJob?.cancel(); idleJob = null
            writer.println(encrypt(MSG_ACCEPT, key))

            socket.soTimeout = 0 // held open indefinitely; the user can Stop, or send when ready
            activeSocket = socket
            activeWriter = writer
            activeKey = key
            _syncState.value = hostingState(clientConnected = true)

            // Watch for a peer drop: a blocking read returns -1 (EOF) or throws when it disconnects.
            dropJob = scope.launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val c = reader.read()
                        if (c == -1) { onPeerDropped(); break }
                        // A well-behaved client sends nothing after HELLO; ignore stray bytes.
                    }
                } catch (e: Exception) {
                    onPeerDropped()
                }
            }
        } catch (e: Exception) {
            try { socket.close() } catch (ex: Exception) {} // pre-auth transport error; keep listening
        }
    }

    // Peer disconnected before we pushed a payload → drop the held socket and wait for a new device.
    private fun onPeerDropped() {
        if (stopped || activeSocket == null) return // our own teardown, or nothing held
        try { activeSocket?.close() } catch (e: Exception) {}
        activeSocket = null
        activeWriter = null
        activeKey = null
        _syncState.value = hostingState(clientConnected = false)
    }

    /**
     * Push the chosen payload over the held connection, then tear the session down (one payload
     * per session). Called from the ViewModel when the sender picks Full or a selective set.
     */
    suspend fun sendToConnectedClient(
        selection: SyncSelection,
        syncMode: String,
        repository: SmsRepository,
        context: Context
    ) = withContext(Dispatchers.IO) {
        val writer = activeWriter
        val key = activeKey
        if (writer == null || key == null) {
            _syncState.value = SyncState.Error("The other device is no longer connected")
            teardown() // no live client; close the server so nothing dangles
            return@withContext
        }
        try {
            _syncState.value = hostingState(clientConnected = true, sending = true)
            val snapshot = repository.collectSyncData()
            val settings = if (selection.settings) SyncSettings.collect(context) else null
            val payload = buildSyncPayload(snapshot, selection, syncMode, settings)
            writer.println(encrypt(payload, key))
            val exported = if (selection.includesMessages) snapshot.messages.size else 0
            _syncState.value = SyncState.Completed(importedCount = 0, exportedCount = exported)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("Sync send failed: ${e.localizedMessage}")
        } finally {
            teardown() // closes sockets/jobs but leaves the Completed/Error state in place
        }
    }

    // Close sockets + cancel jobs without touching the visible state (so a Completed/Error sticks).
    private fun teardown() {
        stopped = true
        idleJob?.cancel(); idleJob = null
        dropJob?.cancel(); dropJob = null
        try { activeSocket?.close() } catch (e: Exception) {}
        activeSocket = null
        activeWriter = null
        activeKey = null
        val server = serverSocket
        serverSocket = null
        if (server != null) { try { server.close() } catch (e: Exception) {} }
        authenticated = false
    }

    fun stopHostServer() {
        teardown()
        _syncState.value = SyncState.Idle
    }

    /**
     * Parse and apply a v:3 payload on the client, **add-only (client-wins)**: any record the
     * receiver already has (matched on a stable natural key) is skipped, never overwritten.
     * Messages are imported first so their new local row ids can re-link finance/reminder rows;
     * finance/reminders are only imported for a message that was newly added (a message the
     * receiver already had keeps its own derived rows). The peer is untrusted even after
     * authentication, so every array is size-capped and every field is validated. On any
     * validation failure this sets an Error state and returns null; otherwise it returns the
     * add-only tally.
     */
    private suspend fun importSyncPayload(
        payload: String,
        repository: SmsRepository,
        appContext: Context
    ): `in`.sreerajp.sms_sentry.data.MergeCounts? {
        val root = try {
            JSONObject(payload)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("Malformed sync payload from host")
            return null
        }

        val version = root.optInt("v", 1)
        if (version != PAYLOAD_VERSION) {
            _syncState.value = SyncState.Error("Incompatible sync version $version — update both phones to the same build")
            return null
        }
        val syncMode = root.optString("syncMode", SYNC_MODE_INCREMENTAL)

        var added = 0
        var skipped = 0

        // 1. Messages first — record each message's local row id and whether it was newly added.
        val msgArr = root.optJSONArray("messages") ?: JSONArray()
        if (msgArr.length() > MAX_MESSAGES) {
            _syncState.value = SyncState.Error("Payload too large: ${msgArr.length()} messages exceeds limit")
            return null
        }
        val localIds = LongArray(msgArr.length())
        val wasAdded = BooleanArray(msgArr.length())
        for (i in 0 until msgArr.length()) {
            val obj = msgArr.getJSONObject(i)
            val sender = obj.optString("sender")
            val body = obj.optString("body")
            val timestamp = obj.optLong("timestamp")
            if (sender.length > MAX_FIELD_LEN || body.length > MAX_FIELD_LEN) {
                _syncState.value = SyncState.Error("Rejected oversized message field from host")
                return null
            }
            if (timestamp <= 0L) {
                _syncState.value = SyncState.Error("Rejected message with invalid timestamp from host")
                return null
            }
            val result = repository.importSyncedMessage(
                SyncMessage(
                    sender = sender,
                    body = body,
                    timestamp = timestamp,
                    category = obj.optString("category", "Others"),
                    simId = obj.optInt("simId", 1),
                    isBlocked = obj.optBoolean("isBlocked", false),
                    isRead = obj.optBoolean("isRead", false),
                    type = obj.optInt("type", SMSMessage.TYPE_INBOX),
                    isMms = obj.optBoolean("isMms", false),
                    status = obj.optInt("status", SMSMessage.STATUS_NONE)
                )
            )
            localIds[i] = result.id
            wasAdded[i] = result.added
            if (result.added) added++ else skipped++
        }

        // 2. Filter rules — add-only.
        val ruleArr = root.optJSONArray("rules") ?: JSONArray()
        if (ruleArr.length() > MAX_RULES) {
            _syncState.value = SyncState.Error("Payload too large: too many filter rules")
            return null
        }
        val rules = ArrayList<SyncRule>(ruleArr.length())
        for (i in 0 until ruleArr.length()) {
            val obj = ruleArr.getJSONObject(i)
            val type = obj.optString("type")
            val value = obj.optString("value")
            val target = obj.optString("targetCategory")
            if (type.length > MAX_FIELD_LEN || value.length > MAX_FIELD_LEN || target.length > MAX_FIELD_LEN) {
                _syncState.value = SyncState.Error("Rejected oversized rule field from host")
                return null
            }
            rules.add(SyncRule(type, value, target))
        }
        val ruleCounts = repository.importSyncedRules(rules)
        added += ruleCounts.added
        skipped += ruleCounts.skipped

        // 3. Finance rows — imported only for a message that was newly added (add-only), re-linked by index.
        val finArr = root.optJSONArray("finance") ?: JSONArray()
        if (finArr.length() > MAX_FINANCE) {
            _syncState.value = SyncState.Error("Payload too large: too many finance rows")
            return null
        }
        for (i in 0 until finArr.length()) {
            val obj = finArr.getJSONObject(i)
            val msgIdx = obj.optInt("msgIdx", -1)
            if (msgIdx !in localIds.indices || !wasAdded[msgIdx]) continue
            val amount = obj.optDouble("amount", 0.0)
            val balance = obj.optDouble("balance", 0.0)
            if (!amount.isFinite() || !balance.isFinite()) {
                _syncState.value = SyncState.Error("Rejected finance row with invalid number")
                return null
            }
            val bank = obj.optString("bankName", "Unknown Bk")
            if (bank.length > MAX_FIELD_LEN) {
                _syncState.value = SyncState.Error("Rejected oversized finance field from host")
                return null
            }
            repository.insertSyncedFinance(
                localIds[msgIdx],
                SyncFinance(msgIdx, bank, amount, obj.optBoolean("isCredit", false), balance, obj.optLong("timestamp"))
            )
            added++
        }

        // 4. Reminders — imported only for a newly-added message, re-linked by index.
        val remArr = root.optJSONArray("reminders") ?: JSONArray()
        if (remArr.length() > MAX_REMINDERS) {
            _syncState.value = SyncState.Error("Payload too large: too many reminders")
            return null
        }
        for (i in 0 until remArr.length()) {
            val obj = remArr.getJSONObject(i)
            val msgIdx = obj.optInt("msgIdx", -1)
            if (msgIdx !in localIds.indices || !wasAdded[msgIdx]) continue
            val sender = obj.optString("sender")
            val title = obj.optString("title", "SMS Reminder")
            val body = obj.optString("body")
            if (sender.length > MAX_FIELD_LEN || title.length > MAX_FIELD_LEN || body.length > MAX_FIELD_LEN) {
                _syncState.value = SyncState.Error("Rejected oversized reminder field from host")
                return null
            }
            repository.insertSyncedReminder(
                localIds[msgIdx],
                SyncReminder(
                    msgIdx = msgIdx,
                    sender = sender,
                    title = title,
                    body = body,
                    dueDate = obj.optLong("dueDate"),
                    isSyncedToCalendar = obj.optBoolean("isSyncedToCalendar", false),
                    recurrence = obj.optString("recurrence", "NONE"),
                    alertEnabled = obj.optBoolean("alertEnabled", true)
                )
            )
            added++
        }

        // 5. Scheduled (future-send) messages — add-only on (recipient, body, scheduledTime); re-arm the alarm.
        val schArr = root.optJSONArray("scheduled") ?: JSONArray()
        if (schArr.length() > MAX_SCHEDULED) {
            _syncState.value = SyncState.Error("Payload too large: too many scheduled messages")
            return null
        }
        val existingScheduled = repository.getAllScheduledOnce()
            .mapTo(HashSet()) { Triple(it.recipient, it.body, it.scheduledTime) }
        val canArm = ScheduledSmsScheduler.canScheduleExact(appContext)
        val now = System.currentTimeMillis()
        for (i in 0 until schArr.length()) {
            val obj = schArr.getJSONObject(i)
            val recipient = obj.optString("recipient")
            val body = obj.optString("body")
            if (recipient.length > MAX_FIELD_LEN || body.length > MAX_FIELD_LEN) {
                _syncState.value = SyncState.Error("Rejected oversized scheduled field from host")
                return null
            }
            val scheduledTime = obj.optLong("scheduledTime")
            val key = Triple(recipient, body, scheduledTime)
            if (key in existingScheduled) {
                skipped++
                continue
            }
            val id = repository.insertScheduled(
                ScheduledSms(
                    recipient = recipient,
                    body = body,
                    simId = obj.optInt("simId", 1),
                    scheduledTime = scheduledTime,
                    createdAt = obj.optLong("createdAt", now)
                )
            )
            existingScheduled.add(key)
            added++
            // Only arm a future send; a past scheduledTime is imported as a record but not fired.
            if (canArm && scheduledTime > now) {
                ScheduledSmsScheduler.schedule(appContext, id, scheduledTime)
            }
        }

        // 6. Settings — allow-listed only; overwrite on a full sync, fill-only on incremental.
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            SyncSettings.apply(appContext, settingsObj, overwrite = syncMode == SYNC_MODE_FULL)
        }

        return `in`.sreerajp.sms_sentry.data.MergeCounts(added, skipped)
    }

    // Client-Side connection and add-only merge. `appContext` is used to apply settings and to
    // re-arm alarms for any synced scheduled (future-send) messages on this device.
    fun connectAndSync(hostIp: String, port: Int, pin: String, repository: SmsRepository, appContext: Context) {
        scope.launch {
            _syncState.value = SyncState.Connecting
            var socket: Socket? = null
            var writer: PrintWriter? = null
            var reader: BufferedReader? = null

            try {
                val code = normalizeCode(pin)
                socket = Socket()
                val address = InetAddress.getByName(hostIp)
                socket.connect(java.net.InetSocketAddress(address, port), 6000) // 6s connect timeout
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
                writer.println(encrypt(MSG_HELLO, key))

                // Step 2: Read accept answer. A wrong code means decrypt() throws (tag mismatch).
                val ans = readBoundedLine(reader, MAX_HANDSHAKE_LINE)
                if (ans == null) {
                    _syncState.value = SyncState.Error("Host closed connection immediately")
                    return@launch
                }
                val accepted = try { decrypt(ans, key) == MSG_ACCEPT } catch (e: Exception) { false }
                if (!accepted) {
                    _syncState.value = SyncState.Error("Sync access denied: incorrect pairing code")
                    return@launch
                }

                // Step 3: Connected — the sender is choosing what to share. Wait (long timeout) for the payload.
                _syncState.value = SyncState.WaitingForSender
                socket.soTimeout = PAYLOAD_WAIT_MS
                val encryptedPayload = readBoundedLine(reader, MAX_PAYLOAD_LINE)
                if (encryptedPayload == null) {
                    _syncState.value = SyncState.Error("The other device closed the connection")
                    return@launch
                }

                _syncState.value = SyncState.Syncing
                val decryptedPayload = try {
                    decrypt(encryptedPayload, key)
                } catch (e: Exception) {
                    _syncState.value = SyncState.Error("Decryption failed. Please check the pairing code matches")
                    return@launch
                }

                val counts = importSyncPayload(decryptedPayload, repository, appContext)
                    ?: return@launch // importSyncPayload already set an Error state

                _syncState.value = SyncState.Completed(
                    importedCount = counts.added,
                    exportedCount = 0,
                    addedCount = counts.added,
                    skippedCount = counts.skipped
                )
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
