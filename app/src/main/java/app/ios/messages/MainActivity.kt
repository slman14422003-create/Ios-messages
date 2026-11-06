package app.ios.messages

import app.ios.messages.ui.Motion
import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.ios.messages.ui.ChatScreen
import app.ios.messages.ui.ConversationsScreen
import app.ios.messages.ui.IMessageTheme
import app.ios.messages.ui.IosBlue
import app.ios.messages.ui.NewMessageScreen
import app.ios.messages.ui.SentBubble
import app.ios.messages.ui.SettingsScreen
import app.ios.messages.ui.SetupScreen
import app.ios.messages.ui.plainClickable
import app.ios.messages.ui.rtlSign
import app.ios.messages.data.Prefs
import app.ios.messages.security.Integrity
import app.ios.messages.security.applyScreenSecure
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatTarget(val threadId: Long?, val address: String?, val name: String?, val photo: String? = null) : Parcelable

class MainActivity : ComponentActivity() {

    private val vm: MessagesViewModel by viewModels()
    private var pendingAddress by mutableStateOf<String?>(null)
    private var appearance by mutableStateOf(0)
    private var textScale by mutableStateOf(1f)

    /** True when the integrity check failed: nothing from the message store is touched. */
    private var locked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots / screen recording / app-switcher preview, and ignore taps while another
        // app draws over us (tapjacking).
        applyScreenSecure(Prefs(this).screenSecure)
        window.decorView.filterTouchesWhenObscured = true
        val verdict = Integrity.verdict(this)
        if (verdict != Integrity.Verdict.OK) {
            locked = true
            enableEdgeToEdge()
            setContent { LockedScreen(onExit = { finishAffinity() }) }
            return
        }
        appearance = Prefs(this).appearance
        textScale = Prefs(this).textScale
        enableEdgeToEdge()
        val crashFile = java.io.File(filesDir, "crash.txt")
        if (crashFile.exists()) {
            val trace = runCatching { crashFile.readText() }.getOrDefault("")
            setContent {
                CrashScreen(
                    trace = trace,
                    onContinue = { crashFile.delete(); recreate() }
                )
            }
            return
        }
        if (savedInstanceState == null) pendingAddress = addressFrom(intent)
        setContent {
            val blue by vm.bubbleBlue.collectAsStateWithLifecycle()
            val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (appearance) { 1 -> false; 2 -> true; else -> sysDark }
            androidx.compose.runtime.DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { dark }
                )
                onDispose { }
            }
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    baseDensity.density, baseDensity.fontScale * textScale
                )
            ) {
            IMessageTheme(sent = if (blue) IosBlue else SentBubble, appearance = appearance) {
                App(
                    vm, pendingAddress,
                    onLanguage = { Prefs(this).language = it; recreate() },
                    appearance = appearance,
                    onAppearance = { appearance = it; Prefs(this).appearance = it },
                    textScale = textScale,
                    onTextScale = { textScale = it; Prefs(this).textScale = it }
                ) { pendingAddress = null }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        addressFrom(intent)?.let { pendingAddress = it }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        if (locked) return
        // A debugger / hook attached while the app was in the background (checked off the main thread:
        // it reads /proc/self/maps).
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            if (Integrity.verdict(this@MainActivity) != Integrity.Verdict.OK) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { recreate() }
            }
        }
        AppState.foreground = true
        requestHighRefresh()
        vm.recheckRole()
        vm.refresh(checkContacts = true)
    }

    /** Ask the display for its highest refresh rate at the current resolution (90 / 120 Hz panels). */
    private fun requestHighRefresh() {
        try {
            val d = display ?: return
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            if (lp.preferredDisplayModeId != best.modeId) {
                lp.preferredDisplayModeId = best.modeId
                lp.preferredRefreshRate = best.refreshRate
                window.attributes = lp
            }
        } catch (_: Throwable) {
        }
    }

    private var prefetch: kotlinx.coroutines.Job? = null
    private var downX = 0f
    private var downY = 0f

    /** While a finger rests on the screen for a moment, grab the backdrop early so a long-press menu opens with its blur ready. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (!locked) {
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    prefetch?.cancel()
                    app.ios.messages.ui.BackdropCache.clear()
                    prefetch = lifecycleScope.launch {
                        kotlinx.coroutines.delay(200)
                        app.ios.messages.ui.BackdropCache.put(app.ios.messages.ui.captureBackdrop(window.decorView))
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
                    if (Math.abs(ev.rawX - downX) > slop || Math.abs(ev.rawY - downY) > slop) {
                        prefetch?.cancel()
                        app.ios.messages.ui.BackdropCache.clear()
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    prefetch?.cancel()
                    app.ios.messages.ui.BackdropCache.clear()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        AppState.foreground = false
        super.onPause()
    }

    private fun addressFrom(i: Intent?): String? {
        val data = i?.data ?: return null
        if (data.scheme !in listOf("sms", "smsto", "mms", "mmsto")) return null
        return data.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun App(vm: MessagesViewModel, pendingAddress: String?, onLanguage: (String) -> Unit, appearance: Int, onAppearance: (Int) -> Unit, textScale: Float, onTextScale: (Float) -> Unit, onConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val language = remember { Prefs(ctx).language }
    val dir = rtlSign()
    var backupDialog by remember { mutableStateOf(false) }
    var backupPw by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pw = backupPw
        backupPw = null
        if (uri != null && pw != null) vm.exportBackup(uri, pw)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val isDefault by vm.isDefault.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val contactsVersion by vm.contactsVersion.collectAsStateWithLifecycle()
    val sims by vm.sims.collectAsStateWithLifecycle()
    val setupDismissed by vm.setupDismissed.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var keepDays by remember { mutableStateOf(Prefs(ctx).keepDays) }

    var chat by rememberSaveable { mutableStateOf<ChatTarget?>(null) }
    var lastChat by remember { mutableStateOf<ChatTarget?>(null) }
    var chatReady by remember { mutableStateOf(false) }
    val shown = chat ?: lastChat
    LaunchedEffect(chat) { if (chat != null) lastChat = chat }

    // iOS push/pop: the chat slides in, the list moves left at ~30% speed. Driven by graphicsLayer
    // so the animation never triggers recomposition.
    val progress = remember { Animatable(if (chat != null) 1f else 0f) }
    LaunchedEffect(chat != null) {
        if (chat != null) {
            if (chat?.address != null || chat?.threadId != null) {
                chatReady = false
                // Wait until the chat list has really been composed and laid out, so that work does not
                // land inside the slide-in animation.
                withTimeoutOrNull(320) { snapshotFlow { chatReady }.first { it } }
            }
            withFrameNanos { }
        }
        progress.animateTo(
            if (chat != null) 1f else 0f,
            Motion.pushF()
        )
        if (chat == null) vm.clearMessages() else {
            // Let the settled screen draw a few frames before the full reload starts.
            kotlinx.coroutines.delay(250)
            vm.refresh()
        }
    }
    val chatVisible by remember { derivedStateOf { progress.value > 0.001f } }

    val settingsProgress = remember { Animatable(if (settingsOpen) 1f else 0f) }
    LaunchedEffect(settingsOpen) {
        settingsProgress.animateTo(
            if (settingsOpen) 1f else 0f,
            Motion.pushF()
        )
    }
    val settingsVisible by remember { derivedStateOf { settingsProgress.value > 0.001f } }

    // Photos are cached by URI, so drop them whenever the address book changed.
    LaunchedEffect(contactsVersion) { if (contactsVersion > 0) app.ios.messages.ui.PhotoCache.invalidate() }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionsChanged() }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.recheckRole() }

    LaunchedEffect(isDefault, hasPermission) { if (isDefault && hasPermission) vm.dismissSetup() }
    LaunchedEffect(Unit) { permLauncher.launch(requiredPermissions()) }
    LaunchedEffect(pendingAddress) {
        pendingAddress?.let {
            chat = ChatTarget(null, it, vm.nameFor(it))
            onConsumed()
        }
    }

    // System back gesture drives the same interactive pop as the edge swipe (finger-following, cancellable).
    val backScope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = chat != null) { events ->
        try {
            events.collect { e -> progress.snapTo((1f - e.progress).coerceIn(0f, 1f)) }
            chat = null
            vm.closeChat()
        } catch (c: kotlinx.coroutines.CancellationException) {
            backScope.launch { progress.animateTo(1f, Motion.pushF()) }
        }
    }
    PredictiveBackHandler(enabled = settingsOpen) { events ->
        try {
            events.collect { e -> settingsProgress.snapTo((1f - e.progress).coerceIn(0f, 1f)) }
            settingsOpen = false
        } catch (c: kotlinx.coroutines.CancellationException) {
            backScope.launch { settingsProgress.animateTo(1f, Motion.pushF()) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        app.ios.messages.ui.BlurWarmUp()
        Box(Modifier.fillMaxSize().graphicsLayer {
            val pr = maxOf(progress.value, settingsProgress.value)
            translationX = -pr * size.width * 0.3f * dir
            // While sliding, keep the screen behind as one cached texture instead of re-drawing it every frame.
            // Stays one cached texture for the whole time a page is on top (no switch at the end of the slide).
            compositingStrategy = if (pr > 0.001f) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            else androidx.compose.ui.graphics.CompositingStrategy.Auto
        }) {
            ConversationsScreen(
                conversations = conversations,
                sims = sims,
                pinned = pinned,
                muted = muted,
                isDefault = isDefault,
                hasPermission = hasPermission,
                onOpen = { c -> chat = ChatTarget(if (c.threadId < 0) null else c.threadId, c.address, c.name) },
                onCompose = { chat = ChatTarget(null, null, null) },
                onSettings = { settingsOpen = true },
                onDelete = { vm.deleteThreads(it) },
                onMarkRead = { vm.markRead(it) },
                onMarkUnread = { vm.markUnread(it) },
                trash = trash,
                onRestoreTrash = { vm.restoreTrash(it) },
                onPurgeTrash = { vm.purgeTrash(it) },
                onTogglePin = { vm.togglePin(it) },
                onToggleMute = { vm.toggleMute(it) },
                onMakeDefault = {
                    val rm = ctx.getSystemService(RoleManager::class.java)
                    if (rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                    }
                },
                onGrant = { permLauncher.launch(requiredPermissions()) },
                onOpenSettings = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    )
                }
            )
        }

        if (chatVisible || chat != null) {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = progress.value * 0.25f) })
            shown?.let { t ->
                LaunchedEffect(t) {
                    if (t.address != null || t.threadId != null) vm.openChat(t.threadId, t.address)
                }
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationX = (1f - progress.value) * size.width * dir
                }) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-14).dp)
                            .fillMaxHeight()
                            .width(14.dp)
                            .background(Brush.horizontalGradient(if (dir > 0f) listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)) else listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent)))
                    )
                    if (t.address == null) {
                        NewMessageScreen(
                            contacts = contacts,
                            onCancel = {
                                chat = null
                                vm.closeChat()
                            },
                            onPick = { addr, name -> chat = ChatTarget(null, addr, name ?: vm.nameFor(addr)) }
                        )
                    } else {
                        // Name and photo follow the address book live: edit the contact anywhere and the header updates.
                        val liveTarget = remember(t, conversations, contactsVersion) {
                            val key = t.address?.let { Prefs.keyOf(it) }
                            val c = conversations.firstOrNull {
                                (t.threadId != null && it.threadId == t.threadId) || (key != null && Prefs.keyOf(it.address) == key)
                            }
                            t.copy(
                                name = c?.name ?: t.address?.let { vm.nameFor(it) } ?: t.name,
                                photo = c?.photo ?: t.address?.let { vm.photoFor(it) }
                            )
                        }
                        ChatScreen(
                            target = liveTarget,
                            messages = messages,
                            isDefault = isDefault,
                            sims = sims,
                            initialSim = vm.chooseSim(t.address.orEmpty()),
                            initialDraft = remember(t.address) { vm.draftFor(t.address.orEmpty()) },
                            onBack = {
                                chat = null
                                vm.closeChat()
                            },
                            onSend = { addr, body, att, sub ->
                                vm.send(addr, body, att, sub) { tid ->
                                    chat = ChatTarget(tid, addr, vm.nameFor(addr))
                                }
                            },
                            onDeleteMessage = vm::deleteMessage,
                            onPickSim = { sub -> vm.pickSim(t.address.orEmpty(), sub) },
                            onDraft = { d -> vm.saveDraft(t.address.orEmpty(), d) },
                            onRetry = { m, sub -> vm.retry(t.address.orEmpty(), m, sub) },
                            onReady = { chatReady = true }
                        )
                    }
                }
            }
        }

        if (settingsVisible || settingsOpen) {
            // Collected only while the settings screen exists, so toggling a preference no longer
            // recomposes the whole app root.
            val bubbleBlue by vm.bubbleBlue.collectAsStateWithLifecycle()
            val defaultSub by vm.defaultSub.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = settingsProgress.value * 0.25f) })
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - settingsProgress.value) * size.width * dir }
                    .plainClickable {}
            ) {
                SettingsScreen(
                    isDefault = isDefault,
                    hasPermission = hasPermission,
                    bubbleBlue = bubbleBlue,
                    onBack = { settingsOpen = false },
                    onMakeDefault = {
                        val rm = ctx.getSystemService(RoleManager::class.java)
                        if (rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                        }
                    },
                    onGrant = { permLauncher.launch(requiredPermissions()) },
                    onAppSettings = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                        )
                    },
                    onBubbleBlue = vm::setBubbleBlue,
                    onSystemNotifications = {
                        Notifier.ensureChannels(ctx)
                        runCatching { ctx.startActivity(Notifier.systemSettingsIntent(ctx)) }
                    },
                    sims = sims,
                    defaultSub = defaultSub,
                    onDefaultSub = vm::setDefaultSub,
                    onSaveSim = vm::saveSim,
                    language = language,
                    onLanguage = onLanguage,
                    appearance = appearance,
                    onAppearance = onAppearance,
                    textScale = textScale,
                    onTextScale = onTextScale,
                    keepDays = keepDays,
                    onKeepDays = { keepDays = it; Prefs(ctx).keepDays = it; vm.applyRetention() },
                    onBackup = { backupDialog = true },
                    onRestore = { openLauncher.launch(arrayOf("*/*")) }
                )
            }
            EdgeSwipeBack(
                progress = settingsProgress,
                enabled = settingsOpen,
                onCommit = { settingsOpen = false },
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        EdgeSwipeBack(
            progress = progress,
            enabled = chat != null,
            onCommit = {
                chat = null
                vm.closeChat()
            },
            modifier = Modifier.align(Alignment.CenterStart)
        )

        if (backupDialog) {
            app.ios.messages.ui.BackupPasswordDialog(
                confirm = true,
                onDismiss = { backupDialog = false },
                onOk = { pw ->
                    backupDialog = false
                    backupPw = pw
                    createLauncher.launch("messages-backup.imsgbak")
                }
            )
        }
        restoreUri?.let { u ->
            app.ios.messages.ui.BackupPasswordDialog(
                confirm = false,
                onDismiss = { restoreUri = null },
                onOk = { pw ->
                    restoreUri = null
                    vm.restoreBackup(u, pw)
                }
            )
        }

        if (!setupDismissed && (!hasPermission || !isDefault)) {
            SetupScreen(
                hasPermission = hasPermission,
                isDefault = isDefault,
                onGrant = { permLauncher.launch(requiredPermissions()) },
                onMakeDefault = {
                    val rm = ctx.getSystemService(RoleManager::class.java)
                    if (rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                    }
                },
                onSkip = { vm.dismissSetup() }
            )
        }
    }
}


@Composable
private fun EdgeSwipeBack(
    progress: Animatable<Float, AnimationVector1D>,
    enabled: Boolean,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val dir = rtlSign()
    val scope = rememberCoroutineScope()
    val commit by rememberUpdatedState(onCommit)
    val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    Box(
        modifier
            .fillMaxHeight()
            .width(12.dp)
            .pointerInput(Unit) {
                val settle = spring<Float>(dampingRatio = 1f, stiffness = 260f)
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (progress.value < 0.55f) commit()
                        else scope.launch { progress.animateTo(1f, settle) }
                    },
                    onDragCancel = { scope.launch { progress.animateTo(1f, settle) } },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        scope.launch { progress.snapTo((progress.value - dir * dx / widthPx).coerceIn(0f, 1f)) }
                    }
                )
            }
    )
}

@Composable
private fun CrashScreen(trace: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(16.dp)) {
            Text(androidx.compose.ui.res.stringResource(R.string.crash_title), fontSize = 20.sp, color = Color.Black)
            Text(androidx.compose.ui.res.stringResource(R.string.crash_hint), fontSize = 14.sp, color = Color.DarkGray)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                SelectionContainer { Text(trace, fontSize = 10.sp, color = Color.Black) }
            }
            Button(onClick = { clipboard.setText(AnnotatedString(trace)) }) { Text(androidx.compose.ui.res.stringResource(R.string.crash_copy)) }
            Button(onClick = onContinue) { Text(androidx.compose.ui.res.stringResource(R.string.crash_retry)) }
        }
    }
}


@Composable
private fun LockedScreen(onExit: () -> Unit) {
    MaterialTheme {
        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(28.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.locked_title), fontSize = 22.sp, color = Color.White)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.locked_body), fontSize = 15.sp, color = Color(0xFFAEAEB2))
            androidx.compose.foundation.layout.Spacer(Modifier.padding(14.dp))
            Button(onClick = onExit) { Text(androidx.compose.ui.res.stringResource(R.string.locked_exit)) }
        }
    }
}
