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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatTarget(val threadId: Long?, val address: String?, val name: String?) : Parcelable

class MainActivity : ComponentActivity() {

    private val vm: MessagesViewModel by viewModels()
    private var pendingAddress by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingAddress = addressFrom(intent)
        setContent {
            IMessageTheme {
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
            tween(if (chat != null) 380 else 320, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
        )
    }
    val chatVisible by remember { derivedStateOf { progress.value > 0.001f } }

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = -progress.value * size.width * 0.3f }) {
            ConversationsScreen(
                conversations = conversations,
                isDefault = isDefault,
                hasPermission = hasPermission,
                onOpen = { c -> chat = ChatTarget(c.threadId, c.address, c.name) },
                onCompose = { chat = ChatTarget(null, null, null) },
                onDelete = { vm.deleteThread(it.threadId) },
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
}
