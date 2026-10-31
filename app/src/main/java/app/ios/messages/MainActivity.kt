package app.ios.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ios.messages.ui.ChatScreen
import app.ios.messages.ui.ConversationsScreen
import app.ios.messages.ui.IMessageTheme
import app.ios.messages.ui.IosBlue
import app.ios.messages.ui.NewMessageScreen
import app.ios.messages.ui.SentBubble
import app.ios.messages.ui.SettingsScreen
import app.ios.messages.ui.plainClickable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatTarget(val threadId: Long?, val address: String?, val name: String?) : Parcelable

class MainActivity : ComponentActivity() {

    private val vm: MessagesViewModel by viewModels()
    private var pendingAddress by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            IMessageTheme(sent = if (blue) IosBlue else SentBubble) {
                App(vm, pendingAddress) { pendingAddress = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        addressFrom(intent)?.let { pendingAddress = it }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
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
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun App(vm: MessagesViewModel, pendingAddress: String?, onConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val isDefault by vm.isDefault.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val bubbleBlue by vm.bubbleBlue.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val previews by vm.previews.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    var chat by rememberSaveable { mutableStateOf<ChatTarget?>(null) }
    var lastChat by remember { mutableStateOf<ChatTarget?>(null) }
    val shown = chat ?: lastChat
    LaunchedEffect(chat) { if (chat != null) lastChat = chat }

    // iOS push/pop: the chat slides in, the list moves left at ~30% speed. Driven by graphicsLayer
    // so the animation never triggers recomposition.
    val progress = remember { Animatable(if (chat != null) 1f else 0f) }
    LaunchedEffect(chat != null) {
        progress.animateTo(
            if (chat != null) 1f else 0f,
            spring(dampingRatio = 1f, stiffness = 260f)
        )
        if (chat == null) vm.clearMessages()
    }
    val chatVisible by remember { derivedStateOf { progress.value > 0.001f } }

    val settingsProgress = remember { Animatable(if (settingsOpen) 1f else 0f) }
    LaunchedEffect(settingsOpen) {
        settingsProgress.animateTo(
            if (settingsOpen) 1f else 0f,
            spring(dampingRatio = 1f, stiffness = 260f)
        )
    }
    val settingsVisible by remember { derivedStateOf { settingsProgress.value > 0.001f } }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionsChanged() }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refresh() }

    LaunchedEffect(Unit) { permLauncher.launch(requiredPermissions()) }
    LaunchedEffect(pendingAddress) {
        pendingAddress?.let {
            chat = ChatTarget(null, it, vm.nameFor(it))
            onConsumed()
        }
    }

    BackHandler(enabled = chat != null) {
        chat = null
        vm.closeChat()
    }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = -maxOf(progress.value, settingsProgress.value) * size.width * 0.3f }) {
            ConversationsScreen(
                conversations = conversations,
                pinned = pinned,
                muted = muted,
                isDefault = isDefault,
                hasPermission = hasPermission,
                onOpen = { c -> chat = ChatTarget(c.threadId, c.address, c.name) },
                onCompose = { chat = ChatTarget(null, null, null) },
                onSettings = { settingsOpen = true },
                onDelete = { vm.deleteThreads(it) },
                onMarkRead = { vm.markRead(it) },
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
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value * 0.25f }.background(Color.Black))
            shown?.let { t ->
                LaunchedEffect(t) {
                    if (t.address != null || t.threadId != null) vm.openChat(t.threadId, t.address)
                }
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = (1f - progress.value) * size.width }) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-14).dp)
                            .fillMaxHeight()
                            .width(14.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f))))
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
                        ChatScreen(
                            target = t,
                            messages = messages,
                            isDefault = isDefault,
                            onBack = {
                                chat = null
                                vm.closeChat()
                            },
                            onSend = { addr, body ->
                                vm.send(addr, body) { tid ->
                                    chat = ChatTarget(tid, addr, vm.nameFor(addr))
                                }
                            },
                            onDeleteMessage = vm::deleteMessage
                        )
                    }
                }
            }
        }

        if (settingsVisible || settingsOpen) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = settingsProgress.value * 0.25f }.background(Color.Black))
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - settingsProgress.value) * size.width }
                    .plainClickable {}
            ) {
                SettingsScreen(
                    isDefault = isDefault,
                    hasPermission = hasPermission,
                    bubbleBlue = bubbleBlue,
                    notifications = notifications,
                    previews = previews,
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
                    onNotifications = vm::setNotifications,
                    onPreviews = vm::setPreviews,
                    onSystemNotifications = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
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
                        scope.launch { progress.snapTo((progress.value - dx / widthPx).coerceIn(0f, 1f)) }
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
            Text("The app crashed last time", fontSize = 20.sp, color = Color.Black)
            Text("Copy this text and send it to Claude:", fontSize = 14.sp, color = Color.DarkGray)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                SelectionContainer { Text(trace, fontSize = 10.sp, color = Color.Black) }
            }
            Button(onClick = { clipboard.setText(AnnotatedString(trace)) }) { Text("Copy") }
            Button(onClick = onContinue) { Text("Try opening the app") }
        }
    }
}
