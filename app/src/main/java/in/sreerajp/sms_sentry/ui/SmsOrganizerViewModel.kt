package `in`.sreerajp.sms_sentry.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.sreerajp.sms_sentry.data.*
import `in`.sreerajp.sms_sentry.engine.MmsParser
import `in`.sreerajp.sms_sentry.engine.P2PSyncEngine
import `in`.sreerajp.sms_sentry.engine.SmsShareUtils
import `in`.sreerajp.sms_sentry.engine.SyncState
import `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle
import `in`.sreerajp.sms_sentry.util.ContactNameResolver
import `in`.sreerajp.sms_sentry.util.DefaultSmsAppManager
import `in`.sreerajp.sms_sentry.util.ReminderAlarmScheduler
import `in`.sreerajp.sms_sentry.util.ScheduledSmsScheduler
import `in`.sreerajp.sms_sentry.util.SimManager
import `in`.sreerajp.sms_sentry.util.SmsNotificationHelper
import `in`.sreerajp.sms_sentry.util.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Prefill values for the composer when launched from an sms:/smsto: intent. */
data class ComposePrefill(val recipient: String, val body: String)

/** Which side of the finance card was tapped to open the contribution breakdown. */
enum class ContribKind { CREDIT, DEBIT }

class SmsOrganizerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SmsDatabase.getDatabase(application)
    private val repository = SmsRepository(db.smsDao)
    private val systemSmsStore = SystemSmsStore(application)
    private val systemMmsStore = SystemMmsStore(application)
    val syncEngine = P2PSyncEngine()

    // Screen State
    var activeTab = mutableStateOf("Dashboard") // Dashboard, Inbox, Accounts, Reminders, Sync, Settings

    // Message detail overlay (null = no message open). Consistent with the activeTab string-switch nav.
    var openedMessage = mutableStateOf<SMSMessage?>(null)

    // Conversation thread overlay (null = no thread open); holds the sender being viewed.
    var openedThread = mutableStateOf<String?>(null)

    // Sender-info overlay (null = closed); holds the sender whose aggregated info is being viewed.
    var openedSenderInfo = mutableStateOf<String?>(null)

    // Contribution breakdown overlay (null = closed); holds which side of the finance card was
    // tapped so the breakdown can list the SMS summed into that month's credit/debit total.
    var openedContribution = mutableStateOf<ContribKind?>(null)

    // Set when the app is launched via an sms:/smsto: intent; opens the composer pre-filled.
    var composePrefill = mutableStateOf<ComposePrefill?>(null)

    // Whether SMS Sentry is currently the device default SMS app. Drives two-way sync + sending.
    var isDefaultSmsApp = mutableStateOf(DefaultSmsAppManager.isDefault(application))
        private set

    // Sending is only possible as the default SMS app.
    val canSendSms: Boolean get() = isDefaultSmsApp.value
    
    // Theme option States
    private val prefs = application.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    var selectedTheme = persistedThemeState("selected_theme", ThemeStyle.LAVENDER) { theme ->
        prefs.edit().putString("selected_theme", theme.name).apply()
    }
    var isDarkTheme = persistedState("is_dark_theme", false) { dark ->
        prefs.edit().putBoolean("is_dark_theme", dark).apply()
    }
    var isSystemTheme = persistedState("is_system_theme", true) { system ->
        prefs.edit().putBoolean("is_system_theme", system).apply()
    }

    // Finance Lock Security
    var isFinanceLocked = mutableStateOf(true)
    var isFinanceAuthenticated = mutableStateOf(false)

    // SIM defaults — persisted so the composer and the headless quick-reply service agree.
    var defaultSmsSim = persistedState("default_sms_sim", "Ask Every Time") { // "SIM 1"/"SIM 2"/"Ask Every Time"
        prefs.edit().putString("default_sms_sim", it).apply()
    }

    // Active SIM subscriptions (empty without READ_PHONE_STATE / on single-SIM). Drives the SIM
    // pickers; the app stores the 1-based slot as simId and resolves the real subId on send.
    private val _activeSims = MutableStateFlow<List<SimManager.SimInfo>>(emptyList())
    val activeSims: StateFlow<List<SimManager.SimInfo>> = _activeSims.asStateFlow()

    // Auto-mark-read delay (seconds) when a conversation is opened. 0 = Off (disabled).
    var autoMarkReadDelaySeconds = persistedState("auto_mark_read_secs", 3) {
        prefs.edit().putInt("auto_mark_read_secs", it).apply()
    }

    // Global on/off for in-app reminder due-alerts (AlarmManager). Default on. Toggling it
    // re-reconciles every reminder's alarm (key must match ReminderAlarmScheduler.PREF_ALERTS_ENABLED).
    var reminderAlertsEnabled = persistedState(ReminderAlarmScheduler.PREF_ALERTS_ENABLED, true) {
        prefs.edit().putBoolean(ReminderAlarmScheduler.PREF_ALERTS_ENABLED, it).apply()
        ReminderAlarmScheduler.reconcile(getApplication(), reminders.value)
    }

    // Muted senders (notifications suppressed) — persisted to theme_prefs as a string set.
    var mutedSenders = mutableStateOf(
        prefs.getStringSet("muted_senders", emptySet())?.toSet() ?: emptySet()
    )
    // Message ids the user has marked as "paid" from the detail smart-card — persisted to theme_prefs.
    var paidMessageIds = mutableStateOf(
        prefs.getStringSet("paid_message_ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    )

    // Unsent per-thread reply text (sender -> draft). Persisted to a dedicated prefs file so a
    // draft survives leaving a thread; surfaced as a "Draft" marker on the inbox conversation card.
    private val draftPrefs = application.getSharedPreferences("drafts", Context.MODE_PRIVATE)
    private val _drafts = MutableStateFlow<Map<String, String>>(
        draftPrefs.all.mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    )
    val drafts: StateFlow<Map<String, String>> = _drafts.asStateFlow()
    
    // Loaded Data Streams
    val allMessages = repository.allMessages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val inboxMessages = repository.inboxMessages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val spamMessages = repository.spamMessages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filterRules = repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders = repository.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val scheduledMessages = repository.scheduledMessages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active connection states
    val syncState = syncEngine.syncState

    // Resolved address-book names for senders (sender -> display name). Only senders with a
    // matching contact appear here; everything else falls back to the raw number in the UI.
    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    // Resolved address-book photos for senders (sender -> photo Uri). Only senders with a saved
    // contact photo appear here; the avatar falls back to a monogram for everything else.
    private val _contactPhotos = MutableStateFlow<Map<String, Uri>>(emptyMap())
    val contactPhotos: StateFlow<Map<String, Uri>> = _contactPhotos.asStateFlow()

    init {
        viewModelScope.launch {
            if (hasReadSmsPermission()) {
                // Real SMS access: import the phone's existing messages.
                importSystemSms()
            }
            // No SMS access yet: leave the inbox empty (no demo seeding).
        }
        // Clear out any reminders whose due date has already passed (one-shot, on launch).
        viewModelScope.launch {
            repository.purgeExpiredReminders()
        }
        // Single arming funnel: re-reconcile reminder due-alerts whenever the set changes.
        viewModelScope.launch {
            reminders.collect { list -> ReminderAlarmScheduler.reconcile(getApplication(), list) }
        }
        // Resolve contact names as messages arrive; cheap after the first pass (resolver caches).
        viewModelScope.launch {
            allMessages.collect { msgs -> resolveContactNames(msgs.map { it.sender }) }
        }
        // Enumerate SIMs up front (no-op without READ_PHONE_STATE; refreshed again on grant/resume).
        refreshActiveSims()
    }

    private fun hasReadSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /** Look up address-book names + photos for [senders] off the main thread and publish matches. */
    private suspend fun resolveContactNames(senders: List<String>) {
        if (!hasReadContactsPermission()) return
        val app = getApplication<Application>()
        val resolved = withContext(Dispatchers.IO) {
            senders.distinct().associateWith { ContactNameResolver.resolve(app, it) }
        }
        _contactNames.value = resolved
            .filter { (sender, info) -> info.name != sender }
            .mapValues { (_, info) -> info.name }
        _contactPhotos.value = resolved
            .mapNotNull { (sender, info) -> info.photoUri?.let { sender to it } }
            .toMap()
    }

    /** Clear cached resolutions and re-resolve current senders (e.g. after a READ_CONTACTS grant). */
    fun refreshContactNames() {
        ContactNameResolver.clearCache()
        viewModelScope.launch { resolveContactNames(allMessages.value.map { it.sender }) }
    }

    /** Re-read whether we are the default SMS app (call from the Activity's onResume). */
    fun refreshDefaultStatus() {
        isDefaultSmsApp.value = DefaultSmsAppManager.isDefault(getApplication())
    }

    /** Re-enumerate active SIM subscriptions (call after a READ_PHONE_STATE grant / onResume). */
    fun refreshActiveSims() {
        _activeSims.value = SimManager.activeSubscriptions(getApplication())
    }

    /**
     * The 1-based slot to send from when the user hasn't picked one (reply row, headless reply).
     * Honors the persisted default-SIM setting; falls back to slot 1.
     */
    fun defaultOutgoingSlot(): Int = when (defaultSmsSim.value) {
        "SIM 2" -> 2
        else -> 1
    }

    // --- Drafts (unsent per-thread reply text) ---
    /** Current draft for [sender], or empty string when none. */
    fun draftFor(sender: String): String = _drafts.value[sender] ?: ""

    /** Persist (or clear, when blank) the draft for [sender]. */
    fun saveDraft(sender: String, text: String) {
        val trimmed = text
        if (trimmed.isBlank()) {
            clearDraft(sender)
            return
        }
        draftPrefs.edit().putString(sender, trimmed).apply()
        _drafts.value = _drafts.value.toMutableMap().apply { put(sender, trimmed) }
    }

    /** Remove any saved draft for [sender] (e.g. after sending). */
    fun clearDraft(sender: String) {
        if (!_drafts.value.containsKey(sender)) return
        draftPrefs.edit().remove(sender).apply()
        _drafts.value = _drafts.value.toMutableMap().apply { remove(sender) }
    }

    // --- Compose-window draft (single most-recent new-message draft: recipient + body) ---
    // Kept in theme_prefs, separate from the per-sender draft map, so a free-form recipient never
    // produces a phantom inbox card. Restored into the composer when it's opened blank.
    fun composeDraft(): Pair<String, String> =
        (prefs.getString("compose_draft_recipient", "") ?: "") to (prefs.getString("compose_draft_body", "") ?: "")

    /** Persist (or clear, when both blank) the new-message composer's unsent recipient + body. */
    fun saveComposeDraft(recipient: String, body: String) {
        if (recipient.isBlank() && body.isBlank()) {
            clearComposeDraft()
            return
        }
        prefs.edit()
            .putString("compose_draft_recipient", recipient)
            .putString("compose_draft_body", body)
            .apply()
    }

    /** Drop the saved compose draft (e.g. after a successful send/schedule). */
    fun clearComposeDraft() {
        prefs.edit().remove("compose_draft_recipient").remove("compose_draft_body").apply()
    }

    /**
     * Import existing SMS + MMS from the system providers into Room (deduped on systemId).
     * No-op without READ_SMS. [onDone] receives the count of newly imported messages.
     */
    fun importSystemSms(onDone: (Int) -> Unit = {}) {
        if (!hasReadSmsPermission()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val rows = withContext(Dispatchers.IO) {
                val smsRows = systemSmsStore.readAll().map { r ->
                    SmsRepository.ImportRow(
                        systemId = r.systemId,
                        sender = r.address,
                        body = r.body,
                        timestamp = r.date,
                        type = if (r.type == android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX)
                            SMSMessage.TYPE_INBOX else SMSMessage.TYPE_SENT,
                        isRead = r.read,
                        threadId = r.threadId,
                        isMms = false,
                        attachmentUri = null
                    )
                }
                val mmsRows = systemMmsStore.readAll().map { r ->
                    val parsed = MmsParser.parse(app, r.systemId)
                    SmsRepository.ImportRow(
                        systemId = r.systemId,
                        sender = parsed.sender,
                        body = parsed.text.ifBlank { "[MMS]" },
                        timestamp = r.date * 1000L, // MMS date is seconds
                        type = if (r.messageBox == 1) SMSMessage.TYPE_INBOX else SMSMessage.TYPE_SENT,
                        isRead = r.read,
                        threadId = r.threadId,
                        isMms = true,
                        attachmentUri = parsed.attachmentUris.joinToString(",").ifBlank { null }
                    )
                }
                smsRows + mmsRows
            }
            val count = repository.importMessages(rows)
            onDone(count)
        }
    }

    // --- Message Simulation (Simulate incoming SMS and instant categorization) ---
    fun simulateSmsReceived(sender: String, body: String, simId: Int) {
        viewModelScope.launch {
            val msgId = repository.processAndInsertMessage(
                sender = sender,
                body = body,
                timestamp = System.currentTimeMillis(),
                simId = simId
            )
            // Trigger native notification with OTP actions
            `in`.sreerajp.sms_sentry.util.SmsNotificationHelper.showNotification(getApplication(), sender, body, simId, msgId)
        }
    }

    // --- Message Composer / SMS sending ---
    /**
     * Send a real SMS via [SmsManager]. Only works when this app is the default SMS app — the
     * fake "insert a Room row" behavior has been removed. When not default, the user is told to
     * set the app as default (the composer Send button should already be disabled).
     */
    fun sendSms(recipient: String, body: String, simId: Int) {
        if (!isDefaultSmsApp.value) {
            Toast.makeText(
                getApplication(),
                "Set SMS Sentry as your default SMS app to send messages.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // Mirror into the system Sent box and persist to Room as "Sending".
            val systemId = systemSmsStore.writeSent(recipient, body, now)
            val msgId = repository.processAndInsertMessage(
                sender = recipient,
                body = body,
                timestamp = now,
                simId = simId,
                systemId = systemId,
                type = SMSMessage.TYPE_SENT,
                isRead = true,
                status = SMSMessage.STATUS_SENDING
            )
            dispatchSms(recipient, body, msgId, simId)
        }
    }

    /**
     * Fire the actual radio send with sent/delivery PendingIntents carrying the Room message id.
     * [slot] is the 1-based SIM slot the user chose; it is resolved to a real subscriptionId here.
     */
    private fun dispatchSms(recipient: String, body: String, msgId: Long, slot: Int) {
        try {
            val subId = SimManager.subscriptionIdForSlot(getApplication(), slot)
            SmsSender.dispatch(getApplication(), recipient, body, msgId, subId)
        } catch (e: Exception) {
            viewModelScope.launch { repository.updateMessageStatus(msgId, SMSMessage.STATUS_FAILED) }
            Toast.makeText(getApplication(), "Failed to send: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Retry a previously-FAILED outgoing message: flip it back to SENDING and re-dispatch. */
    fun resendMessage(msg: SMSMessage) {
        if (msg.type != SMSMessage.TYPE_SENT) return
        if (!isDefaultSmsApp.value) {
            Toast.makeText(getApplication(), "Set SMS Sentry as your default SMS app to send messages.", Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch {
            repository.updateMessageStatus(msg.id, SMSMessage.STATUS_SENDING)
            dispatchSms(msg.sender, msg.body, msg.id, msg.simId)
        }
    }

    // --- Scheduled (future-delivery) SMS ---
    /** True when the platform currently allows exact alarms (required to schedule delivery). */
    fun canScheduleExactAlarms(): Boolean = ScheduledSmsScheduler.canScheduleExact(getApplication())

    /**
     * Persist a future-delivery SMS and arm its exact alarm. Exact-only: caller must have
     * verified [canScheduleExactAlarms]; if it became false, this no-ops and returns false.
     * Returns false (and toasts) for a non-future [scheduledTime].
     */
    fun scheduleSms(recipient: String, body: String, simId: Int, scheduledTime: Long): Boolean {
        if (scheduledTime <= System.currentTimeMillis()) {
            Toast.makeText(getApplication(), "Pick a time in the future.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!canScheduleExactAlarms()) return false
        viewModelScope.launch {
            val id = repository.insertScheduled(
                ScheduledSms(
                    recipient = recipient,
                    body = body,
                    simId = simId,
                    scheduledTime = scheduledTime,
                    createdAt = System.currentTimeMillis()
                )
            )
            ScheduledSmsScheduler.schedule(getApplication(), id, scheduledTime)
        }
        return true
    }

    /** Cancel a pending scheduled message: cancel its alarm and delete the row. */
    fun cancelScheduled(id: Long) {
        ScheduledSmsScheduler.cancel(getApplication(), id)
        viewModelScope.launch { repository.deleteScheduledById(id) }
    }

    /** Build the intent that opens the system "exact alarm" settings screen for this app. */
    fun buildExactAlarmSettingsIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:" + getApplication<Application>().packageName)
        }

    // Delete a single message — also removes it from the system provider when we are default.
    fun deleteMessage(msg: SMSMessage) {
        viewModelScope.launch {
            deleteFromSystemIfDefault(msg)
            repository.deleteMessage(msg)
        }
    }

    /** Delete an arbitrary set of selected messages (thread multi-select), syncing to the provider. */
    fun deleteMessages(msgs: List<SMSMessage>) {
        viewModelScope.launch {
            for (m in msgs) {
                deleteFromSystemIfDefault(m)
                repository.deleteMessage(m)
            }
        }
    }

    /** Remove a message's matching row from the system SMS/MMS provider when we are default. */
    private suspend fun deleteFromSystemIfDefault(msg: SMSMessage) {
        val systemId = msg.systemId ?: return
        if (!isDefaultSmsApp.value) return
        withContext(Dispatchers.IO) {
            if (msg.isMms) systemMmsStore.deleteById(systemId)
            else systemSmsStore.deleteById(systemId)
        }
    }

    // --- Conversation thread navigation ---
    fun openThread(sender: String) { openedThread.value = sender }
    fun closeThread() { openedThread.value = null }

    /**
     * If [recipient] already has a conversation, return that thread's canonical sender key;
     * otherwise null. Lets the composer fold a new message into an existing thread instead of
     * staying a separate "New message" screen. Phone numbers match on their trailing digits so
     * `9496135390`, `+91 94961 35390`, etc. resolve to the same thread; alphanumeric sender IDs
     * (e.g. HDFCBK) match case-insensitively. Name-typed-as-recipient is intentionally not matched.
     */
    fun existingThreadFor(recipient: String): String? {
        val needle = recipient.trim()
        if (needle.isBlank()) return null
        val byNumber = ContactNameResolver.isPhoneNumberLike(needle)
        val needleTail = if (byNumber) digitsTail(needle) else ""
        return allMessages.value.asSequence()
            .map { it.sender }
            .distinct()
            .firstOrNull { sender ->
                if (byNumber && ContactNameResolver.isPhoneNumberLike(sender)) {
                    needleTail.isNotEmpty() && digitsTail(sender) == needleTail
                } else {
                    sender.trim().equals(needle, ignoreCase = true)
                }
            }
    }

    /** Digits of [s] only, keeping the trailing 10 (or all when shorter) for number comparison. */
    private fun digitsTail(s: String): String {
        val digits = s.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    // --- Sender info navigation ---
    fun openSenderInfo(sender: String) { openedSenderInfo.value = sender }
    fun closeSenderInfo() { openedSenderInfo.value = null }

    /** True when [sender] is a phone number with no saved contact — eligible for "Add to contacts". */
    fun isUnknownContact(sender: String): Boolean =
        ContactNameResolver.isPhoneNumberLike(sender) && !_contactNames.value.containsKey(sender)

    /**
     * Hand off to the system Contacts app to create/link a contact for [sender]. Uses
     * ACTION_INSERT_OR_EDIT so the user can attach the number to an existing contact too. Needs no
     * WRITE_CONTACTS; works whether or not we're the default SMS app.
     */
    fun addToContacts(sender: String) {
        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, sender)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "No contacts app available", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Compose-from-intent (sms:/smsto: links) ---
    fun requestCompose(recipient: String, body: String) {
        composePrefill.value = ComposePrefill(recipient, body)
    }
    fun clearComposePrefill() { composePrefill.value = null }

    // --- Message detail navigation ---
    fun openMessage(msg: SMSMessage) {
        openedMessage.value = msg
        // Opening a message marks it read; the reactive inbox Flow updates the card behind it.
        if (!msg.isRead) {
            viewModelScope.launch { repository.markAsRead(msg.id) }
        }
    }
    fun closeMessage() { openedMessage.value = null }

    // --- Contribution breakdown navigation (tap income/expense on the finance card) ---
    fun openContribution(kind: ContribKind) { openedContribution.value = kind }
    fun closeContribution() { openedContribution.value = null }

    /**
     * Open the source SMS behind a finance/ledger row by its messageId. No-op (the row simply
     * does nothing) if the underlying SMS was deleted; reuses [openMessage], so it also marks the
     * message read.
     */
    fun openMessageById(messageId: Long) {
        viewModelScope.launch {
            repository.getMessageById(messageId)?.let { openMessage(it) }
        }
    }

    /**
     * Open a message from a notification tap: surfaces it over the Inbox so pressing Back
     * returns to the Inbox (where the message lives) rather than the default Dashboard tab.
     * Distinct from [openMessageById], which finance/ledger rows reuse to return to Accounts.
     */
    fun openMessageFromNotification(messageId: Long) {
        activeTab.value = "Inbox"
        openMessageById(messageId)
    }

    /** Switch to the Reminders tab (used when a reminder due-alert notification is tapped). */
    fun openRemindersFromNotification() {
        activeTab.value = "Reminders"
    }

    // --- Block / Report / Delete from the card or detail screen ---
    /** Delete every message from a sender (the whole conversation), syncing to the provider. */
    fun deleteConversation(sender: String) {
        viewModelScope.launch {
            val msgs = allMessages.value.filter { it.sender == sender }
            for (m in msgs) {
                deleteFromSystemIfDefault(m)
                repository.deleteMessage(m)
            }
        }
    }

    /** Mark every message from a sender as read. */
    fun markConversationRead(sender: String) {
        viewModelScope.launch {
            allMessages.value
                .filter { it.sender == sender && !it.isRead }
                .forEach { repository.markAsRead(it.id) }
        }
    }

    /** Mark every inbound message from a sender as unread (manual override). */
    fun markConversationUnread(sender: String) {
        viewModelScope.launch {
            allMessages.value
                .filter { it.sender == sender && it.isRead && it.type == SMSMessage.TYPE_INBOX }
                .forEach { repository.markAsUnread(it.id) }
        }
    }

    /** Mark specific messages read (per-message selection action). */
    fun markMessagesRead(ids: Collection<Long>) {
        viewModelScope.launch { ids.forEach { repository.markAsRead(it) } }
    }

    /** Mark specific messages unread (per-message selection action). */
    fun markMessagesUnread(ids: Collection<Long>) {
        viewModelScope.launch { ids.forEach { repository.markAsUnread(it) } }
    }

    /** Block a sender (add a CONTACT->Spam rule) and clear their current conversation. */
    fun blockConversation(sender: String) {
        viewModelScope.launch {
            repository.addRule("CONTACT", sender, "Spam")
        }
        deleteConversation(sender)
    }

    /** Report as spam: move the message to the Spam folder and remember the sender. */
    fun reportSpam(msg: SMSMessage) {
        viewModelScope.launch {
            repository.reportSpam(msg)
            repository.addRule("CONTACT", msg.sender, "Spam")
        }
    }

    /** Report a whole conversation as spam: move every message from the sender to Spam + remember it. */
    fun reportSpamSender(sender: String) {
        viewModelScope.launch {
            val msgs = allMessages.value.filter { it.sender == sender }
            for (m in msgs) {
                repository.reportSpam(m)
            }
            repository.addRule("CONTACT", sender, "Spam")
        }
    }

    /**
     * Move a whole conversation out of spam: allowlist the sender (so future messages aren't
     * auto-spammed) and then re-classify each of their existing Spam messages. Reverse of
     * [reportSpamSender].
     */
    fun markNotSpamSender(sender: String) {
        viewModelScope.launch {
            repository.allowlistSender(sender)
            val msgs = allMessages.value.filter { it.sender == sender && it.category == "Spam" }
            for (m in msgs) {
                repository.restoreFromSpam(m)
            }
        }
    }

    /**
     * Manually move a whole conversation (every message from [sender]) into [targetCategory]
     * (Personal / Promotions / Others / Spam), a user override of the classifier. Moving to Spam
     * also adds a CONTACT->Spam rule so the sender's future messages are auto-spammed, but the
     * existing messages stay visible in the Spam folder (it does NOT delete the conversation,
     * unlike [blockConversation]).
     */
    fun moveConversationToCategory(sender: String, targetCategory: String) {
        viewModelScope.launch {
            val msgs = allMessages.value.filter { it.sender == sender }
            for (m in msgs) {
                repository.moveMessageToCategory(m, targetCategory)
            }
            if (targetCategory == "Spam") {
                repository.addRule("CONTACT", sender, "Spam")
            }
        }
    }

    // --- Mute notifications for a sender ---
    fun isMuted(sender: String): Boolean = mutedSenders.value.contains(sender)

    fun toggleMute(sender: String) {
        val next = mutedSenders.value.toMutableSet().apply {
            if (!add(sender)) remove(sender)
        }
        mutedSenders.value = next
        prefs.edit().putStringSet("muted_senders", next).apply()
    }

    // --- Mark a finance/reminder message as paid (detail smart card) ---
    fun isPaid(id: Long): Boolean = paidMessageIds.value.contains(id)

    fun togglePaid(id: Long) {
        val next = paidMessageIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        paidMessageIds.value = next
        prefs.edit().putStringSet("paid_message_ids", next.map { it.toString() }.toSet()).apply()
    }

    /** Create a reminder from a message (detail smart card "Add reminder"). */
    fun addReminderForMessage(msg: SMSMessage, title: String, dueDate: Long) {
        viewModelScope.launch {
            repository.addReminderForMessage(msg, title, dueDate)
        }
    }

    // Delete a reminder
    fun deleteReminder(id: Long) {
        ReminderAlarmScheduler.cancel(getApplication(), id)
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    /** Toggle the in-app due-alert for a reminder. The reminders Flow re-reconciles the alarm. */
    fun setReminderAlert(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setReminderAlert(id, enabled)
        }
    }

    /** Change a reminder's recurrence cadence (see [ReminderAlarmScheduler]/RecurrenceUtil values). */
    fun setReminderRecurrence(id: Long, recurrence: String) {
        viewModelScope.launch {
            repository.setReminderRecurrence(id, recurrence)
        }
    }

    // --- Save Rule ---
    fun addRule(type: String, query: String, category: String) {
        viewModelScope.launch {
            repository.addRule(type, query, category)
        }
    }

    // Delete Rule
    fun removeRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    // Clear all DB data
    fun clearAllSms() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    /**
     * Re-run the classifier over every stored message and rewrite it onto the current four
     * categories (Personal / Promotions / Others / Spam), rebuilding the finance ledger and
     * filling in any missing reminders. Triggered from Settings. [onDone] gets the count.
     */
    fun recategorizeAllMessages(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = repository.recategorizeAllMessages()
            onDone(count)
        }
    }

    // --- Share Utility integrations ---
    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            val list = repository.allMessages.first()
            val csv = SmsShareUtils.messagesToCsv(list)
            SmsShareUtils.shareText(context, "SMS Organizer CSV Export", csv)
        }
    }

    fun exportToJson(context: Context) {
        viewModelScope.launch {
            val list = repository.allMessages.first()
            val json = SmsShareUtils.messagesToJson(list)
            SmsShareUtils.shareText(context, "SMS Organizer JSON Export", json)
        }
    }

    fun importCsv(context: Context, text: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val count = SmsShareUtils.importFromCsv(text, repository)
                onComplete(count)
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                onComplete(0)
            }
        }
    }

    fun importJson(context: Context, text: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val count = SmsShareUtils.importFromJson(text, repository)
                onComplete(count)
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                onComplete(0)
            }
        }
    }

    // --- Peer to Peer Sync Triggers ---
    fun generatePairingCode(): String = syncEngine.generatePairingCode()

    fun hostSyncServer(pin: String) {
        syncEngine.startHostServer(pin, repository)
    }

    fun joinSyncServer(ip: String, pin: String) {
        syncEngine.connectAndSync(ip, pin, repository)
    }

    fun stopHosting() {
        syncEngine.stopHostServer()
    }

    fun resetSyncState() {
        syncEngine.resetState()
    }

    fun getLocalIp(): String {
        return syncEngine.getLocalIpAddress()
    }

    // --- Set Calendar Event Trigger ---
    fun addEventToCalendar(context: Context, reminder: ReminderSms) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, reminder.title)
                putExtra(CalendarContract.Events.DESCRIPTION, reminder.body)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, reminder.dueDate)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, reminder.dueDate + 3600 * 1000) // 1 Hour
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            
            // Mark as synced to display status checkmark
            viewModelScope.launch {
                repository.updateReminderSyncState(reminder.id, true)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Calendar app not available: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> persistedState(key: String, defaultValue: T, saver: (T) -> Unit): androidx.compose.runtime.MutableState<T> {
        val prefs = getApplication<Application>().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val initialValue = when (defaultValue) {
            is Boolean -> prefs.getBoolean(key, defaultValue) as T
            is String -> prefs.getString(key, defaultValue) as T
            is Int -> prefs.getInt(key, defaultValue) as T
            else -> defaultValue
        }
        val state = mutableStateOf(initialValue)
        return object : androidx.compose.runtime.MutableState<T> by state {
            override var value: T
                get() = state.value
                set(value) {
                    state.value = value
                    saver(value)
                }
        }
    }

    private fun persistedThemeState(key: String, defaultValue: ThemeStyle, saver: (ThemeStyle) -> Unit): androidx.compose.runtime.MutableState<ThemeStyle> {
        val prefs = getApplication<Application>().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val initialStr = prefs.getString(key, defaultValue.name) ?: defaultValue.name
        val initialValue = try { ThemeStyle.valueOf(initialStr) } catch(e: Exception) { defaultValue }
        val state = mutableStateOf(initialValue)
        return object : androidx.compose.runtime.MutableState<ThemeStyle> by state {
            override var value: ThemeStyle
                get() = state.value
                set(value) {
                    state.value = value
                    saver(value)
                }
        }
    }
}
