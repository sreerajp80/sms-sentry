package `in`.sreerajp.sms_sentry.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
import `in`.sreerajp.sms_sentry.data.FinanceTx
import `in`.sreerajp.sms_sentry.data.ReminderSms
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.engine.P2PSyncEngine
import `in`.sreerajp.sms_sentry.engine.SyncState
import `in`.sreerajp.sms_sentry.ui.theme.ThemeStyle
import `in`.sreerajp.sms_sentry.ui.theme.HighDensityBackgroundLight
import `in`.sreerajp.sms_sentry.ui.theme.HighDensityBackgroundDark
import `in`.sreerajp.sms_sentry.ui.theme.categoryColor
import `in`.sreerajp.sms_sentry.ui.theme.categoryColors
import `in`.sreerajp.sms_sentry.ui.theme.goodColor
import `in`.sreerajp.sms_sentry.ui.theme.goodSoftColor
import `in`.sreerajp.sms_sentry.ui.theme.spamColor
import `in`.sreerajp.sms_sentry.ui.theme.spamSoftColor
import `in`.sreerajp.sms_sentry.util.AboutInfo
import `in`.sreerajp.sms_sentry.util.RecurrenceUtil
import `in`.sreerajp.sms_sentry.util.SimManager
import `in`.sreerajp.sms_sentry.util.SmsSegment
import `in`.sreerajp.sms_sentry.util.loadAboutConfig
import `in`.sreerajp.sms_sentry.BuildConfig
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Sender (phone number) -> resolved address-book name, provided once at the app root so every
 * display point can show the saved contact name in place of the raw number. Empty by default
 * (no permission / no matches), in which case callers fall back to the raw sender.
 */
val LocalContactNames = compositionLocalOf { emptyMap<String, String>() }

/**
 * Sender (phone number) -> saved contact photo Uri, provided once at the app root alongside
 * [LocalContactNames]. Only senders with a saved photo appear; callers fall back to a monogram
 * [AvatarTile] for everything else.
 */
val LocalContactPhotos = compositionLocalOf { emptyMap<String, android.net.Uri>() }

/** Saved contact photo for [sender], or null when there's no match / no photo. */
@Composable
private fun photoUriFor(sender: String): android.net.Uri? = LocalContactPhotos.current[sender]

/** Display name for [sender]: the saved contact name when known, else the raw sender. */
@Composable
private fun displayNameFor(sender: String): String =
    LocalContactNames.current[sender] ?: sender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsOrganizerApp(viewModel: SmsOrganizerViewModel) {
    val context = LocalContext.current
    val currentTab by remember { viewModel.activeTab }
    val navReminders by viewModel.reminders.collectAsState()
    val contactNames by viewModel.contactNames.collectAsState()
    val contactPhotos by viewModel.contactPhotos.collectAsState()

    // Request vital SMS receiving/reading/sending and notification permissions at startup
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // If we just gained READ_SMS, import the phone's existing messages.
        if (grants[android.Manifest.permission.READ_SMS] == true) {
            viewModel.importSystemSms()
        }
        // If we just gained READ_CONTACTS, resolve sender numbers to saved names.
        if (grants[android.Manifest.permission.READ_CONTACTS] == true) {
            viewModel.refreshContactNames()
        }
        // If we just gained READ_PHONE_STATE, enumerate active SIMs for dual-SIM routing.
        if (grants[android.Manifest.permission.READ_PHONE_STATE] == true) {
            viewModel.refreshActiveSims()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            // MMS-group dangerous permissions requested explicitly (not relying on SMS-group auto-grant).
            android.Manifest.permission.RECEIVE_MMS,
            android.Manifest.permission.RECEIVE_WAP_PUSH,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }
    
    // Dialog visibility states
    var showComposeDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    CompositionLocalProvider(
        LocalContactNames provides contactNames,
        LocalContactPhotos provides contactPhotos
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "SMS Sentry",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SMS Sentry",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Offline · Encrypted",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showComposeDialog = true },
                            modifier = Modifier.testTag("open_composer_bar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Compose New Message",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    val menuItems = listOf(
                        Triple("Dashboard", Icons.Default.GridView, "dashboard_tab"),
                        Triple("Inbox", Icons.Default.MailOutline, "inbox_tab"),
                        Triple("Accounts", Icons.Default.MonetizationOn, "accounts_tab"),
                        Triple("Reminders", Icons.Default.Schedule, "reminders_tab"),
                        Triple("Sync", Icons.Default.Sync, "sync_tab"),
                        Triple("Settings", Icons.Default.Settings, "settings_tab")
                    )

                    menuItems.forEach { (title, icon, tag) ->
                        NavigationBarItem(
                            selected = currentTab == title,
                            onClick = { viewModel.activeTab.value = title },
                            label = {
                                Text(
                                    text = title,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible
                                )
                            },
                            icon = {
                                if (title == "Reminders" && navReminders.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ) {
                                                Text(navReminders.size.toString())
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = icon, contentDescription = title)
                                    }
                                } else {
                                    Icon(imageVector = icon, contentDescription = title)
                                }
                            },
                            modifier = Modifier.testTag(tag),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "TabContent"
                ) { targetTab ->
                    when (targetTab) {
                        "Dashboard" -> DashboardScreen(viewModel, onNavigate = { viewModel.activeTab.value = it })
                        "Inbox" -> InboxScreen(viewModel)
                        "Accounts" -> FinanceScreen(viewModel)
                        "Reminders" -> RemindersScreen(viewModel)
                        "Sync" -> SyncScreen(viewModel)
                        "Settings" -> SettingsScreen(viewModel, onOpenImport = { showImportDialog = true })
                    }
                }
            }
        }

        // --- Composers and Trigger Dialogs ---

        // Open the composer pre-filled when launched from an sms:/smsto: intent.
        val composePrefill = viewModel.composePrefill.value
        androidx.compose.runtime.LaunchedEffect(composePrefill) {
            if (composePrefill != null) showComposeDialog = true
        }

        if (showComposeDialog) {
            ComposeSmsDialog(
                viewModel = viewModel,
                initialRecipient = composePrefill?.recipient ?: "",
                initialBody = composePrefill?.body ?: "",
                onDismiss = {
                    showComposeDialog = false
                    viewModel.clearComposePrefill()
                }
            )
        }

        if (showImportDialog) {
            ImportBackupDialog(
                viewModel = viewModel,
                onDismiss = { showImportDialog = false }
            )
        }

        // --- Conversation thread full-screen overlay ---
        val openedThread = viewModel.openedThread.value
        if (openedThread != null) {
            BackHandler { viewModel.closeThread() }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ThreadScreen(viewModel = viewModel, sender = openedThread)
            }
        }

        // --- Contribution breakdown overlay (tap income/expense on the finance card). Rendered
        // before the message-detail block so SMS detail stacks above it and Back closes
        // detail -> breakdown -> finance in order. ---
        val openedContribution = viewModel.openedContribution.value
        if (openedContribution != null) {
            BackHandler { viewModel.closeContribution() }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ContributionBreakdownScreen(viewModel = viewModel, kind = openedContribution)
            }
        }

        // --- Message detail full-screen overlay (stacks above the thread) ---
        val openedMessage = viewModel.openedMessage.value
        if (openedMessage != null) {
            BackHandler { viewModel.closeMessage() }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MessageDetailScreen(viewModel = viewModel, msg = openedMessage)
            }
        }

        // --- Sender info full-screen overlay (opened from the thread overflow menu) ---
        val openedSenderInfo = viewModel.openedSenderInfo.value
        if (openedSenderInfo != null) {
            BackHandler { viewModel.closeSenderInfo() }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                SenderInfoScreen(viewModel = viewModel, sender = openedSenderInfo)
            }
        }
    }
    }
}

// ==========================================
// SCREEN: DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(viewModel: SmsOrganizerViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val isSecured = viewModel.isFinanceLocked.value
    val isAuthenticated = viewModel.isFinanceAuthenticated.value
    val isFinanceHidden = isSecured && !isAuthenticated

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.isFinanceAuthenticated.value = true
            Toast.makeText(context, "Access Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Verification failed.", Toast.LENGTH_SHORT).show()
        }
    }

    val inboxMessages by viewModel.inboxMessages.collectAsState()
    val spamMessages by viewModel.spamMessages.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    val spamCount = spamMessages.size
    val personalCount = inboxMessages.count { m -> m.category == "Personal" }
    val promotionsCount = inboxMessages.count { m -> m.category == "Promotions" }
    val othersCount = inboxMessages.count { m -> m.category == "Others" }
    val reminderCount = reminders.size
    val totalMessagesCount = inboxMessages.size + spamMessages.size
    val breakdownTotal = personalCount + promotionsCount + othersCount + spamCount

    val activeBalance = latestParsedBalance(transactions)
    val catColors = categoryColors()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HERO
        item {
            Column {
                Text(
                    text = "WELCOME BACK",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Your inbox is ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append("secure.")
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 34.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Threads are evaluated locally by offline security engines. Nothing leaves your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // 2. STATUS PILLS
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = "Engine active",
                    container = goodSoftColor(),
                    contentColor = goodColor()
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(goodColor())
                    )
                }
                StatusPill(
                    text = "Scanned 2m ago · 0 threats",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. AVAILABLE BALANCE
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isFinanceHidden) {
                            triggerDeviceAuthentication(context, authLauncher, onImmediateBypass = {
                                viewModel.isFinanceAuthenticated.value = true
                                onNavigate("Accounts")
                            })
                        } else {
                            onNavigate("Accounts")
                        }
                    }
                    .testTag("dashboard_balance_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVAILABLE BALANCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        // When unlocked, the pill doubles as a re-lock control so the
                        // balance can be hidden again without leaving the Dashboard.
                        val canRelock = isSecured && !isFinanceHidden
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = if (canRelock) {
                                Modifier
                                    .clickable {
                                        viewModel.isFinanceAuthenticated.value = false
                                        Toast.makeText(context, "Balance locked", Toast.LENGTH_SHORT).show()
                                    }
                                    .testTag("dashboard_lock_pill")
                            } else {
                                Modifier
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFinanceHidden) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isFinanceHidden) "Locked" else "Visible",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "₹",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isFinanceHidden) "••,•••.••" else String.format("%,.2f", activeBalance),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (isFinanceHidden) "Parsed from bank SMS · stays on device" else "Latest parsed balance · stays on device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    if (isFinanceHidden) {
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    "Tap to decrypt",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. STAT ROW
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                DashboardStatCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("Inbox") },
                    icon = Icons.Default.Shield,
                    iconTint = spamColor(),
                    iconBg = spamSoftColor(),
                    badgeText = "Safe",
                    badgeColor = goodColor(),
                    badgeBg = goodSoftColor(),
                    value = "$spamCount",
                    caption = "Spam blocked · 7d"
                )
                DashboardStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconBg = MaterialTheme.colorScheme.primaryContainer,
                    badgeText = "AES-256",
                    badgeColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    badgeBg = Color.Transparent,
                    value = "$totalMessagesCount",
                    caption = "Threads encrypted"
                )
            }
        }

        // 5. MESSAGE BREAKDOWN
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(17.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Message breakdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Auto-sorted · this week",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "$breakdownTotal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val safeTotal = if (breakdownTotal == 0) 1 else breakdownTotal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val segs = listOf(personalCount, promotionsCount, othersCount, spamCount)
                        if (breakdownTotal == 0) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        } else {
                            segs.forEachIndexed { i, c ->
                                if (c > 0) {
                                    Box(
                                        Modifier
                                            .weight(c.toFloat() / safeTotal)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(catColors[i])
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val maxCount = maxOf(personalCount, promotionsCount, othersCount, spamCount, 1)
                    BreakdownRow("Personal", personalCount, maxCount, catColors[0])
                    Spacer(Modifier.height(13.dp))
                    BreakdownRow("Promotions", promotionsCount, maxCount, catColors[1])
                    Spacer(Modifier.height(13.dp))
                    BreakdownRow("Others", othersCount, maxCount, catColors[2])
                    Spacer(Modifier.height(13.dp))
                    BreakdownRow("Spam blocked", spamCount, maxCount, catColors[3])
                }
            }
        }

        // 6. OPERATIONAL CHANNELS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Operational channels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "All",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                OperationalChannelCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Tune,
                    title = "Rules",
                    subtitle = "7 active",
                    onClick = { onNavigate("Settings") }
                )
                OperationalChannelCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CalendarMonth,
                    title = "Calendar",
                    subtitle = "$reminderCount events",
                    onClick = { onNavigate("Reminders") }
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                OperationalChannelCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WifiTethering,
                    title = "P2P Backup",
                    subtitle = "Synced",
                    onClick = { onNavigate("Sync") }
                )
                OperationalChannelCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock,
                    title = "Vault",
                    subtitle = if (isFinanceHidden) "Locked" else "Open",
                    onClick = { onNavigate("Accounts") }
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    container: Color,
    contentColor: Color,
    leading: @Composable () -> Unit
) {
    Surface(color = container, shape = RoundedCornerShape(999.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            leading()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun DashboardStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    badgeText: String,
    badgeColor: Color,
    badgeBg: Color,
    value: String,
    caption: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = iconTint)
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = value,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun BreakdownRow(label: String, count: Int, maxCount: Int, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(count.toFloat() / maxCount)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun OperationalChannelCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ==========================================
// SCREEN: INBOX / CONVERSATIONS SCREEN
// ==========================================
@Composable
fun InboxScreen(viewModel: SmsOrganizerViewModel) {
    val inboxMessages by viewModel.inboxMessages.collectAsState()
    val spamMessages by viewModel.spamMessages.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val drafts by viewModel.drafts.collectAsState()

    // Parsed entity (amount / due date) per message, keyed by message id.
    val txByMsg = remember(transactions) { transactions.associateBy { it.messageId } }
    val reminderByMsg = remember(reminders) { reminders.associateBy { it.messageId } }

    var activeInboxFolder by remember { mutableStateOf("All") } // All, Personal, Promotions, Others, Spam

    val filteredMessages = when (activeInboxFolder) {
        "Personal" -> inboxMessages.filter { it.category == "Personal" }
        "Promotions" -> inboxMessages.filter { it.category == "Promotions" }
        "Others" -> inboxMessages.filter { it.category == "Others" }
        "Spam" -> spamMessages
        else -> inboxMessages
    }

    // --- Search (scope-aware, client-side over the in-memory flows) ---
    // "All" searches every message including Spam; a specific folder searches only within it.
    var searchQuery by remember { mutableStateOf("") }
    val trimmedQuery = searchQuery.trim()
    val searchActive = trimmedQuery.isNotEmpty()
    val searchSource =
        if (activeInboxFolder == "All") inboxMessages + spamMessages
        else filteredMessages
    val searchResults = remember(searchSource, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else {
            val q = trimmedQuery.lowercase(Locale.getDefault())
            searchSource
                .filter {
                    it.sender.lowercase(Locale.getDefault()).contains(q) ||
                    it.body.lowercase(Locale.getDefault()).contains(q)
                }
                .sortedByDescending { it.timestamp }
        }
    }

    val context = LocalContext.current

    // --- Multi-select state (long-press a card to select; act on one or more senders) ---
    var selectionMode by remember { mutableStateOf(false) }
    val selectedSenders = remember { mutableStateListOf<String>() }
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showMarkReadDialog by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }

    fun clearSelection() {
        selectedSenders.clear()
        selectionMode = false
    }
    fun toggle(sender: String) {
        if (!selectedSenders.remove(sender)) selectedSenders.add(sender)
        if (selectedSenders.isEmpty()) selectionMode = false
    }
    // Drop selections that no longer exist (deleted/blocked, or filtered out by a folder switch).
    // Validate against searchSource (a superset of filteredMessages in every folder) so that a
    // sender selected from a search — e.g. a Spam sender in an "All" search — isn't pruned.
    LaunchedEffect(searchSource) {
        val validSenders = searchSource.mapTo(HashSet()) { it.sender }
        if (selectedSenders.retainAll(validSenders) && selectedSenders.isEmpty()) {
            selectionMode = false
        }
    }

    // While selecting, the system back button exits selection rather than leaving the screen.
    BackHandler(enabled = selectionMode) { clearSelection() }

    Column(modifier = Modifier.fillMaxSize()) {
      if (selectionMode) {
        // Contextual action bar (mirrors ThreadScreen's selection header).
        val allMuted = selectedSenders.isNotEmpty() && selectedSenders.all { viewModel.isMuted(it) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { clearSelection() },
                modifier = Modifier.testTag("inbox_selection_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel selection",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "${selectedSenders.size} selected",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.5.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showMarkReadDialog = true },
                modifier = Modifier.testTag("inbox_mark_read_selected")
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Mark as read",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = { showMoveSheet = true },
                modifier = Modifier.testTag("inbox_move_selected")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Move to folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = { showDeleteSelectedDialog = true },
                modifier = Modifier.testTag("inbox_delete_selected")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = spamColor(),
                    modifier = Modifier.size(22.dp)
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("inbox_selection_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (allMuted) "Unmute notifications" else "Mute notifications") },
                        leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            val shouldMute = !allMuted
                            selectedSenders.forEach { s ->
                                if (viewModel.isMuted(s) != shouldMute) viewModel.toggleMute(s)
                            }
                            Toast.makeText(
                                context,
                                if (shouldMute) "Notifications muted" else "Notifications unmuted",
                                Toast.LENGTH_SHORT
                            ).show()
                            clearSelection()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark as unread") },
                        leadingIcon = { Icon(Icons.Default.MarkChatUnread, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            selectedSenders.forEach { viewModel.markConversationUnread(it) }
                            clearSelection()
                        }
                    )
                    if (activeInboxFolder == "Spam") {
                        DropdownMenuItem(
                            text = { Text("Not spam") },
                            leadingIcon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                selectedSenders.toList().forEach { viewModel.markNotSpamSender(it) }
                                Toast.makeText(context, "Moved out of spam", Toast.LENGTH_SHORT).show()
                                clearSelection()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Block sender") },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            selectedSenders.toList().forEach { viewModel.blockConversation(it) }
                            clearSelection()
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      } else {
        // Search bar — scoped to the active folder (see searchSource above).
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .testTag("inbox_search_field"),
            singleLine = true,
            placeholder = { Text("Search messages") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.testTag("inbox_search_clear")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // Pill filter tabs with count badges (Direction A)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val folders = listOf("All", "Personal", "Promotions", "Others", "Spam")
            items(folders) { folderName ->
                val isSelected = activeInboxFolder == folderName
                val count = when (folderName) {
                    "Spam" -> spamMessages.size
                    "Personal" -> inboxMessages.count { it.category == "Personal" }
                    "Promotions" -> inboxMessages.count { it.category == "Promotions" }
                    "Others" -> inboxMessages.count { it.category == "Others" }
                    else -> inboxMessages.size
                }
                val pillColor = when (folderName) {
                    "Personal" -> categoryColor("Personal")
                    "Promotions" -> categoryColor("Promotions")
                    "Others" -> categoryColor("Others")
                    "Spam" -> categoryColor("Spam")
                    else -> MaterialTheme.colorScheme.onSurfaceVariant // "All" — neutral, distinct from categories
                }
                InboxFilterPill(
                    label = folderName,
                    count = count,
                    selected = isSelected,
                    color = pillColor,
                    onClick = { activeInboxFolder = folderName }
                )
            }
        }

        // Swipe hint row (matches the design's "Tap a card to reveal Block & Delete")
        if (!searchActive && filteredMessages.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwipeLeft,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
                Text(
                    text = "Swipe a card left for Block & Delete · tap to open · long-press to select",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            }
        }
      }

        // Confirm dialog for marking selected conversations as read.
        if (showMarkReadDialog) {
            val n = selectedSenders.size
            AlertDialog(
                onDismissRequest = { showMarkReadDialog = false },
                title = { Text("Mark $n conversation${if (n == 1) "" else "s"} as read?") },
                text = {
                    Text("Every message from the selected " +
                         "sender${if (n == 1) "" else "s"} will be marked read.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedSenders.toList().forEach { viewModel.markConversationRead(it) }
                        showMarkReadDialog = false
                        clearSelection()
                    }) { Text("Mark read") }
                },
                dismissButton = {
                    TextButton(onClick = { showMarkReadDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Confirm dialog for multi-delete of selected conversations.
        if (showDeleteSelectedDialog) {
            val n = selectedSenders.size
            AlertDialog(
                onDismissRequest = { showDeleteSelectedDialog = false },
                title = { Text("Delete $n conversation${if (n == 1) "" else "s"}?") },
                text = {
                    Text("This permanently deletes every message from the selected " +
                         "sender${if (n == 1) "" else "s"}.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedSenders.toList().forEach { viewModel.deleteConversation(it) }
                        showDeleteSelectedDialog = false
                        clearSelection()
                    }) { Text("Delete", color = spamColor()) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Chooser for moving the selected conversation(s) into a category/folder.
        if (showMoveSheet) {
            val n = selectedSenders.size
            // category label -> stored category string
            val targets = listOf(
                "Personal" to "Personal",
                "Promotions" to "Promotions",
                "Others" to "Others",
                "Spam" to "Spam"
            )
            AlertDialog(
                onDismissRequest = { showMoveSheet = false },
                title = { Text("Move $n conversation${if (n == 1) "" else "s"} to") },
                text = {
                    Column {
                        targets.forEach { (label, category) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val senders = selectedSenders.toList()
                                        senders.forEach { viewModel.moveConversationToCategory(it, category) }
                                        showMoveSheet = false
                                        clearSelection()
                                        Toast.makeText(
                                            context, "Moved to $label", Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .testTag("inbox_move_to_$category"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor(category).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(categoryColor(category))
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMoveSheet = false }) { Text("Cancel") }
                }
            )
        }

        // Content Index
        if (searchActive) {
            // --- Search results (message-level, scoped to the active folder) ---
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "No results",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No messages match",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Nothing in ${if (activeInboxFolder == "All") "your inbox" else activeInboxFolder} " +
                                   "matches \"$trimmedQuery\".",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    items(searchResults, key = { it.id }) { msg ->
                        SearchResultCard(
                            msg = msg,
                            selected = msg.sender in selectedSenders,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) toggle(msg.sender)
                                else viewModel.openThread(msg.sender)
                            },
                            onLongClick = {
                                if (!selectionMode) selectionMode = true
                                toggle(msg.sender)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(30.dp)) }
                }
            }
        } else if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Empty Box",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Inbox is Calm",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Nice job! Any incoming texts matching rules are sorted instantly.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            // Group the filtered messages into per-sender conversations (one card per sender).
            val conversations = remember(filteredMessages) {
                filteredMessages
                    .groupBy { it.sender }
                    .map { (sender, msgs) ->
                        val latest = msgs.maxByOrNull { it.timestamp }!!
                        Conversation(
                            sender = sender,
                            latest = latest,
                            unreadCount = msgs.count { !it.isRead && it.type == SMSMessage.TYPE_INBOX },
                            total = msgs.size
                        )
                    }
                    .sortedByDescending { it.latest.timestamp }
            }
            // Group the (already newest-first) conversations into day buckets, keyed by the
            // last message's timestamp. groupBy preserves first-encounter order, so the
            // descending sort above carries through to both bucket order and card order.
            val grouped = remember(conversations) {
                conversations.groupBy { conversationDateBucket(it.latest.timestamp) }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                grouped.forEach { (bucket, convs) ->
                    item(key = "header_$bucket") { DateSectionHeader(bucket) }
                    items(convs, key = { it.sender }) { conv ->
                        val entityText = remember(conv.latest.id, txByMsg, reminderByMsg) {
                            messageEntityText(conv.latest, txByMsg[conv.latest.id], reminderByMsg[conv.latest.id])
                        }
                        ConversationCard(
                            conversation = conv,
                            entityText = entityText,
                            draft = drafts[conv.sender]?.takeIf { it.isNotBlank() },
                            selected = conv.sender in selectedSenders,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) toggle(conv.sender)
                                else viewModel.openThread(conv.sender)
                            },
                            onLongClick = {
                                if (!selectionMode) selectionMode = true
                                toggle(conv.sender)
                            },
                            onDelete = { viewModel.deleteConversation(conv.sender) },
                            onBlock = { viewModel.blockConversation(conv.sender) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }
}

// ==========================================
// CONVERSATIONS (grouped per-sender inbox + thread view)
// ==========================================

/** A per-sender conversation summary for the grouped inbox list. */
data class Conversation(
    val sender: String,
    val latest: SMSMessage,
    val unreadCount: Int,
    val total: Int
)

/** Day-granularity section label for the conversation list, from a message timestamp. */
fun conversationDateBucket(timestamp: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(msgCal, today)     -> "Today"
        sameDay(msgCal, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Left-aligned muted date subheader separating conversation day buckets. */
@Composable
private fun DateSectionHeader(label: String) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

/** Short label for a sent message's delivery state, or null for received messages. */
fun deliveryStatusLabel(msg: SMSMessage): String? {
    if (msg.type != SMSMessage.TYPE_SENT) return null
    return when (msg.status) {
        SMSMessage.STATUS_SENDING -> "Sending…"
        SMSMessage.STATUS_SENT -> "Sent"
        SMSMessage.STATUS_DELIVERED -> "Delivered"
        SMSMessage.STATUS_FAILED -> "Failed"
        else -> null
    }
}

/**
 * A single message result row for inbox search. Tapping opens the sender's conversation
 * thread; long-pressing enters the inbox's (sender-based) multi-select mode. Because results
 * are message-level but selection is sender-level, selecting one result marks every result
 * row from that sender as selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultCard(
    msg: SMSMessage,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val categoryColor = categoryColor(msg.category)
    val timeText = remember(msg.timestamp) {
        SimpleDateFormat("dd MMM · hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("search_result_${msg.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                                 MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                     .compositeOver(MaterialTheme.colorScheme.surface)
                             else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, categoryColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) Icons.Default.CheckCircle
                                      else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(22.dp)
                    )
                }
                val senderName = displayNameFor(msg.sender)
                AvatarTile(
                    label = senderName.take(1).uppercase(Locale.getDefault()),
                    color = categoryColor,
                    photoUri = photoUriFor(msg.sender)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = senderName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg.body,
                fontSize = 13.sp,
                lineHeight = 19.5.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationCard(
    conversation: Conversation,
    entityText: String?,
    draft: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit
) {
    val msg = conversation.latest
    val categoryColor = categoryColor(msg.category)
    val hasUnread = conversation.unreadCount > 0
    val otp = remember(msg.body) { detectOtp(msg.body) }
    val timeText = remember(msg.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    }
    val statusLabel = deliveryStatusLabel(msg)

    val density = LocalDensity.current
    val revealPx = with(density) { 132.dp.toPx() }
    val offsetX = remember(conversation.sender) { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeAction(
                icon = Icons.Default.Block,
                label = "Block",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onBlock
            )
            SwipeAction(
                icon = Icons.Default.Delete,
                label = "Delete",
                container = spamSoftColor(),
                content = spamColor(),
                onClick = onDelete
            )
        }

        val baseContainer = if (msg.isBlocked)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    .compositeOver(MaterialTheme.colorScheme.surface)
                            else MaterialTheme.colorScheme.surface
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(conversation.sender) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                            scope.launch { offsetX.snapTo(next) }
                        },
                        onDragEnd = {
                            val target = if (offsetX.value < -revealPx / 2f) -revealPx else 0f
                            scope.launch { offsetX.animateTo(target) }
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (offsetX.value != 0f) scope.launch { offsetX.animateTo(0f) } else onClick()
                    },
                    onLongClick = onLongClick
                )
                .testTag("conversation_card_${conversation.sender}"),
            colors = CardDefaults.cardColors(
                containerColor = if (selected)
                                     MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                         .compositeOver(baseContainer)
                                 else baseContainer
            ),
            shape = RoundedCornerShape(20.dp),
            border = when {
                selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                hasUnread -> BorderStroke(2.dp, categoryColor)
                else -> BorderStroke(1.dp, categoryColor.copy(alpha = 0.45f))
            }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Icon(
                            imageVector = if (selected) Icons.Default.CheckCircle
                                          else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (selected) "Selected" else "Not selected",
                            tint = if (selected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(22.dp)
                        )
                    }
                    val senderName = displayNameFor(conversation.sender)
                    AvatarTile(
                        label = senderName.take(1).uppercase(Locale.getDefault()),
                        color = categoryColor,
                        photoUri = photoUriFor(conversation.sender)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(categoryColor)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                    }
                    Text(
                        text = senderName,
                        fontWeight = if (hasUnread) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (hasUnread) 1f else 0.65f
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (conversation.total > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(categoryColor.copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = conversation.total.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (draft != null) {
                        // An unsent draft takes over the preview, like a stock SMS app.
                        Text(
                            text = "Draft: ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = spamColor()
                        )
                        Text(
                            text = draft,
                            fontSize = 13.sp,
                            lineHeight = 19.5.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        if (msg.isMms) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "MMS",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        if (statusLabel != null) {
                            Text(
                                text = "$statusLabel · ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (msg.status == SMSMessage.STATUS_FAILED) spamColor() else categoryColor
                            )
                        }
                        Text(
                            text = msg.body,
                            fontSize = 13.sp,
                            lineHeight = 19.5.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (entityText != null || otp != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (entityText != null) EntityChip(entityText)
                        if (otp != null) CopyOtpChip(otp, msg.id)
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN: CONVERSATION THREAD (chat-style per-sender history)
// ==========================================
@Composable
fun ThreadScreen(viewModel: SmsOrganizerViewModel, sender: String) {
    val allMessages by viewModel.allMessages.collectAsState()
    val threadMessages = remember(allMessages, sender) {
        allMessages.filter { it.sender == sender }.sortedBy { it.timestamp }
    }
    val categoryColor = categoryColor(threadMessages.lastOrNull()?.category ?: "Personal")
    // Restore any saved draft for this thread; persist it again when leaving (see DisposableEffect).
    var replyText by remember(sender) { mutableStateOf(viewModel.draftFor(sender)) }
    val context = LocalContext.current

    DisposableEffect(sender) {
        onDispose { viewModel.saveDraft(sender, replyText) }
    }

    // Open the thread positioned on the most recent message; also re-pin to the latest
    // after a sent reply or freshly received message.
    val listState = rememberLazyListState()
    LaunchedEffect(sender, threadMessages.size) {
        if (threadMessages.isNotEmpty()) listState.scrollToItem(threadMessages.lastIndex)
    }

    // Auto-mark the conversation read after a configurable delay on open (0 = Off). Keyed on the
    // unread count so it re-arms when new unread messages arrive; leaving the thread cancels the
    // pending mark, so a quick preview doesn't mark anything read.
    val autoReadDelay by viewModel.autoMarkReadDelaySeconds
    val unreadInboxCount = threadMessages.count { !it.isRead && it.type == SMSMessage.TYPE_INBOX }
    LaunchedEffect(sender, autoReadDelay, unreadInboxCount) {
        if (autoReadDelay > 0 && unreadInboxCount > 0) {
            kotlinx.coroutines.delay(autoReadDelay * 1000L)
            viewModel.markConversationRead(sender)
        }
    }

    // --- Multi-select state ---
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }

    fun clearSelection() {
        selectedIds.clear()
        selectionMode = false
    }
    fun toggle(id: Long) {
        if (!selectedIds.remove(id)) selectedIds.add(id)
        if (selectedIds.isEmpty()) selectionMode = false
    }
    // Drop selections that no longer exist (e.g. after a delete) — done as a side effect so we
    // never write snapshot state during composition.
    LaunchedEffect(threadMessages) {
        val validIds = threadMessages.mapTo(HashSet()) { it.id }
        if (selectedIds.retainAll(validIds) && selectedIds.isEmpty()) {
            selectionMode = false
        }
    }

    // While selecting, the system back button exits selection rather than closing the thread.
    BackHandler(enabled = selectionMode) { clearSelection() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("thread_${sender}")
    ) {
        // Header — normal vs. selection (contextual action) mode.
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { clearSelection() },
                    modifier = Modifier.testTag("thread_selection_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "${selectedIds.size} selected",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.5.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            viewModel.markMessagesRead(selectedIds.toList())
                            clearSelection()
                        }
                    },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.testTag("thread_mark_read_selected")
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Mark as read",
                        tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            viewModel.markMessagesUnread(selectedIds.toList())
                            clearSelection()
                        }
                    },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.testTag("thread_mark_unread_selected")
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkChatUnread,
                        contentDescription = "Mark as unread",
                        tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = { if (selectedIds.isNotEmpty()) showDeleteSelectedDialog = true },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.testTag("thread_delete_selected")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected",
                        tint = if (selectedIds.isNotEmpty()) spamColor()
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.closeThread() },
                    modifier = Modifier.testTag("thread_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                val senderName = displayNameFor(sender)
                AvatarTile(
                    label = senderName.take(1).uppercase(Locale.getDefault()),
                    color = categoryColor,
                    size = 38.dp,
                    corner = 12.dp,
                    fontSize = 16.sp,
                    photoUri = photoUriFor(sender)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = senderName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${threadMessages.size} message${if (threadMessages.size == 1) "" else "s"}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag("thread_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mute notifications") },
                            leadingIcon = {
                                Icon(Icons.Default.NotificationsOff, contentDescription = null)
                            },
                            trailingIcon = {
                                Text(
                                    text = if (viewModel.isMuted(sender)) "On" else "Off",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewModel.isMuted(sender)) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                )
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.toggleMute(sender)
                                Toast.makeText(
                                    context,
                                    if (viewModel.isMuted(sender)) "Notifications muted for $sender"
                                    else "Notifications unmuted for $sender",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select messages") },
                            leadingIcon = {
                                Icon(Icons.Default.Checklist, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                selectionMode = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sender info") },
                            leadingIcon = {
                                Icon(Icons.Default.Info, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.openSenderInfo(sender)
                            }
                        )
                        if (viewModel.isUnknownContact(sender)) {
                            DropdownMenuItem(
                                text = { Text("Add to contacts") },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.addToContacts(sender)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Block sender") },
                            leadingIcon = {
                                Icon(Icons.Default.Block, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.blockConversation(sender)
                                viewModel.closeThread()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Report as spam") },
                            leadingIcon = {
                                Icon(Icons.Default.Report, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.reportSpamSender(sender)
                                Toast.makeText(context, "Reported as spam", Toast.LENGTH_SHORT).show()
                                viewModel.closeThread()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete entire chat", color = spamColor()) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = spamColor())
                            },
                            onClick = {
                                menuOpen = false
                                showDeleteChatDialog = true
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // --- Confirmation dialogs (destructive: also remove from the system provider when default) ---
        if (showDeleteSelectedDialog) {
            val n = selectedIds.size
            AlertDialog(
                onDismissRequest = { showDeleteSelectedDialog = false },
                title = { Text("Delete $n message${if (n == 1) "" else "s"}?") },
                text = { Text("This permanently deletes the selected message${if (n == 1) "" else "s"}.") },
                confirmButton = {
                    TextButton(onClick = {
                        val toDelete = threadMessages.filter { it.id in selectedIds }
                        viewModel.deleteMessages(toDelete)
                        showDeleteSelectedDialog = false
                        clearSelection()
                    }) { Text("Delete", color = spamColor()) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Cancel") }
                }
            )
        }
        if (showDeleteChatDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteChatDialog = false },
                title = { Text("Delete entire chat?") },
                text = { Text("This permanently deletes the whole conversation with $sender.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteChatDialog = false
                        viewModel.deleteConversation(sender)
                        viewModel.closeThread()
                    }) { Text("Delete", color = spamColor()) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteChatDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Chat bubbles
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(threadMessages, key = { it.id }) { msg ->
                ThreadBubble(
                    msg = msg,
                    selected = msg.id in selectedIds,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) toggle(msg.id)
                        else viewModel.openMessage(msg)
                    },
                    onLongClick = {
                        if (!selectionMode) selectionMode = true
                        toggle(msg.id)
                    },
                    onRetry = { viewModel.resendMessage(msg) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Reply composer — only when this app is the default SMS app.
        if (viewModel.canSendSms) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Segment counter — only once the reply spills into multiple SMS or nears a part limit.
            val seg = SmsSegment.info(replyText)
            if (SmsSegment.shouldShow(seg)) {
                Text(
                    text = SmsSegment.label(seg),
                    fontSize = 10.sp,
                    color = if (seg.isUnicode) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (replyText.isEmpty()) {
                                Text(
                                    "Type a reply…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                val canSend = replyText.trim().isNotEmpty()
                IconButton(
                    onClick = {
                        if (canSend) {
                            viewModel.sendSms(sender, replyText.trim(), viewModel.defaultOutgoingSlot())
                            replyText = ""
                            viewModel.clearDraft(sender)
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = if (canSend) categoryColor else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                        .testTag("thread_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Set SMS Sentry as your default SMS app to reply.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadBubble(
    msg: SMSMessage,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit
) {
    val isSent = msg.type == SMSMessage.TYPE_SENT
    val isUnreadIncoming = !msg.isRead && msg.type == SMSMessage.TYPE_INBOX
    val accentColor = categoryColor(msg.category)
    val bubbleColor = if (isSent) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSent) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
    val timeText = remember(msg.timestamp) {
        SimpleDateFormat("dd MMM · hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    }
    val statusLabel = deliveryStatusLabel(msg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                else Modifier
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp)
            )
        }
        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading accent bar marks an unread incoming message (clears on read).
                if (isUnreadIncoming) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomEnd = if (isSent) 4.dp else 16.dp,
                                bottomStart = if (isSent) 16.dp else 4.dp
                            )
                        )
                        .background(bubbleColor)
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Column {
                        if (msg.isMms && !msg.attachmentUri.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Attachment",
                                    tint = textColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "Attachment",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = msg.body,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = textColor
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            ) {
                if (isUnreadIncoming) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = timeText,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
                if (statusLabel != null) {
                    Text(
                        text = " · $statusLabel",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (msg.status == SMSMessage.STATUS_FAILED) spamColor()
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
                // A failed send can be retried in place.
                if (msg.status == SMSMessage.STATUS_FAILED) {
                    Text(
                        text = " · Tap to retry",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.clickable(enabled = !selectionMode) { onRetry() }
                    )
                }
            }
        }
    }
}

// ==========================================
// INBOX HELPERS (Direction A "Secure Cards")
// ==========================================

private val OTP_KEYWORDS = listOf(
    "otp", "verification", "code", "passcode", "pin", "password", "security", "one-time", "secret"
)

/** Returns the OTP code in a message body if it looks like a verification message, else null. */
fun detectOtp(body: String): String? {
    val lower = body.lowercase(Locale.getDefault())
    if (OTP_KEYWORDS.none { lower.contains(it) }) return null
    return Regex("\\b\\d{4,8}\\b").find(body)?.value
}

/** Indian-grouped rupee formatter; drops the decimals when the amount is a whole number. */
fun formatRupees(amount: Double): String {
    val nf = java.text.NumberFormat.getNumberInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
        maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    }
    return "₹${nf.format(amount)}"
}

/** Parsed-entity chip text for a card: signed amount (Accounts) or due date (Reminder). */
fun messageEntityText(msg: SMSMessage, tx: FinanceTx?, reminder: ReminderSms?): String? {
    if (tx != null) {
        val sign = if (tx.isCredit) "" else "−"
        return "$sign${formatRupees(tx.amount)}"
    }
    if (reminder != null) {
        val due = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(reminder.dueDate))
        return "Due $due"
    }
    return null
}

@Composable
fun InboxFilterPill(label: String, count: Int, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) color else color.copy(alpha = 0.45f),
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = if (selected) 0.28f else 0.16f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                count.toString(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
fun AvatarTile(
    label: String,
    color: Color,
    size: Dp = 44.dp,
    corner: Dp = 13.dp,
    fontSize: TextUnit = 17.sp,
    photoUri: android.net.Uri? = null
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (photoUri != null) {
            coil.compose.AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(label, fontWeight = FontWeight.ExtraBold, color = color, fontSize = fontSize)
        }
    }
}

// ==========================================
// SCREEN: SENDER INFO (aggregated per-sender view, opened from the thread overflow menu)
// ==========================================
@Composable
fun SenderInfoScreen(viewModel: SmsOrganizerViewModel, sender: String) {
    val context = LocalContext.current
    val allMessages by viewModel.allMessages.collectAsState()
    val msgs = remember(allMessages, sender) { allMessages.filter { it.sender == sender } }

    val senderName = displayNameFor(sender)
    val photoUri = photoUriFor(sender)
    val accent = categoryColor(msgs.maxByOrNull { it.timestamp }?.category ?: "Personal")

    val received = msgs.count { it.type == SMSMessage.TYPE_INBOX }
    val sent = msgs.count { it.type == SMSMessage.TYPE_SENT }
    val firstSeen = msgs.minByOrNull { it.timestamp }?.timestamp
    val lastSeen = msgs.maxByOrNull { it.timestamp }?.timestamp
    val byCategory = remember(msgs) {
        msgs.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }
    }
    val isBlocked = msgs.any { it.isBlocked }
    val isSpam = msgs.isNotEmpty() && msgs.all { it.category == "Spam" }
    val muted = viewModel.isMuted(sender)
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("sender_info_${sender}")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.closeSenderInfo() },
                modifier = Modifier.testTag("sender_info_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "Sender info",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.5.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarTile(
                label = senderName.take(1).uppercase(Locale.getDefault()),
                color = accent,
                size = 84.dp,
                corner = 26.dp,
                fontSize = 34.sp,
                photoUri = photoUri
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = senderName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            if (senderName != sender) {
                Text(
                    text = sender,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }

            // Status chips (only those currently active)
            if (muted || isBlocked || isSpam) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (muted) SenderStatusChip("Muted", Icons.Default.NotificationsOff, MaterialTheme.colorScheme.primary)
                    if (isBlocked) SenderStatusChip("Blocked", Icons.Default.Block, spamColor())
                    if (isSpam) SenderStatusChip("Spam", Icons.Default.Report, spamColor())
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Stats card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SenderInfoRow("Total messages", msgs.size.toString())
                    SenderInfoRow("Received", received.toString())
                    SenderInfoRow("Sent", sent.toString())
                    firstSeen?.let { SenderInfoRow("First seen", dateFmt.format(Date(it))) }
                    lastSeen?.let { SenderInfoRow("Last seen", dateFmt.format(Date(it))) }
                }
            }

            if (byCategory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "By category",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        byCategory.forEach { (category, count) ->
                            val color = categoryColor(category)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = category,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = count.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = color
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick actions
            if (viewModel.isUnknownContact(sender)) {
                SenderActionButton("Add to contacts", Icons.Default.PersonAdd) {
                    viewModel.addToContacts(sender)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            SenderActionButton(
                label = if (muted) "Unmute notifications" else "Mute notifications",
                icon = Icons.Default.NotificationsOff
            ) {
                viewModel.toggleMute(sender)
                Toast.makeText(
                    context,
                    if (viewModel.isMuted(sender)) "Notifications muted" else "Notifications unmuted",
                    Toast.LENGTH_SHORT
                ).show()
            }
            Spacer(modifier = Modifier.height(10.dp))
            SenderActionButton("Report as spam", Icons.Default.Report, tint = spamColor()) {
                viewModel.reportSpamSender(sender)
                Toast.makeText(context, "Reported as spam", Toast.LENGTH_SHORT).show()
                viewModel.closeSenderInfo()
                viewModel.closeThread()
            }
            Spacer(modifier = Modifier.height(10.dp))
            SenderActionButton("Block sender", Icons.Default.Block, tint = spamColor()) {
                viewModel.blockConversation(sender)
                viewModel.closeSenderInfo()
                viewModel.closeThread()
            }
        }
    }
}

@Composable
private fun SenderStatusChip(label: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun SenderInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SenderActionButton(
    label: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sender_action_${label}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f))
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun SimPill(simId: Int) {
    Text(
        text = "SIM $simId",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun EntityChip(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
internal fun CopyOtpChip(otp: String, msgId: Long) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            clipboardManager.setText(AnnotatedString(otp))
            Toast.makeText(context, "OTP $otp copied to clipboard!", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier
            .height(34.dp)
            .testTag("copy_otp_button_$msgId"),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFFFFB300).copy(alpha = 0.18f),
            contentColor = Color(0xFFE0A900)
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        shape = RoundedCornerShape(11.dp)
    ) {
        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy OTP", modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text("Copy OTP ($otp)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** A single behind-the-card swipe action (Block / Delete). */
@Composable
private fun SwipeAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(66.dp)
            .background(container)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = content, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(5.dp))
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

// ==========================================
// SCREEN: MESSAGE DETAIL (Direction A "in depth")
// ==========================================
@Composable
fun MessageDetailScreen(viewModel: SmsOrganizerViewModel, msg: SMSMessage) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val transactions by viewModel.transactions.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    val tx = remember(transactions, msg.id) { transactions.firstOrNull { it.messageId == msg.id } }
    val reminder = remember(reminders, msg.id) { reminders.firstOrNull { it.messageId == msg.id } }
    val categoryColor = categoryColor(msg.category)
    val otp = remember(msg.body) { detectOtp(msg.body) }

    val isPaid = viewModel.paidMessageIds.value.contains(msg.id)

    val dateText = remember(msg.timestamp) {
        SimpleDateFormat("dd MMM · hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    }
    val annotatedBody = remember(msg.body, otp) {
        buildAnnotatedString {
            if (otp != null) {
                val i = msg.body.indexOf(otp)
                if (i >= 0) {
                    append(msg.body.substring(0, i))
                    withStyle(
                        SpanStyle(
                            color = Color(0xFFE0A900),
                            fontWeight = FontWeight.ExtraBold,
                            background = Color(0xFFFFB300).copy(alpha = 0.15f)
                        )
                    ) { append(otp) }
                    append(msg.body.substring(i + otp.length))
                } else append(msg.body)
            } else append(msg.body)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("message_detail_${msg.id}")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.closeMessage() },
                modifier = Modifier.testTag("detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            val senderName = displayNameFor(msg.sender)
            AvatarTile(
                label = senderName.take(1).uppercase(Locale.getDefault()),
                color = categoryColor,
                size = 38.dp,
                corner = 12.dp,
                fontSize = 16.sp,
                photoUri = photoUriFor(msg.sender)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = senderName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.5.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${msg.category} · SIM ${msg.simId}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Encryption banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(goodSoftColor())
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = goodColor(),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    "End-to-end encrypted · stored only on this device",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = goodColor()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date divider
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                Text(
                    text = dateText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Message bubble (left aligned)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                    )
                    .padding(horizontal = 15.dp, vertical = 13.dp)
            ) {
                Column {
                    if (msg.isMms && !msg.attachmentUri.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Attachment",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MMS attachment",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = annotatedBody,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 5.dp)
            ) {
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
                val statusLabel = deliveryStatusLabel(msg)
                if (statusLabel != null) {
                    Text(
                        text = " · $statusLabel",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (msg.status == SMSMessage.STATUS_FAILED) spamColor()
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }

            // Smart card — detected entities
            if (tx != null || reminder != null) {
                Spacer(modifier = Modifier.height(18.dp))
                MessageSmartCard(
                    msg = msg,
                    tx = tx,
                    reminder = reminder,
                    isPaid = isPaid,
                    onAddReminder = {
                        val due = reminder?.dueDate ?: (System.currentTimeMillis() + 24 * 3600 * 1000L)
                        viewModel.addReminderForMessage(msg, "${msg.sender} payment", due)
                        Toast.makeText(context, "Reminder added", Toast.LENGTH_SHORT).show()
                    },
                    onMarkPaid = {
                        viewModel.togglePaid(msg.id)
                    }
                )
            }

            // Copy OTP (full-width) when present
            if (otp != null) {
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(otp))
                        Toast.makeText(context, "OTP $otp copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("detail_copy_otp_${msg.id}"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFFB300).copy(alpha = 0.18f),
                        contentColor = Color(0xFFE0A900)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("Copy OTP ($otp)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Sender-scoped actions (Mute / Block / Report / Delete) now live on the thread
            // (sender) level — see ThreadScreen's overflow menu. This screen is message-only.

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MessageSmartCard(
    msg: SMSMessage,
    tx: FinanceTx?,
    reminder: ReminderSms?,
    isPaid: Boolean,
    onAddReminder: () -> Unit,
    onMarkPaid: () -> Unit
) {
    // Build up to two stat tiles from the parsed entities.
    val tiles = buildList {
        if (tx != null) {
            val sign = if (tx.isCredit) "" else "−"
            add("Amount" to "$sign${formatRupees(tx.amount)}")
        }
        if (reminder != null) {
            add("Due date" to SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(reminder.dueDate)))
        }
        if (tx != null && tx.balance > 0.0) {
            add("Balance" to formatRupees(tx.balance))
        }
    }.take(2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                "Detected in this message",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(13.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.forEach { (label, value) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(11.dp)
                ) {
                    Text(
                        label.uppercase(Locale.getDefault()),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.em,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        value,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(13.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = onAddReminder,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add reminder", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = onMarkPaid,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isPaid) goodColor() else MaterialTheme.colorScheme.onBackground
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPaid) "Paid" else "Mark as paid", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ==========================================
// SECURITY HELPERS FOR DEVICE LOCK
// ==========================================
@Suppress("DEPRECATION")
fun triggerDeviceAuthentication(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    onImmediateBypass: () -> Unit
) {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (keyguardManager?.isDeviceSecure == true) {
        try {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "SMS Sentry Safeguard",
                "Confirm screen lock credentials to view secure ledger and transactions"
            )
            if (intent != null) {
                launcher.launch(intent)
            } else {
                onImmediateBypass()
            }
        } catch (e: Exception) {
            onImmediateBypass()
        }
    } else {
        onImmediateBypass()
    }
}

@Composable
fun FinanceLockedScreen(viewModel: SmsOrganizerViewModel) {
    val context = LocalContext.current
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.isFinanceAuthenticated.value = true
            Toast.makeText(context, "Access Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Verification failed or canceled.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Secure",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = "Accounts Folder Locked",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "This screen displays personal bank transaction logs, spend ratios, and balances. Access requires active device confirmation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        triggerDeviceAuthentication(context, authLauncher, onImmediateBypass = {
                            viewModel.isFinanceAuthenticated.value = true
                            Toast.makeText(context, "Identity Verified (Sandbox Bypass)", Toast.LENGTH_SHORT).show()
                        })
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = "Unlock")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Passcode", fontWeight = FontWeight.Bold)
                }

                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (keyguardManager?.isDeviceSecure != true) {
                    Text(
                        text = "Sandbox Tip: No active lock screen PIN or password detected on this emulator profile. Bypass mode automatically active for testing.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN: FINANCE SECTION
// ==========================================

/** Start-of-current-calendar-month in millis; the boundary for the "(This Month)" finance totals. */
private fun monthStartMillis(): Long =
    Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * Single source of truth for the finance card totals AND the contribution breakdown screen:
 * the current-month transactions split into (credits, debits). Both surfaces derive from this so
 * the breakdown list can never silently disagree with the headline figure on the card.
 */
private fun monthlyContributions(transactions: List<FinanceTx>): Pair<List<FinanceTx>, List<FinanceTx>> {
    val monthStart = monthStartMillis()
    val month = transactions.filter { it.timestamp >= monthStart }
    return month.filter { it.isCredit } to month.filter { !it.isCredit }
}

/**
 * Single source of truth for the "latest parsed balance" shown on both the Dashboard
 * "Available Balance" card and the Accounts "Estimated Liquid Savings" card. Both surfaces
 * derive from this so they can never silently disagree (they previously did: the dashboard
 * showed 0.00 while Accounts showed the real balance).
 *
 * The transaction stream is newest-first, but the most recent row frequently carries no
 * balance (balance == 0.0 when the SMS had no balance to parse), so we return the most
 * recent transaction that actually carried one.
 *
 * KNOWN LIMITATION: `balance` is a non-nullable Double defaulting to 0.0, so this cannot
 * distinguish "no balance parsed" from a genuine 0.0 balance, and it skips negative
 * (overdraft) balances. A genuinely-zero or negative current balance will therefore show an
 * older positive figure. Fixing that properly requires a nullable `balance` column + migration.
 */
internal fun latestParsedBalance(transactions: List<FinanceTx>): Double =
    transactions.firstOrNull { it.balance > 0.0 }?.balance ?: 0.0

@Composable
fun FinanceScreen(viewModel: SmsOrganizerViewModel) {
    val isSecured = viewModel.isFinanceLocked.value
    val isAuthenticated = viewModel.isFinanceAuthenticated.value
    if (isSecured && !isAuthenticated) {
        FinanceLockedScreen(viewModel = viewModel)
        return
    }

    val transactions by viewModel.transactions.collectAsState()

    // Compute stats. Credit/debit totals are scoped to the current calendar month to match the
    // "(This Month)" labels below; the same split feeds the tap-through breakdown screen.
    val (monthCredits, monthDebits) = remember(transactions) { monthlyContributions(transactions) }
    val totalCredits = monthCredits.sumOf { it.amount }
    val totalDebits = monthDebits.sumOf { it.amount }
    // Liquid savings = latest parsed balance (shared with the Dashboard card so they agree).
    val activeBalance = latestParsedBalance(transactions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Accounts Card Overview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "ESTIMATED LIQUID SAVINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("Rs. %,.2f", activeBalance),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.openContribution(ContribKind.CREDIT) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Total Credit (This Month)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        Text(text = String.format("+Rs. %,.2f", totalCredits), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = goodColor())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Tap to verify", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.openContribution(ContribKind.DEBIT) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Total Debit (This Month)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        Text(text = String.format("-Rs. %,.2f", totalDebits), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = spamColor())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Tap to verify", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        // Accuracy disclaimer: these figures are heuristically parsed from SMS text and can be
        // incomplete or wrong — make sure the user treats them as an estimate, not a statement.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Estimated from your SMS messages — may be incomplete or inaccurate. " +
                    "Always verify with your bank's official statement.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Automated Transaction Ledger",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Tracks banking texts and parses amounts offline instantly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No account SMS parsed yet. Tap '+' in top bar to simulate bank alerts!", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions) { tx ->
                    TransactionRowItem(tx = tx, onClick = { viewModel.openMessageById(tx.messageId) })
                }
            }
        }
    }
}

@Composable
fun TransactionRowItem(tx: FinanceTx, onClick: () -> Unit = {}) {
    val dateText = remember(tx.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(tx.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconColor = if (tx.isCredit) goodColor() else spamColor()
                val icon = if (tx.isCredit) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = "Tx Flow", tint = iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = tx.bankName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = dateText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val sign = if (tx.isCredit) "+ " else "- "
                val amtCol = if (tx.isCredit) goodColor() else spamColor()
                Text(
                    text = String.format("$sign Rs. %,.2f", tx.amount),
                    fontWeight = FontWeight.ExtraBold,
                    color = amtCol,
                    fontSize = 15.sp
                )
                Text(
                    text = String.format("Bal: Rs. %,.2f", tx.balance),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// SCREEN: CONTRIBUTION BREAKDOWN (audit the finance card totals)
// ==========================================
/**
 * Full-screen overlay listing exactly the SMS-derived transactions that were summed into the
 * tapped "Total Credit"/"Total Debit (This Month)" figure. The header total is computed from the
 * same [monthlyContributions] split as the card, so the two always agree. Tapping a row opens the
 * original SMS so the user can confirm the parsed amount and credit/debit direction. View-only:
 * there is no in-place correction of a mis-parsed entry.
 */
@Composable
fun ContributionBreakdownScreen(viewModel: SmsOrganizerViewModel, kind: ContribKind) {
    val transactions by viewModel.transactions.collectAsState()
    val (credits, debits) = remember(transactions) { monthlyContributions(transactions) }
    val rows = if (kind == ContribKind.CREDIT) credits else debits
    val total = rows.sumOf { it.amount }
    val isCredit = kind == ContribKind.CREDIT
    val title = if (isCredit) "Income — This Month" else "Expenses — This Month"
    val accent = if (isCredit) goodColor() else spamColor()
    val sign = if (isCredit) "+" else "-"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.closeContribution() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = "${rows.size} transaction${if (rows.size == 1) "" else "s"} · tap one to view the SMS",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Text(
                text = String.format("$sign Rs. %,.2f", total),
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                fontSize = 16.sp
            )
        }

        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No ${if (isCredit) "income" else "expense"} messages this month.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rows) { tx ->
                    TransactionRowItem(tx = tx, onClick = { viewModel.openMessageById(tx.messageId) })
                }
            }
        }
    }
}

// ==========================================
// SCREEN: REMINDERS SECTION (TRACK REMINDERS)
// ==========================================
@Composable
fun RemindersScreen(viewModel: SmsOrganizerViewModel) {
    val reminders by viewModel.reminders.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Bell",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Intelligent Reminders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Any SMS containing bills, appointments or deadlines is saved here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Prompt to grant exact-alarm permission when alerts are on but the OS withholds them,
        // otherwise due-alerts silently never fire.
        val alertsOn by viewModel.reminderAlertsEnabled
        if (alertsOn && !viewModel.canScheduleExactAlarms()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Allow exact alarms so due-date alerts can fire.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { context.startActivity(viewModel.buildExactAlarmSettingsIntent()) }) {
                        Text("Allow", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Active Reminders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No pending reminders. Safe and calm :)",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderRowItem(
                        reminder = reminder,
                        onOpen = { viewModel.openMessageById(reminder.messageId) },
                        onAddToCalendar = { viewModel.addEventToCalendar(context, reminder) },
                        onDismiss = { viewModel.deleteReminder(reminder.id) },
                        onToggleAlert = { enabled -> viewModel.setReminderAlert(reminder.id, enabled) },
                        onRecurrenceChange = { rec -> viewModel.setReminderRecurrence(reminder.id, rec) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderRowItem(
    reminder: ReminderSms,
    onOpen: () -> Unit,
    onAddToCalendar: () -> Unit,
    onDismiss: () -> Unit,
    onToggleAlert: (Boolean) -> Unit,
    onRecurrenceChange: (String) -> Unit
) {
    val dateText = remember(reminder.dueDate) {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        sdf.format(Date(reminder.dueDate))
    }
    var recurrenceMenuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "Event",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = reminder.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (reminder.isSyncedToCalendar) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Calendar Synced", fontSize = 10.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Check, contentDescription = "Synced", modifier = Modifier.size(12.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = goodSoftColor(),
                            labelColor = goodColor()
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scheduled For: $dateText",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reminder.body,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            // Per-reminder controls: in-app due-alert toggle + recurrence cadence.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { onToggleAlert(!reminder.alertEnabled) },
                    label = { Text(if (reminder.alertEnabled) "Alert on" else "Alert off", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (reminder.alertEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Toggle alert",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = if (reminder.alertEnabled) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )

                Box {
                    AssistChip(
                        onClick = { recurrenceMenuOpen = true },
                        label = { Text(RecurrenceUtil.label(reminder.recurrence), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Recurrence",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = recurrenceMenuOpen,
                        onDismissRequest = { recurrenceMenuOpen = false }
                    ) {
                        RecurrenceUtil.ALL.forEach { rec ->
                            DropdownMenuItem(
                                text = { Text(RecurrenceUtil.label(rec)) },
                                onClick = {
                                    recurrenceMenuOpen = false
                                    if (rec != reminder.recurrence) onRecurrenceChange(rec)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Dismiss", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAddToCalendar,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Calendar", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Calendar", fontSize = 12.sp)
                }
            }
        }
    }
}

// ==========================================
// SCREEN: PEER TO PEER SYNC SECTION
// ==========================================
// Groups the raw pairing code into hyphen-separated blocks for readable display/transcription.
private fun formatPairingCode(raw: String): String =
    raw.chunked(P2PSyncEngine.PAIRING_CODE_GROUP).joinToString("-")

@Composable
fun SyncScreen(viewModel: SmsOrganizerViewModel) {
    val syncState by viewModel.syncState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showHostPanel by remember { mutableStateOf(false) }
    var showJoinPanel by remember { mutableStateOf(false) }

    var ipInput by remember { mutableStateOf("") }
    var pinValue by remember { mutableStateOf("") }

    val localIp = remember { viewModel.getLocalIp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature description Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Encrypted Peer-to-Peer Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Transmit and back up messages securely between dual devices in real-time. Works entirely offline over local Wi-Fi hotspots or LAN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Active State monitor
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("CURRENT STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                when (val state = syncState) {
                    is SyncState.Idle -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Gray))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Local Server is Offline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    is SyncState.Hosting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(goodColor()))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Active Sync Server", fontWeight = FontWeight.Bold, color = goodColor())
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Ip Address: ${state.ipAddress}", fontSize = 13.sp)
                                Text("Port Node: ${state.port}", fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Pairing code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        formatPairingCode(state.code),
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(formatPairingCode(state.code)))
                                        Toast.makeText(context, "Pairing code copied", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy pairing code", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { viewModel.stopHosting() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Stop Sync Server")
                        }
                    }
                    is SyncState.Connecting -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Handshaking over sockets...", style = MaterialTheme.typography.bodySmall)
                    }
                    is SyncState.Syncing -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Exchanging encrypted files securely...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    is SyncState.Completed -> {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Check", tint = goodColor(), modifier = Modifier.size(36.dp))
                        Text("Sync Completed Successfully!", color = goodColor(), fontWeight = FontWeight.Bold)
                        if (state.exportedCount > 0) {
                            Text("Exported ${state.exportedCount} messages.", fontSize = 12.sp)
                        }
                        if (state.importedCount > 0) {
                            Text("Imported & merged ${state.importedCount} messages.", fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.resetSyncState() }) {
                            Text("Dismiss Status")
                        }
                    }
                    is SyncState.Error -> {
                        Icon(imageVector = Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                        Text("Exchange Interrupted", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(state.message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        TextButton(onClick = { viewModel.resetSyncState() }) {
                            Text("Retry Sync")
                        }
                    }
                }
            }
        }

        // Actions Panels
        if (syncState is SyncState.Idle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        showHostPanel = true
                        showJoinPanel = false
                        pinValue = viewModel.generatePairingCode()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Host Server", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        showJoinPanel = true
                        showHostPanel = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Connect to Peer", maxLines = 1)
                }
            }
        }

        // Host server action configuration
        if (showHostPanel && syncState is SyncState.Idle) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Host Configuration", fontWeight = FontWeight.Bold)
                    Text("Exposes your active message logs. The peer must look for your IP and insert matching parameters.", fontSize = 12.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Your IP Address: ", fontSize = 13.sp)
                        Text(localIp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("One-time pairing code", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(formatPairingCode(pinValue)))
                            Toast.makeText(context, "Pairing code copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy pairing code", modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        formatPairingCode(pinValue),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Enter this exact code on the peer device. It is generated fresh for this session and never sent over the network.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            viewModel.hostSyncServer(pinValue)
                            showHostPanel = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Turn On Host Server")
                    }
                }
            }
        }

        // Join server action configuration
        if (showJoinPanel && syncState is SyncState.Idle) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Connect to Peer", fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("Host Server IP Address") },
                        placeholder = { Text("e.g. 192.168.1.45") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { pinValue = it },
                        label = { Text("Pairing code from host") },
                        placeholder = { Text("e.g. K7M2P9QR-3T6VWX4Y-…") },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            if (ipInput.trim().isEmpty() || pinValue.trim().isEmpty()) {
                                return@Button
                            }
                            viewModel.joinSyncServer(ipInput, pinValue)
                            showJoinPanel = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Establish Sync Sync Connection")
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN: SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: SmsOrganizerViewModel, onOpenImport: () -> Unit) {
    // null = main page; other keys open dedicated sub-pages (same nav pattern throughout).
    var subPage by rememberSaveable { mutableStateOf<String?>(null) }

    if (subPage != null) {
        BackHandler { subPage = null }
    }

    when (subPage) {
        "theme" -> ThemeSettingsPage(
            viewModel = viewModel,
            onBack = { subPage = null }
        )
        "integration" -> IntegrationSettingsPage(
            viewModel = viewModel,
            onBack = { subPage = null }
        )
        "permissions" -> PermissionsSettingsPage(
            viewModel = viewModel,
            onBack = { subPage = null }
        )
        "advanced" -> AdvancedSettingsPage(
            viewModel = viewModel,
            onOpenImport = onOpenImport,
            onBack = { subPage = null }
        )
        "testing" -> TestingSandboxPage(
            viewModel = viewModel,
            onBack = { subPage = null }
        )
        "about" -> AboutPage(onBack = { subPage = null })
        else -> SettingsMainPage(
            viewModel = viewModel,
            onOpenTheme = { subPage = "theme" },
            onOpenIntegration = { subPage = "integration" },
            onOpenPermissions = { subPage = "permissions" },
            onOpenAdvanced = { subPage = "advanced" },
            onOpenTesting = { subPage = "testing" },
            onOpenAbout = { subPage = "about" }
        )
    }
}

// ------------------------------------------------------------------
// Main settings page: aesthetics/theme + system integration + nav rows
// ------------------------------------------------------------------
private data class SchemeMeta(
    val style: ThemeStyle,
    val name: String,
    val desc: String,
    val swatch: Color
)

@Composable
private fun SettingsMainPage(
    viewModel: SmsOrganizerViewModel,
    onOpenTheme: () -> Unit,
    onOpenIntegration: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenTesting: () -> Unit,
    onOpenAbout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero
        item {
            Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                Text(
                    "PREFERENCES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.08.em
                )
                Text(
                    "Settings",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // ---- Navigation rows ----
        item {
            SettingsNavRow(
                icon = Icons.Default.Palette,
                title = "Appearance & theme",
                subtitle = "Color scheme & light/dark mode",
                onClick = onOpenTheme
            )
        }
        item {
            SettingsNavRow(
                icon = Icons.Default.Extension,
                title = "System integration",
                subtitle = "Default SMS app & permissions",
                onClick = onOpenIntegration
            )
        }
        item {
            SettingsNavRow(
                icon = Icons.Default.Lock,
                title = "Permissions",
                subtitle = "What SMS Sentry can access & why",
                onClick = onOpenPermissions
            )
        }
        item {
            SettingsNavRow(
                icon = Icons.Default.Tune,
                title = "Advanced settings",
                subtitle = "Security, SIM, filters & backup",
                onClick = onOpenAdvanced
            )
        }
        item {
            SettingsNavRow(
                icon = Icons.Default.Science,
                title = "Testing",
                subtitle = "SMS Sentry sandbox simulator",
                onClick = onOpenTesting
            )
        }
        item {
            SettingsNavRow(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "Author, build & tooling info",
                onClick = onOpenAbout
            )
        }

        // ---- Footer ----
        item {
            Text(
                text = "SMS Sentry · v${BuildConfig.VERSION_NAME} · Offline build",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

// ------------------------------------------------------------------
// Appearance & theme sub-page: color scheme + light/dark mode
// ------------------------------------------------------------------
@Composable
private fun ThemeSettingsPage(viewModel: SmsOrganizerViewModel, onBack: () -> Unit) {
    var appTheme by viewModel.selectedTheme
    var forceDark by viewModel.isDarkTheme
    var systemTheme by viewModel.isSystemTheme

    val appearanceMode = when {
        systemTheme -> "system"
        forceDark -> "dark"
        else -> "light"
    }
    val systemDark = isSystemInDarkTheme()
    val modeLabel = when (appearanceMode) {
        "system" -> if (systemDark) "Auto · Dark" else "Auto · Light"
        "dark" -> "Dark"
        else -> "Light"
    }
    val modeHint = when (appearanceMode) {
        "system" -> "Follows your phone's appearance setting automatically."
        "dark" -> "Dark theme is forced on across the whole app."
        else -> "Light theme is forced on across the whole app."
    }

    val schemes = listOf(
        SchemeMeta(ThemeStyle.LAVENDER, "Lavender", "Soft violet", Color(0xFF6F57AB)),
        SchemeMeta(ThemeStyle.HIGH_DENSITY, "High Density", "Indigo · compact", Color(0xFF284A8B)),
        SchemeMeta(ThemeStyle.SAGE, "Sage", "Calm green", Color(0xFF416F52)),
        SchemeMeta(ThemeStyle.SLATE, "Cosmic Slate", "Muted plum", Color(0xFF495582)),
        SchemeMeta(ThemeStyle.BLUE, "Slate Blue", "Cool blue", Color(0xFF316E9B)),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SubPageHeader("Appearance & theme", onBack) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "COLOR SCHEME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Soothing shades",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        schemes.forEach { meta ->
                            SchemeCard(
                                meta = meta,
                                selected = appTheme == meta.style,
                                onClick = { appTheme = meta.style }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "APPEARANCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            modeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AppearanceSegmented(
                        mode = appearanceMode,
                        onSelect = { m ->
                            when (m) {
                                "light" -> { systemTheme = false; forceDark = false }
                                "dark" -> { systemTheme = false; forceDark = true }
                                "system" -> { systemTheme = true }
                            }
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            modeHint,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

// ------------------------------------------------------------------
// System integration sub-page: default SMS app + permissions
// ------------------------------------------------------------------
@Composable
private fun IntegrationSettingsPage(viewModel: SmsOrganizerViewModel, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SubPageHeader("System integration", onBack) }
        item { DefaultSmsAppCard(viewModel) }
        item { SystemIntegrationCard() }
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

// ------------------------------------------------------------------
// Permissions sub-page: full inventory of what the app accesses & why
// ------------------------------------------------------------------

/** A dangerous (runtime-prompted) permission the app requests explicitly. */
private data class RuntimePermissionInfo(
    val permission: String,
    val label: String,
    val reason: String,
    /** Only requested/relevant at or above this SDK; below it is implicitly granted or absent. */
    val minSdk: Int = 0
)

private val RUNTIME_PERMISSIONS = listOf(
    RuntimePermissionInfo(android.Manifest.permission.RECEIVE_SMS, "Receive SMS", "Receive incoming text messages as they arrive."),
    RuntimePermissionInfo(android.Manifest.permission.READ_SMS, "Read SMS", "Read existing messages on your phone to organize them."),
    RuntimePermissionInfo(android.Manifest.permission.SEND_SMS, "Send SMS", "Send replies and scheduled messages."),
    RuntimePermissionInfo(android.Manifest.permission.RECEIVE_MMS, "Receive MMS", "Receive picture / group (MMS) messages."),
    RuntimePermissionInfo(android.Manifest.permission.RECEIVE_WAP_PUSH, "Receive WAP push", "Detect incoming MMS notifications from the carrier."),
    RuntimePermissionInfo(android.Manifest.permission.READ_CONTACTS, "Read contacts", "Show sender names instead of raw numbers."),
    RuntimePermissionInfo(android.Manifest.permission.READ_PHONE_STATE, "Phone state", "Detect active SIMs for correct dual-SIM sending."),
    RuntimePermissionInfo(
        android.Manifest.permission.POST_NOTIFICATIONS,
        "Notifications",
        "Show new-message, OTP and reminder notifications.",
        minSdk = android.os.Build.VERSION_CODES.TIRAMISU
    )
)

/** A normal permission Android grants automatically at install (no runtime prompt). */
private data class ImplicitPermissionInfo(val label: String, val reason: String)

private val IMPLICIT_PERMISSIONS = listOf(
    ImplicitPermissionInfo("Internet", "Local peer-to-peer sync over Wi-Fi (no data leaves your network)."),
    ImplicitPermissionInfo("Network state", "Check connectivity for peer-to-peer sync."),
    ImplicitPermissionInfo("Wi-Fi state", "Discover peers on the same Wi-Fi network."),
    ImplicitPermissionInfo("Change Wi-Fi state", "Establish the local sync connection."),
    ImplicitPermissionInfo("Run after reboot", "Re-arm scheduled messages and reminders after a reboot.")
)

@Composable
private fun PermissionsSettingsPage(viewModel: SmsOrganizerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current

    fun isGranted(p: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, p) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    // Bumped to force a recompute of grant state after the prompt returns or on resume.
    var grantTick by remember { mutableStateOf(0) }
    val applicable = remember {
        RUNTIME_PERMISSIONS.filter { android.os.Build.VERSION.SDK_INT >= it.minSdk }
    }
    val grants = remember(grantTick) { applicable.associate { it.permission to isGranted(it.permission) } }
    val exactAlarmOk = remember(grantTick) { viewModel.canScheduleExactAlarms() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> grantTick++ }

    // Refresh pills when returning from the system settings / app-info screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) grantTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SubPageHeader("Permissions", onBack) }
        item {
            Text(
                text = "Everything SMS Sentry can access, and exactly why. The app works fully offline — " +
                       "network access is only for optional peer-to-peer sync on your own Wi-Fi.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Requested at runtime (dangerous) ----
        item {
            PermissionSectionHeader(
                "REQUESTED AT RUNTIME",
                "You're asked to allow these — you can change them anytime."
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("permissions_runtime_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    applicable.forEachIndexed { i, info ->
                        PermissionRow(
                            granted = grants[info.permission] == true,
                            label = info.label,
                            reason = info.reason,
                            statusOn = "Allowed",
                            statusOff = "Not allowed",
                            showDivider = i < applicable.lastIndex
                        )
                    }
                }
            }
        }
        item {
            val anyDenied = applicable.any { grants[it.permission] != true }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anyDenied) {
                    Button(
                        onClick = { permissionLauncher.launch(applicable.map { it.permission }.toTypedArray()) },
                        modifier = Modifier.weight(1.5f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Grant permissions", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open app info", fontSize = 12.sp)
                }
            }
        }

        // ---- Special access ----
        item {
            PermissionSectionHeader(
                "SPECIAL ACCESS",
                "Granted from system settings rather than a normal prompt."
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PermissionRow(
                        granted = exactAlarmOk,
                        label = "Alarms & reminders",
                        reason = "Deliver scheduled SMS and reminders at the exact time.",
                        statusOn = "Allowed",
                        statusOff = "Not allowed",
                        showDivider = false
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !exactAlarmOk) {
                        OutlinedButton(
                            onClick = { context.startActivity(viewModel.buildExactAlarmSettingsIntent()) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                        ) {
                            Text("Open settings", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ---- Granted automatically (normal) ----
        item {
            PermissionSectionHeader(
                "GRANTED AUTOMATICALLY",
                "Standard permissions Android grants at install — no prompt, and they can't be revoked individually."
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    IMPLICIT_PERMISSIONS.forEachIndexed { i, info ->
                        PermissionRow(
                            granted = true,
                            label = info.label,
                            reason = info.reason,
                            statusOn = "Auto",
                            statusOff = "Auto",
                            showDivider = i < IMPLICIT_PERMISSIONS.lastIndex
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
private fun PermissionSectionHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(
    granted: Boolean,
    label: String,
    reason: String,
    statusOn: String,
    statusOff: String,
    showDivider: Boolean
) {
    val good = goodColor()
    val goodSoft = goodSoftColor()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (granted) goodSoft else MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) good else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(reason, fontSize = 11.5.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        val pillText = if (granted) statusOn else statusOff
        val pillFg = if (granted) good else MaterialTheme.colorScheme.error
        val pillBg = if (granted) goodSoft else MaterialTheme.colorScheme.errorContainer
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(pillBg)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(pillText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = pillFg)
        }
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun SchemeCard(meta: SchemeMeta, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .width(50.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            ) {
                Box(modifier = Modifier.weight(2.4f).fillMaxHeight().background(meta.swatch))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(meta.swatch.copy(alpha = 0.55f)))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(meta.swatch.copy(alpha = 0.22f)))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    meta.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    meta.desc,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}

@Composable
private fun AppearanceSegmented(mode: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppearanceSegment(Modifier.weight(1f), Icons.Default.LightMode, "Light", mode == "light") { onSelect("light") }
        AppearanceSegment(Modifier.weight(1f), Icons.Default.DarkMode, "Dark", mode == "dark") { onSelect("dark") }
        AppearanceSegment(Modifier.weight(1f), Icons.Default.PhoneAndroid, "System", mode == "system") { onSelect("system") }
    }
}

@Composable
private fun AppearanceSegment(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ------------------------------------------------------------------
// Default SMS app card (request the role + import existing messages)
// ------------------------------------------------------------------
@Composable
private fun DefaultSmsAppCard(viewModel: SmsOrganizerViewModel) {
    val context = LocalContext.current
    val isDefault by viewModel.isDefaultSmsApp

    // Re-check default-app status whenever this screen resumes. The Activity also refreshes in
    // onResume, but this keeps the card correct if it becomes visible without an Activity resume.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDefaultStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.refreshDefaultStatus()
        if (`in`.sreerajp.sms_sentry.util.DefaultSmsAppManager.isDefault(context)) {
            viewModel.importSystemSms { count ->
                Toast.makeText(context, "Imported $count messages from phone.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (isDefault) Icons.Default.CheckCircle else Icons.Default.Sms,
                    contentDescription = null,
                    tint = if (isDefault) goodColor() else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Default SMS app",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isDefault) "SMS Sentry is your default SMS app. Sending, two-way delete and full sync are on."
                               else "Set SMS Sentry as default to send SMS, sync all phone messages, and delete from the phone too.",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Always surface which app the system currently treats as default, so the
                    // status is unambiguous even if the two readings ever disagree.
                    val currentDefault = remember(isDefault) {
                        `in`.sreerajp.sms_sentry.util.DefaultSmsAppManager.currentDefaultPackage(context)
                    }
                    Text(
                        text = "Current system default: ${currentDefault ?: "none"}",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isDefault) {
                Button(
                    onClick = {
                        try {
                            roleLauncher.launch(`in`.sreerajp.sms_sentry.util.DefaultSmsAppManager.buildRequestIntent(context))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open default-app picker: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("set_default_sms_button")
                ) {
                    Text("Set as default SMS app")
                }
            }

            OutlinedButton(
                onClick = {
                    viewModel.importSystemSms { count ->
                        Toast.makeText(context, "Imported $count new messages.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("import_existing_sms_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import existing SMS")
            }
        }
    }
}

// ------------------------------------------------------------------
// System integration card (permission status + open app info)
// ------------------------------------------------------------------
@Composable
private fun SystemIntegrationCard() {
    val context = LocalContext.current
    var hasSmsPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECEIVE_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { matches ->
        hasSmsPermission = matches[android.Manifest.permission.RECEIVE_SMS] ?: hasSmsPermission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = matches[android.Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("permissions_integration_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "INTEGRATION STATUS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IntegrationStatusRow(
                granted = hasSmsPermission,
                title = "Inbound SMS receiver",
                subGranted = "Enabled · listening offline",
                subDenied = "Disabled: cannot parse SMS",
                showDivider = true
            )
            IntegrationStatusRow(
                granted = hasNotificationPermission,
                title = "Push notifications",
                subGranted = "OTP overlays displayed instantly",
                subDenied = "Disabled: cannot show OTP overlays",
                showDivider = false
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasSmsPermission || !hasNotificationPermission) {
                    Button(
                        onClick = {
                            val list = mutableListOf(android.Manifest.permission.RECEIVE_SMS)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                list.add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(list.toTypedArray())
                        },
                        modifier = Modifier.weight(1.5f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Grant Permission", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open app info", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun IntegrationStatusRow(
    granted: Boolean,
    title: String,
    subGranted: String,
    subDenied: String,
    showDivider: Boolean
) {
    val good = goodColor()
    val goodSoft = goodSoftColor()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (granted) goodSoft else MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) good else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = if (granted) subGranted else subDenied,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val pillText = if (granted) "On" else "Off"
        val pillFg = if (granted) good else MaterialTheme.colorScheme.error
        val pillBg = if (granted) goodSoft else MaterialTheme.colorScheme.errorContainer
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(pillBg)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(pillText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = pillFg)
        }
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    }
}

// ------------------------------------------------------------------
// Advanced settings sub-page: security, SIM, filter rules, backup
// ------------------------------------------------------------------
@Composable
private fun AdvancedSettingsPage(
    viewModel: SmsOrganizerViewModel,
    onOpenImport: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val rules by viewModel.filterRules.collectAsState()

    var showAddRuleState by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    var ruleCategory by remember { mutableStateOf("Spam") }
    var ruleTypeSelection by remember { mutableStateOf("KEYWORD") }
    var defaultSimChoice by viewModel.defaultSmsSim
    val activeSims by viewModel.activeSims.collectAsState()
    var autoMarkReadDelay by viewModel.autoMarkReadDelaySeconds

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SubPageHeader("Advanced settings", onBack) }

        // --- Security & Privacy ---
        item {
            Text("Security & Privacy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("security_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("FINANCE DATA PROTECTION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Secure Accounts Info", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Require phone lock credentials to view electronic ledger balances, credit/debit logs, and spend ratios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        var isLockedCheck by viewModel.isFinanceLocked
                        Switch(
                            checked = isLockedCheck,
                            onCheckedChange = {
                                isLockedCheck = it
                                if (!it) {
                                    viewModel.isFinanceAuthenticated.value = false
                                }
                            }
                        )
                    }

                    if (viewModel.isFinanceLocked.value) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Temporary Verification State", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (viewModel.isFinanceAuthenticated.value) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Unlocked",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unlocked", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { viewModel.isFinanceAuthenticated.value = false },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Lock Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Locked", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Multi-SIM Preferences --- (only on dual-SIM devices with READ_PHONE_STATE granted)
        if (activeSims.size > 1) {
            item {
                Text("Multi-SIM Preferences", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CHOOSE DEFAULT OUTGOING SIM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                        // Option value stays "SIM <slot>" for persistence; the carrier name is the label.
                        val simLabels = activeSims.associate { "SIM ${it.slot}" to it.displayLabel }
                        val simOptions = activeSims.map { "SIM ${it.slot}" } + "Ask Every Time"
                        simOptions.forEach { opt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { defaultSimChoice = opt }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = simLabels[opt] ?: opt, style = MaterialTheme.typography.bodyMedium)
                                RadioButton(
                                    selected = defaultSimChoice == opt,
                                    onClick = { defaultSimChoice = opt }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Reading ---
        item {
            Text("Reading", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AUTO-MARK CONVERSATIONS READ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "When you open a conversation, its messages are marked read after this delay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    // label -> seconds (0 = Off)
                    val delayOptions = listOf(
                        "Off" to 0,
                        "2 seconds" to 2,
                        "3 seconds" to 3,
                        "5 seconds" to 5,
                        "10 seconds" to 10,
                        "15 seconds" to 15
                    )
                    delayOptions.forEach { (label, secs) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { autoMarkReadDelay = secs }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                            RadioButton(
                                selected = autoMarkReadDelay == secs,
                                onClick = { autoMarkReadDelay = secs }
                            )
                        }
                    }
                }
            }
        }

        // --- Reminders ---
        item {
            Text("Reminders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        item {
            var reminderAlertsOn by viewModel.reminderAlertsEnabled
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DUE-DATE ALERTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Reminder due alerts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Notify you in-app at 9:00 AM on a reminder's due date. Each reminder can be silenced or set to repeat individually.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = reminderAlertsOn,
                            onCheckedChange = { reminderAlertsOn = it }
                        )
                    }
                    if (reminderAlertsOn && !viewModel.canScheduleExactAlarms()) {
                        TextButton(
                            onClick = { context.startActivity(viewModel.buildExactAlarmSettingsIntent()) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Allow exact alarms", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Custom Filtering Safeguards ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Filtering Safeguards",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { showAddRuleState = !showAddRuleState }) {
                    Text(if (showAddRuleState) "Cancel" else "+ Add Rule")
                }
            }
        }

        if (showAddRuleState) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Add Filtering Overrule Rule", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = ruleTypeSelection == "KEYWORD",
                                onClick = { ruleTypeSelection = "KEYWORD" },
                                label = { Text("Keyword Block") }
                            )
                            FilterChip(
                                selected = ruleTypeSelection == "CONTACT",
                                onClick = { ruleTypeSelection = "CONTACT" },
                                label = { Text("Block Contact") }
                            )
                        }

                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            label = { Text(if (ruleTypeSelection == "KEYWORD") "Word / Pattern e.g. LUCKY" else "Phone Number e.g. +910900") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("Select Categorization Target:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val listCat = listOf("Personal", "Promotions", "Others", "Spam")
                            listCat.forEach { cat ->
                                FilterChip(
                                    selected = ruleCategory == cat,
                                    onClick = { ruleCategory = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (queryText.trim().isEmpty()) return@Button
                                viewModel.addRule(ruleTypeSelection, queryText.trim(), ruleCategory)
                                queryText = ""
                                showAddRuleState = false
                                Toast.makeText(context, "Added Filter Rule successfully!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Engage Custom Filter Rule")
                        }
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                Text(
                    text = "No custom filters active. Standard AI heuristics analyzing active inbox.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(rules) { r ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(r.type, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(r.value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "Redirects to: ${r.targetCategory}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.removeRule(r.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // --- Categorization ---
        item {
            Text("Categorization", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        item {
            var isRecategorizing by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Re-analyze every stored message and sort it into the current categories " +
                            "(Personal, Promotions, Others, Spam). Finance messages stay flagged for the " +
                            "secured Accounts ledger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            isRecategorizing = true
                            viewModel.recategorizeAllMessages { count ->
                                isRecategorizing = false
                                Toast.makeText(
                                    context,
                                    "Re-categorized $count message${if (count == 1) "" else "s"}.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isRecategorizing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Re-categorize")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRecategorizing) "Re-categorizing…" else "Re-categorize all messages")
                    }
                }
            }
        }

        // --- Backup & Database Integrity ---
        item {
            Text("Backup & Database Integrity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportToCsv(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Export")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Backup as CSV")
                    }

                    Button(
                        onClick = { viewModel.exportToJson(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "JSON")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Backup as JSON")
                    }

                    OutlinedButton(
                        onClick = onOpenImport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Import")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Backup Database")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            viewModel.clearAllSms()
                            Toast.makeText(context, "All SMS & Transactions cleared from Room DB.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wipe Active Databases")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

// ------------------------------------------------------------------
// About sub-page: values sourced from assets/about_config.json
// ------------------------------------------------------------------
@Composable
private fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val about: AboutInfo = remember { loadAboutConfig(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SubPageHeader("About", onBack) }

        // App identity block
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text("SMS Sentry", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "v${BuildConfig.VERSION_NAME} · Offline build",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Values card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    AboutRow(Icons.Default.Person, "Author", about.author, true)
                    AboutRow(Icons.Default.Event, "Last Build Date", about.lastBuildDate, true)
                    AboutRow(Icons.Default.Code, "IDE Used", about.ideUsed, true)
                    AboutRow(Icons.Default.AutoAwesome, "AI Used", about.aiUsed, false)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String, showDivider: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    }
}

// ==========================================
// COMPOSABLE COMPONENT: COMPOSE NEW SMS DIALOG
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSmsDialog(
    viewModel: SmsOrganizerViewModel,
    onDismiss: () -> Unit,
    initialRecipient: String = "",
    initialBody: String = ""
) {
    val context = LocalContext.current
    // Restore the saved compose draft only when opened blank; otherwise honor the explicit prefill.
    val restoreDraft = initialRecipient.isBlank() && initialBody.isBlank()
    val composeDraft = remember { if (restoreDraft) viewModel.composeDraft() else "" to "" }
    var senderInput by remember { mutableStateOf(if (restoreDraft) composeDraft.first else initialRecipient) }
    var smsBodyInput by remember { mutableStateOf(if (restoreDraft) composeDraft.second else initialBody) }
    // Persist the unsent recipient/body when the composer closes without sending/scheduling.
    var handledExit by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (!handledExit) viewModel.saveComposeDraft(senderInput.trim(), smsBodyInput.trim())
        }
    }

    val currentDefaultSim by viewModel.defaultSmsSim
    val activeSims by viewModel.activeSims.collectAsState()
    var selectedSimId by remember { mutableStateOf(if (currentDefaultSim == "SIM 2") 2 else 1) }
    // Keep the chosen slot valid for the SIMs actually present (avoids sending on a missing slot).
    LaunchedEffect(activeSims) {
        if (activeSims.isNotEmpty() && activeSims.none { it.slot == selectedSimId }) {
            selectedSimId = activeSims.first().slot
        }
    }

    // --- Scheduled delivery state ---
    val scheduledMessages by viewModel.scheduledMessages.collectAsState()
    // Two-step picker: date first, then time. Holds the chosen date (midnight, ms) between steps.
    var showScheduleDatePicker by remember { mutableStateOf(false) }
    var showScheduleTimePicker by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showScheduledListSheet by remember { mutableStateOf(false) }

    val inboxMessages by viewModel.inboxMessages.collectAsState()
    val suggestedSenders = remember(inboxMessages) {
        inboxMessages.map { it.sender }.distinct().filter { it.isNotBlank() }.take(5)
    }

    // Fold into an existing conversation the moment the recipient resolves to one (typed full
    // number or tapped suggestion). Exact-match only, so a partial prefix never jumps mid-typing.
    val allMessagesForMatch by viewModel.allMessages.collectAsState()
    LaunchedEffect(senderInput, allMessagesForMatch) {
        val match = viewModel.existingThreadFor(senderInput.trim()) ?: return@LaunchedEffect
        // Carry the typed body over as the thread's reply draft (don't clobber an existing draft
        // with a blank composer).
        val body = smsBodyInput.trim()
        if (body.isNotEmpty()) viewModel.saveDraft(match, body)
        handledExit = true
        viewModel.clearComposeDraft()
        viewModel.openThread(match)
        onDismiss()
    }

    // Launcher for selecting a contact
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri?.let { uri ->
            try {
                val contentResolver = context.contentResolver
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIndex = c.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                        val nameIndex = c.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                        val hasPhoneIndex = c.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)

                        val contactId = if (idIndex >= 0) c.getString(idIndex) else ""
                        val name = if (nameIndex >= 0) c.getString(nameIndex) else "Contact"
                        val hasPhone = if (hasPhoneIndex >= 0) c.getInt(hasPhoneIndex) > 0 else true

                        if (contactId.isNotEmpty() && hasPhone) {
                            val phoneCursor = contentResolver.query(
                                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )
                            phoneCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val numberIndex = pc.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numberIndex >= 0) {
                                        val number = pc.getString(numberIndex) ?: ""
                                        if (number.isNotEmpty()) {
                                            senderInput = number
                                            Toast.makeText(context, "Selected contact: $name ($number)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "No phone number found matching contact.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "No phone number available on picked contact.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load contact data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher for requesting contacts permission at runtime
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                contactPickerLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening phonebook: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Contacts permission was denied. Please input number manually or select from the suggestions list below.", Toast.LENGTH_LONG).show()
        }
    }

    // --- Scheduled delivery: date picker (step 1) ---
    if (showScheduleDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showScheduleDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMillis = dateState.selectedDateMillis
                    showScheduleDatePicker = false
                    if (pickedDateMillis != null) showScheduleTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    // --- Scheduled delivery: time picker (step 2) ---
    if (showScheduleTimePicker) {
        val nowCal = Calendar.getInstance()
        val timeState = rememberTimePickerState(
            initialHour = nowCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = nowCal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showScheduleTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val recipient = senderInput.trim()
                    val body = smsBodyInput.trim()
                    if (recipient.isEmpty() || body.isEmpty()) {
                        Toast.makeText(context, "Enter a recipient and message first.", Toast.LENGTH_SHORT).show()
                        showScheduleTimePicker = false
                        return@TextButton
                    }
                    // DatePicker reports UTC midnight; combine its Y/M/D with the picked local time.
                    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = pickedDateMillis ?: System.currentTimeMillis()
                    }
                    val triggerCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, utc.get(Calendar.YEAR))
                        set(Calendar.MONTH, utc.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val triggerAt = triggerCal.timeInMillis
                    showScheduleTimePicker = false
                    if (viewModel.scheduleSms(recipient, body, selectedSimId, triggerAt)) {
                        val when_ = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(triggerAt))
                        Toast.makeText(context, "Scheduled for $when_", Toast.LENGTH_SHORT).show()
                        handledExit = true
                        viewModel.clearComposeDraft()
                        onDismiss()
                    }
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timeState) }
        )
    }

    // --- Scheduled delivery: exact-alarm permission prompt ---
    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text("Enable exact alarms") },
            text = { Text("To schedule SMS delivery at a precise time, allow SMS Sentry to set exact alarms in system settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmDialog = false
                    try {
                        context.startActivity(viewModel.buildExactAlarmSettingsIntent())
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't open exact-alarm settings.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- Scheduled delivery: pending list + cancel ---
    if (showScheduledListSheet) {
        AlertDialog(
            onDismissRequest = { showScheduledListSheet = false },
            title = { Text("Scheduled messages") },
            confirmButton = {
                TextButton(onClick = { showScheduledListSheet = false }) { Text("Close") }
            },
            text = {
                if (scheduledMessages.isEmpty()) {
                    Text("No scheduled messages.")
                } else {
                    Column {
                        scheduledMessages.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.recipient, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(s.scheduledTime)),
                                        fontSize = 12.sp,
                                        color = Color(0xFF8C92AC)
                                    )
                                    Text(
                                        s.body,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color(0xFF8C92AC)
                                    )
                                }
                                IconButton(onClick = { viewModel.cancelScheduled(s.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel scheduled message"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        onDismiss()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F101A) // High-contrast sleek slate background matching the photo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            // 1. Header Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New message",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 2. Recipient Input Area ("To:")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "To:  ",
                        color = Color(0xFF9094A6),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = senderInput,
                            onValueChange = { senderInput = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            cursorBrush = SolidColor(Color(0xFF4C8DF6)),
                            decorationBox = { innerTextField ->
                                if (senderInput.isEmpty()) {
                                    Text(
                                        text = "Type by name, number etc",
                                        color = Color(0xFF535766),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("composer_sender_input")
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_CONTACTS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                try {
                                    contactPickerLauncher.launch(null)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error opening phonebook: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                permissionsLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("composer_contacts_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Contact Lookup Trigger",
                            tint = Color(0xFF4C8DF6),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = Color(0xFF232533),
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                // Sending requires being the default SMS app — tell the user when it's disabled.
                if (!viewModel.canSendSms) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A1F12))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFE0A900),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Set SMS Sentry as your default SMS app (Settings ▸ System integration) to send messages.",
                            color = Color(0xFFE0A900),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp
                        )
                    }
                }

                // 3. Middle Canvas area (Shows matching suggestions or empty active thread field)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF090A10))
                ) {
                    // Filter matching senders dynamically
                    val demoList = listOf("+91 98302 00100", "+1 (555) 019-2834", "+91 94470 12345", "SBI-ALRT", "HDFC-TXN", "IRCTC", "Google-OTP")
                    val allSenders = (suggestedSenders + demoList).distinct().take(5)
                    val filteredSenders = remember(senderInput, allSenders) {
                        val filtered = if (senderInput.isBlank()) {
                            allSenders
                        } else {
                            allSenders.filter { it.contains(senderInput, ignoreCase = true) }
                        }
                        filtered.take(5)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (filteredSenders.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Recent Senders & Suggested Contacts",
                                    color = Color(0xFF5B6275),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            items(filteredSenders) { sender ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            senderInput = sender
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val senderChar = sender.firstOrNull { it.isLetter() }?.uppercase() ?: "#"
                                    val avatarColor = remember(sender) {
                                        val colors = listOf(
                                            Color(0xFF6F53A4), Color(0xFF386B52), 
                                            Color(0xFF2B5EA0), Color(0xFFD32F2F), 
                                            Color(0xFF006874), Color(0xFFF57C00)
                                        )
                                        colors[sender.hashCode().coerceAtLeast(0) % colors.size]
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(avatarColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = senderChar,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = sender,
                                            color = Color(0xFFE2E4EB),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (sender.any { it.isLetter() }) "Business ID" else "Mobile Recipient",
                                            color = Color(0xFF5B6275),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Elegant Docked Message Composers Bar resembling high fidelity photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F101A))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E2130)) // High contrast beautiful slate text field capsule
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Dynamic Message editor box
                        BasicTextField(
                            value = smsBodyInput,
                            onValueChange = { smsBodyInput = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            cursorBrush = SolidColor(Color(0xFF4C8DF6)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp, max = 180.dp)
                                .testTag("composer_body_input"),
                            decorationBox = { innerTextField ->
                                if (smsBodyInput.isEmpty()) {
                                    Text(
                                        text = "Message...",
                                        color = Color(0xFF6C7185),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls Panel (Attachment, scheduling, sim slot configuration, send triggers)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Attachment Paperclip action button
                                IconButton(
                                    onClick = {
                                        Toast.makeText(context, "Attachment simulated: select image/doc", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Attachment,
                                        contentDescription = "Attach File",
                                        tint = Color(0xFF8C92AC),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Schedule Clock action button — opens a date+time picker, or
                                // prompts to enable exact alarms if the OS hasn't granted them.
                                IconButton(
                                    onClick = {
                                        if (viewModel.canScheduleExactAlarms()) {
                                            showScheduleDatePicker = true
                                        } else {
                                            showExactAlarmDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("composer_schedule_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Schedule SMS delivery",
                                        tint = Color(0xFF8C92AC),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Pending scheduled-messages badge (tap to view / cancel)
                                if (scheduledMessages.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF223152))
                                            .clickable { showScheduledListSheet = true }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Scheduled ${scheduledMessages.size}",
                                            color = Color(0xFF7FA8F5),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Always-on character count (adds segment info once the body splits).
                                Text(
                                    text = SmsSegment.composerLabel(smsBodyInput),
                                    color = Color(0xFF8C92AC),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                // SIM picker — only on multi-SIM devices; cycles through the active
                                // subscriptions so the send routes to the chosen slot.
                                if (activeSims.size > 1) {
                                    val selectedSim = activeSims.firstOrNull { it.slot == selectedSimId } ?: activeSims.first()
                                    Box(
                                        modifier = Modifier
                                            .border(
                                                width = 1.dp,
                                                color = Color(0xFF8C92AC),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                val idx = activeSims.indexOfFirst { it.slot == selectedSim.slot }
                                                val next = activeSims[(idx + 1) % activeSims.size]
                                                selectedSimId = next.slot
                                                Toast.makeText(context, "Sending via ${next.displayLabel}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "SIM ${selectedSim.slot}",
                                            color = Color(0xFF8C92AC),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Dynamic Color themed Send Button matching target reference.
                            // Sending requires being the default SMS app — otherwise it stays disabled.
                            val canSend = senderInput.trim().isNotEmpty() &&
                                smsBodyInput.trim().isNotEmpty() &&
                                viewModel.canSendSms
                            IconButton(
                                onClick = {
                                    if (canSend) {
                                        viewModel.sendSms(senderInput.trim(), smsBodyInput.trim(), selectedSimId)
                                        handledExit = true
                                        viewModel.clearComposeDraft()
                                        onDismiss()
                                    }
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (canSend) Color(0xFF4C8DF6) else Color(0xFF2C2F3D),
                                        shape = CircleShape
                                    )
                                    .testTag("composer_send_action_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send SMS",
                                    tint = if (canSend) Color.White else Color(0xFF565A6B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

// ==========================================
// SETTINGS SUB-PAGE: TESTING SANDBOX (SMS Sentry Sandbox simulator)
// ==========================================
@Composable
fun TestingSandboxPage(viewModel: SmsOrganizerViewModel, onBack: () -> Unit) {
    var fakeSender by remember { mutableStateOf("") }
    var fakeBody by remember { mutableStateOf("") }
    var selectedSimSlot by remember { mutableStateOf(1) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SubPageHeader("Testing", onBack) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "SMS Sentry Sandbox",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Simulates direct SMS reception. Excellent for seeing how the local ML engine evaluates and organizes lists in modern pipelines offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = fakeSender,
                        onValueChange = { fakeSender = it },
                        label = { Text("Incoming Number / ID Header") },
                        placeholder = { Text("e.g. HDFCBK or +919876") },
                        modifier = Modifier.fillMaxWidth().testTag("sandbox_sender_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = fakeBody,
                        onValueChange = { fakeBody = it },
                        label = { Text("SMS Text Body") },
                        placeholder = { Text("e.g., Dear customer, your A/c has been debited Rs. 500. Bal: Rs. 12000.") },
                        modifier = Modifier
                            .height(100.dp)
                            .fillMaxWidth()
                            .testTag("sandbox_body_input"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    )

                    // Quick pre-fill buttons to test AI categorization!
                    Text("Pretest Template Scenarios:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                fakeSender = "ICICIB"
                                fakeBody = "Your account has been credited for Rs. 15,200.00 via IMPS. Bal: Rs. 94,500.00"
                            },
                            label = { Text("Bank Trx", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                fakeSender = "SBIPAY"
                                fakeBody = "Outstanding Credit Card minimum payment Rs. 4,500.00 due date 30-05-2026. Please pay."
                            },
                            label = { Text("Due Bill", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                fakeSender = "PROMO_WIN"
                                fakeBody = "CLAIM FREE BONUS NOW! Click luckybox.io/promocode to unlock your $500 gift voucher right away!"
                            },
                            label = { Text("Spam Box", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                fakeSender = "+9198765432"
                                fakeBody = "Hey there! Long time no see. Let's arrange a telephone meetup next week!"
                            },
                            label = { Text("Friend Msg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                fakeSender = "കുരുവിള ജോ ചെറിയാൻ"
                                fakeBody = "Your otp is 12345"
                            },
                            label = { Text("Malayalam OTP (12345)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                fakeSender = "Google"
                                fakeBody = "G-984210 is your Google verification code."
                            },
                            label = { Text("Google OTP (984210)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Sim Selection bar
                    Text("Inbound Sim Slot Handler:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedSimSlot == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedSimSlot = 1
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SIM Slot 1", color = if (selectedSimSlot == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedSimSlot == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedSimSlot = 2
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SIM Slot 2", color = if (selectedSimSlot == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (fakeSender.trim().isEmpty() || fakeBody.trim().isEmpty()) {
                                Toast.makeText(context, "Enter a sender and message body first.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.simulateSmsReceived(fakeSender.trim(), fakeBody.trim(), selectedSimSlot)
                            Toast.makeText(context, "Simulated SMS delivered to the receiver.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sandbox_trigger_simulate_button")
                    ) {
                        Text("Trigger Simulated Receiver")
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE COMPONENT: IMPORT BACKUP DIALOG
// ==========================================
@Composable
fun ImportBackupDialog(viewModel: SmsOrganizerViewModel, onDismiss: () -> Unit) {
    var rawText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Import SMS Database",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Paste your exported CSV string or JSON payload to restore database nodes instantly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Raw CSV / JSON payload") },
                    placeholder = { Text("e.g. [{\"sender\":\"ICICIB\",\"body\":\"Test Msg\",\"simId\":2}]") },
                    modifier = Modifier
                        .height(150.dp)
                        .fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (rawText.trim().isNotEmpty()) {
                                viewModel.importJson(context, rawText.trim()) { count ->
                                    if (count > 0) {
                                        Toast.makeText(context, "Successfully imported $count messages!", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "JSON Parsing failed or empty payload.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import JSON")
                    }

                    OutlinedButton(
                        onClick = {
                            if (rawText.trim().isNotEmpty()) {
                                viewModel.importCsv(context, rawText.trim()) { count ->
                                    if (count > 0) {
                                        Toast.makeText(context, "Successfully imported $count messages!", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "CSV Parsing failed or empty columns.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import CSV")
                    }
                }
            }
        }
    }
}
