package app.ios.messages.ui

import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import app.ios.messages.R

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.ChatTarget
import app.ios.messages.data.Message
import app.ios.messages.data.Otp
import app.ios.messages.data.SimInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

private sealed interface ChatItem {
    val key: String

    data class Time(val day: String, val time: String, val id: Long) : ChatItem {
        override val key: String get() = "t$id"
    }

    data class Bubble(val m: Message, val tail: Boolean, val tight: Boolean) : ChatItem {
        override val key: String get() = "m${m.id}"
    }

    data class Status(val text: String, val failed: Boolean = false, val msg: Message? = null) : ChatItem {
        override val key: String get() = "status"
    }
}

private const val TIME_GAP = 60L * 60_000L

/** Like iOS: consecutive messages from the same sender form a group; only the last one gets a tail. */
private fun buildItems(ctx: android.content.Context, msgs: List<Message>): List<ChatItem> {
    val out = ArrayList<ChatItem>(msgs.size + 8)
    var prev: Message? = null
    msgs.forEachIndexed { i, m ->
        val p = prev
        val header = p == null || m.date - p.date > TIME_GAP
        if (header) {
            val (d, t) = DateFmt.chat(ctx, m.date)
            out += ChatItem.Time(d, t, m.id)
        }
        val next = msgs.getOrNull(i + 1)
        val tight = p != null && !header && p.mine == m.mine
        val tail = next == null || next.mine != m.mine || next.date - m.date > TIME_GAP
        out += ChatItem.Bubble(m, tail, tight)
        prev = m
    }
    val last = msgs.lastOrNull()
    if (last != null && last.mine) {
        out += ChatItem.Status(
            when {
                last.failed -> ctx.getString(R.string.not_delivered)
                last.sending -> ctx.getString(R.string.sending)
                else -> ctx.getString(R.string.sent_as_text)
            },
            last.failed, last
        )
    }
    return out
}

@Composable
fun ChatScreen(
    target: ChatTarget,
    messages: List<Message>,
    isDefault: Boolean,
    sims: List<SimInfo>,
    initialSim: Int,
    initialDraft: String,
    onBack: () -> Unit,
    onSend: (address: String, body: String, attachment: String?, subId: Int) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onPickSim: (Int) -> Unit,
    onDraft: (String) -> Unit,
    onRetry: (Message, Int) -> Unit
) {
    CompositionLocalProvider(LocalSims provides sims) {
        ChatScreenBody(
            target, messages, isDefault, sims, initialSim, initialDraft,
            onBack, onSend, onDeleteMessage, onPickSim, onDraft, onRetry
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatScreenBody(
    target: ChatTarget,
    messages: List<Message>,
    isDefault: Boolean,
    sims: List<SimInfo>,
    initialSim: Int,
    initialDraft: String,
    onBack: () -> Unit,
    onSend: (address: String, body: String, attachment: String?, subId: Int) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onPickSim: (Int) -> Unit,
    onDraft: (String) -> Unit,
    onRetry: (Message, Int) -> Unit
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val address = target.address.orEmpty()
    var menu by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchPos by remember { mutableStateOf(0) }
    var simMenu by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = emojiOpen) { emojiOpen = false }
    val name = target.name ?: target.address ?: ""
    var text by rememberSaveable(address) { mutableStateOf(initialDraft) }
    var picked by remember(address) { mutableStateOf<Int?>(null) }
    val simSel = picked?.takeIf { s -> sims.any { it.subId == s } } ?: initialSim
    var warned by rememberSaveable(address) { mutableStateOf(false) }
    var wrongSim by remember { mutableStateOf(false) }
    val latestText by rememberUpdatedState(text)
    val saveDraft by rememberUpdatedState(onDraft)
    LaunchedEffect(text) {
        delay(600)
        if (address.isNotEmpty()) saveDraft(text)
    }
    DisposableEffect(address) {
        onDispose { if (address.isNotEmpty()) saveDraft(latestText) }
    }
    var attach by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attach = uri.toString()
    }
    val itemsCtx = androidx.compose.ui.platform.LocalContext.current
    val kbc = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var camUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) attach = camUri
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val line = readContactLine(itemsCtx, uri)
            if (line != null) text = if (text.isBlank()) line else text + "\n" + line
        }
    }
    fun addLocation() {
        val link = lastLocationLink(itemsCtx)
        if (link == null) {
            android.widget.Toast.makeText(itemsCtx, itemsCtx.getString(R.string.location_unavailable), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            text = if (text.isBlank()) link else text + "\n" + link
        }
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) addLocation()
    }
    // Grouping, date headers and tail flags are computed off the main thread so opening a long
    // chat does not stall the slide-in animation.
    val rev by androidx.compose.runtime.produceState<List<ChatItem>>(emptyList(), messages) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            buildItems(itemsCtx, messages).asReversed()
        }
    }
    val listState = rememberLazyListState()
    val liveTop = remember { LiveBlur() }
    val matches = remember(rev, searchQuery, searchOpen) {
        if (!searchOpen || searchQuery.isBlank()) emptyList()
        else rev.mapIndexedNotNull { i, it -> if (it is ChatItem.Bubble && it.m.body.contains(searchQuery, true)) i else null }
    }
    val curMatch = matches.getOrNull(searchPos.coerceIn(0, maxOf(0, matches.size - 1)))
    val curMatchKey = curMatch?.let { rev.getOrNull(it)?.key }
    LaunchedEffect(matches, searchPos) {
        curMatch?.let { runCatching { listState.animateScrollToItem(it) } }
    }
    var headerH by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val blurPx = with(LocalDensity.current) { 32.dp.toPx() }
    val fadeTail = with(LocalDensity.current) { 24.dp.toPx() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var inputH by remember { mutableIntStateOf(with(density) { (66.dp + navBottom).roundToPx() }) }
    val statusH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barH = 128.dp
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val rdir = rtlSign()
    val reveal = remember { Animatable(0f) }
    val revealPx = with(density) { 64.dp.toPx() }

    val newestKey = rev.firstOrNull()?.key
    var listReady by remember { mutableStateOf(false) }
    LaunchedEffect(newestKey) {
        if (rev.isNotEmpty()) {
            if (listReady) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        }
    }
    LaunchedEffect(rev.isNotEmpty()) { if (rev.isNotEmpty()) listReady = true }
    val listAlpha by animateFloatAsState(
        if (rev.isEmpty()) 0f else 1f,
        Motion.fade(240),
        label = "list"
    )

    Box(Modifier.fillMaxSize().background(p.bg).imePadding()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = listAlpha }
                .liveBlurEdges(
                    liveTop,
                    { headerH + fadeTail }, { 1f },
                    { inputH + fadeTail }, { 1f },
                    p.bg, p.bg.copy(alpha = 0.62f), blurPx, fade = true
                )
                .pointerInput(Unit) {
                    val back = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
                    detectHorizontalDragGestures(
                        onDragEnd = { scope.launch { reveal.animateTo(0f, back) } },
                        onDragCancel = { scope.launch { reveal.animateTo(0f, back) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { reveal.snapTo((reveal.value - rdir * dx / revealPx).coerceIn(0f, 1f)) }
                        }
                    )
                },
            contentPadding = PaddingValues(
                top = statusH + barH + 6.dp,
                bottom = with(density) { inputH.toDp() } + 6.dp
            )
        ) {
            items(rev.size, key = { rev.getOrNull(it)?.key ?: "i$it" }, contentType = { rev.getOrNull(it)?.let { x -> x::class } }) { idx ->
                val item = rev.getOrNull(idx) ?: return@items
                when (item) {
                    is ChatItem.Time -> TimeHeader(item)
                    is ChatItem.Bubble -> BubbleRow(
                        item,
                        if (!searchOpen || searchQuery.isBlank()) 0
                        else if (curMatchKey != null && item.key == curMatchKey) 2
                        else if (item.m.body.contains(searchQuery, true)) 1 else 0,
                        isDefault,
                        onDeleteMessage,
                        { reveal.value },
                        { viewing = it },
                        if (listReady && idx < 6) Modifier.animateItemPlacement(Motion.layout()) else Modifier
                    )
                    is ChatItem.Status -> Column(
                        Modifier.fillMaxWidth().padding(top = 3.dp, end = 16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            item.text,
                            color = if (item.failed) Red else p.secondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End
                        )
                        val fm = item.msg
                        if (item.failed && fm != null && fm.images.isEmpty()) {
                            val other = if (sims.size > 1) sims.firstOrNull { it.subId != fm.subId } else null
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (other != null) {
                                    Text(
                                        stringResource(R.string.send_via, other.label),
                                        color = p.blue,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.plainClickable {
                                            picked = other.subId
                                            onPickSim(other.subId)
                                            onRetry(fm, other.subId)
                                        }
                                    )
                                }
                                Text(
                                    stringResource(R.string.try_again),
                                    color = p.blue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.plainClickable { onRetry(fm, fm.subId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Header: soft fade instead of a hard bar, so message text never shows through awkwardly.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerH = it.height.toFloat() }
                .statusBarsPadding()
        ) {
            run {
                Box(Modifier.fillMaxWidth().height(barH)) {
                    GlassCircle(
                        44.dp,
                        Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp),
                        onClick = onBack
                    ) { ChevronLeft(p.blue, Modifier.padding(end = 2.dp)) }
                    if (menu) ContactSheet(name, address, { searchOpen = true; searchQuery = ""; searchPos = 0 }) { menu = false }
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .plainClickable { menu = true }
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Avatar(name, 58.dp)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                color = p.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            ChevronRight(p.secondary, width = 6, height = 11, stroke = 1.6f)
                        }
                    }
                    val headerSim = sims.firstOrNull { it.subId == simSel }
                    if (headerSim != null) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .plainClickable { if (sims.size > 1) simMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dotC by animateColorAsState(Color(headerSim.color), label = "dot")
                                Box(Modifier.size(6.dp).clip(CircleShape).background(dotC))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    headerSim.label,
                                    color = p.secondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp)
                                )
                                if (sims.size > 1) {
                                    Spacer(Modifier.width(3.dp))
                                    ChevronRight(p.secondary, width = 5, height = 8, stroke = 1.2f, modifier = Modifier.graphicsLayer { rotationZ = 90f })
                                }
                            }
                            IosMenu(expanded = simMenu, onDismissRequest = { simMenu = false }) {
                                sims.forEach { sm ->
                                    IosMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                SimBadge(sm.label, 22.dp)
                                                Spacer(Modifier.width(12.dp))
                                                Text(sm.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        },
                                        onClick = { simMenu = false; picked = sm.subId; onPickSim(sm.subId) },
                                        trailingIcon = { if (sm.subId == simSel) CheckMark(p.label, Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        viewing?.let { ImageViewer(it) { viewing = null } }

        val doSend = { sub: Int ->
            val body = text.trim()
            if (body.isNotEmpty() || attach != null) {
                tick()
                onSend(address, body, attach, sub)
                text = ""
                attach = null
                onDraft("")
            }
        }
        val lastInSub = messages.lastOrNull { !it.mine && it.subId >= 0 }?.subId
        if (wrongSim) {
            val a = sims.firstOrNull { it.subId == lastInSub }
            val b = sims.firstOrNull { it.subId == simSel }
            if (a != null && b != null) {
                IosAlert(
                    title = stringResource(R.string.different_sim),
                    message = stringResource(R.string.different_sim_msg, a.label, b.label),
                    actions = listOf(
                        AlertAction(stringResource(R.string.use_sim, a.label), bold = true, onClick = {
                            wrongSim = false
                            picked = a.subId
                            onPickSim(a.subId)
                            doSend(a.subId)
                        }),
                        AlertAction(stringResource(R.string.send_from, b.label), onClick = {
                            wrongSim = false
                            warned = true
                            doSend(simSel)
                        })
                    ),
                    onDismiss = { wrongSim = false }
                )
            } else wrongSim = false
        }

        val showDown by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 4 } }
        val downScope = androidx.compose.runtime.rememberCoroutineScope()
        val downDensity = LocalDensity.current
        androidx.compose.animation.AnimatedVisibility(
            visible = showDown,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = with(downDensity) { inputH.toDp() } + 10.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.6f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f)
        ) {
            GlassCircle(40.dp, Modifier, onClick = { downScope.launch { listState.animateScrollToItem(0) } }) {
                ChevronLeft(p.blue, Modifier.graphicsLayer { rotationZ = -90f })
            }
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            if (searchOpen) ChatSearchBar(
                query = searchQuery,
                onQuery = { searchQuery = it; searchPos = 0 },
                count = matches.size,
                pos = searchPos.coerceIn(0, maxOf(0, matches.size - 1)),
                onPrev = { if (matches.isNotEmpty()) searchPos = (searchPos + 1) % matches.size },
                onNext = { if (matches.isNotEmpty()) searchPos = (searchPos - 1 + matches.size) % matches.size },
                onClose = { searchOpen = false; searchQuery = "" },
                onHeight = { inputH = it }
            ) else InputBar(
                text = text,
                attach = attach,
                sims = sims,
                simSel = simSel,
                onSim = { picked = it; onPickSim(it) },
                onChange = { text = it },
                onHeight = { inputH = it },
                emojiOpen = emojiOpen,
                onEmojiToggle = { emojiOpen = it },
                onEmoji = { text += it },
                onBackspace = { text = dropLastGrapheme(text) },
                onCamera = {
                    runCatching {
                        val dir = java.io.File(itemsCtx.cacheDir, "mms").apply { mkdirs() }
                        val f = java.io.File(dir, "cam_${System.currentTimeMillis()}.jpg")
                        val u = androidx.core.content.FileProvider.getUriForFile(itemsCtx, "${itemsCtx.packageName}.fileprovider", f)
                        camUri = u.toString()
                        camera.launch(u)
                    }
                },
                onPhotos = { picker.launch("image/*") },
                onStickers = { kbc?.hide(); emojiOpen = true },
                onContact = { runCatching { pickContact.launch(null) } },
                onLocation = {
                    val granted = itemsCtx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) addLocation()
                    else locPerm.launch(arrayOf(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ))
                },
                onClearAttach = { attach = null },
                onSend = {
                    if (address.isNotEmpty() && (text.isNotBlank() || attach != null)) {
                        if (sims.size > 1 && lastInSub != null && lastInSub != simSel && !warned &&
                            sims.any { it.subId == lastInSub }
                        ) {
                            wrongSim = true
                        } else {
                            doSend(simSel)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TimeHeader(t: ChatItem.Time) {
    val p = LocalPalette.current
    val label = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(t.day) }
        append(" ")
        append(t.time)
    }
    Text(
        label,
        color = p.secondary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun BubbleContent(
    b: ChatItem.Bubble,
    maxW: Dp,
    interactive: Boolean,
    onLongPress: () -> Unit,
    onImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val sent = LocalSentColor.current
    val mine = b.m.mine
    val ctx = LocalContext.current
    val shape = remember(mine, b.tail) { BubbleShape(mine, b.tail) }
    val linkColor = if (mine) Color.White else p.blue
    val annotated = remember(b.m.body, linkColor) { linkify(b.m.body, linkColor) }
    val layoutRef = remember { arrayOfNulls<TextLayoutResult>(1) }
    val padStart = if (mine) 12.dp else 18.dp
    val padEnd = if (mine) 18.dp else 12.dp
    val hasText = b.m.body.isNotBlank()
    val otp = remember(b.m.body, mine) { if (mine) null else Otp.extract(b.m.body) }
    val sims = LocalSims.current
    Column(modifier, horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        b.m.images.forEachIndexed { i, uri ->
            if (i > 0) Spacer(Modifier.height(2.dp))
            MmsImage(
                uri,
                maxW,
                if (interactive) Modifier.pointerInput(uri) {
                    detectTapGestures(onLongPress = { onLongPress() }, onTap = { onImage(uri) })
                } else Modifier
            )
        }
        val jumbo = remember(b.m.body) { emojiOnlyCount(b.m.body) }
        if (hasText && jumbo > 0) {
            if (b.m.images.isNotEmpty()) Spacer(Modifier.height(2.dp))
            Text(
                text = b.m.body.trim(),
                fontSize = when (jumbo) { 1 -> 64.sp; 2 -> 52.sp; else -> 44.sp },
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .then(if (interactive) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) } else Modifier)
            )
        } else if (hasText) {
            if (b.m.images.isNotEmpty()) Spacer(Modifier.height(2.dp))
            Text(
                text = annotated,
                onTextLayout = { layoutRef[0] = it },
                color = if (mine) Color.White else p.receivedText,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .widthIn(max = maxW)
                    .background(if (mine) sent else p.received, shape)
                    .then(
                        if (interactive) Modifier.pointerInput(annotated) {
                            val ox = padStart.toPx()
                            val oy = 7.dp.toPx()
                            detectTapGestures(
                                onLongPress = { onLongPress() },
                                onTap = { pos ->
                                    val l = layoutRef[0] ?: return@detectTapGestures
                                    val off = l.getOffsetForPosition(Offset(pos.x - ox, pos.y - oy))
                                    val hit = annotated.getStringAnnotations(off, off).firstOrNull()
                                        ?: return@detectTapGestures
                                    runCatching {
                                        if (hit.tag == TAG_URL) {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.item)))
                                        } else {
                                            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(hit.item))))
                                        }
                                    }
                                }
                            )
                        } else Modifier
                    )
                    .padding(start = padStart, end = padEnd, top = 7.dp, bottom = 7.dp)
            )
        }
        if (otp != null && interactive) OtpChip(otp)
        if (b.tail && sims.size > 1 && b.m.subId >= 0) {
            sims.firstOrNull { it.subId == b.m.subId }?.let { SimTag(it) }
        }
    }
}

@Composable
private fun BubbleRow(
    b: ChatItem.Bubble,
    hl: Int,
    isDefault: Boolean,
    onDelete: (Long) -> Unit,
    reveal: () -> Float,
    onImage: (String) -> Unit,
    modifier: Modifier
) {
    val p = LocalPalette.current
    val mine = b.m.mine
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val thud = rememberThud()
    var menu by remember { mutableStateOf(false) }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.76f).dp
    val boundsRef = remember { arrayOfNulls<Rect>(1) }
    val coordsRef = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val timeLabel = remember(b.m.date) { DateFmt.time(b.m.date) }
    val isNew = remember { System.currentTimeMillis() - b.m.date < 4000L }
    val appear = remember { Animatable(if (isNew) 0f else 1f) }
    if (isNew) LaunchedEffect(Unit) {
        if (appear.value < 1f) appear.animateTo(1f, Motion.bubble())
    }
    val bdir = rtlSign()
    val shiftPx = with(LocalDensity.current) { 56.dp.toPx() }

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = -reveal() * shiftPx * bdir }
                .padding(top = if (b.tight) 2.dp else 9.dp, start = 8.dp, end = 8.dp),
            contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        val v = appear.value
                        val s = 0.72f + 0.28f * v
                        scaleX = s
                        scaleY = s
                        alpha = v.coerceIn(0f, 1f)
                        translationY = (1f - v) * 22.dp.toPx()
                        transformOrigin = TransformOrigin(if (mine == (bdir > 0f)) 1f else 0f, 1f)
                    }
                    .onGloballyPositioned { coordsRef[0] = it }
                    .then(
                        if (hl > 0) Modifier.border(
                            if (hl == 2) 2.5.dp else 1.5.dp,
                            if (hl == 2) Color(0xFFFFCC00) else Color(0x80FFCC00),
                            RoundedCornerShape(20.dp)
                        ) else Modifier
                    )
            ) {
                BubbleContent(
                    b = b,
                    maxW = maxW,
                    interactive = true,
                    onLongPress = {
                        thud()
                        coordsRef[0]?.takeIf { it.isAttached }?.let { boundsRef[0] = it.boundsInWindow() }
                        menu = true
                    },
                    onImage = onImage
                )
            }
        }
        Text(
            timeLabel,
            color = p.secondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .graphicsLayer { alpha = reveal() }
        )
    }

    val r = boundsRef[0]
    if (menu && r != null) {
        ContextOverlay(
            bounds = r,
            menuAtEnd = mine,
            previewWidth = null,
            onDismiss = { menu = false },
            preview = { BubbleContent(b, maxW, false, {}, {}) }
        ) {
            IosMenuItem(
                text = { Text(stringResource(R.string.copy)) },
                onClick = {
                    menu = false
                    clipboard.setText(AnnotatedString(b.m.body))
                }
            )
            IosMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = {
                    menu = false
                    runCatching {
                        ctx.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, b.m.body)
                                },
                                null
                            )
                        )
                    }
                }
            )
            if (isDefault) {
                IosMenuItem(
                    text = { Text(stringResource(R.string.delete), color = Red) },
                    onClick = {
                        menu = false
                        onDelete(b.m.id)
                    }
                )
            }
        }
    } else if (menu) {
        menu = false
    }
}

@Composable
private fun ImageViewer(uri: String, onDismiss: () -> Unit) {
    val bmp = rememberMmsImage(uri, 2048)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).plainClickable(onDismiss),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    attach: String?,
    sims: List<SimInfo>,
    simSel: Int,
    onSim: (Int) -> Unit,
    onChange: (String) -> Unit,
    onHeight: (Int) -> Unit,
    emojiOpen: Boolean,
    onEmojiToggle: (Boolean) -> Unit,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onStickers: () -> Unit,
    onContact: () -> Unit,
    onLocation: () -> Unit,
    onClearAttach: () -> Unit,
    onSend: () -> Unit
) {
    val p = LocalPalette.current
    val sent = LocalSentColor.current
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val fr = remember { FocusRequester() }
    val pill = RoundedCornerShape(22.dp)
    val canSend = text.isNotBlank() || attach != null
    var plusOpen by remember { mutableStateOf(false) }
    val plusRot by androidx.compose.animation.core.animateFloatAsState(
        if (plusOpen) 45f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "plusRot"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(
            visible = attach != null,
            enter = fadeIn(Motion.fade(160)) + scaleIn(initialScale = 0.8f, animationSpec = Motion.pop()),
            exit = fadeOut(Motion.fade(120)) + scaleOut(Motion.fade(140), targetScale = 0.8f)
        ) {
            val bmp = if (attach != null) rememberMmsImage(attach, 400) else null
            Box(Modifier.padding(start = 66.dp, top = 14.dp, bottom = 2.dp)) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.received)
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .plainClickable(onClearAttach),
                    contentAlignment = Alignment.Center
                ) { Text("\u00D7", color = Color.White, fontSize = 15.sp) }
            }
        }
        if (text.length >= 100) {
            val uni = text.any { it.code > 127 }
            val single = if (uni) 70 else 160
            val multi = if (uni) 67 else 153
            val seg = if (text.length <= single) 1 else (text.length + multi - 1) / multi
            Text(
                "${text.length} · $seg",
                color = p.secondary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).padding(end = 22.dp, top = 4.dp)
            )
        }
        Row(
            Modifier.padding(
                start = 12.dp, end = 12.dp,
                top = if (attach != null) 6.dp else 14.dp, bottom = 8.dp
            ),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(36.dp)
                    .shadow(8.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                    .clip(CircleShape)
                    .background(p.pressed)
                    .plainClickable { plusOpen = !plusOpen },
                contentAlignment = Alignment.Center
            ) {
                PlusIcon(p.secondary, Modifier.graphicsLayer { rotationZ = plusRot })
                IosMenu(expanded = plusOpen, onDismissRequest = { plusOpen = false }, originX = 0.1f) {
                    IosMenuRow(checkColumn = false, icon = { CameraIcon(p.label) }, onClick = { plusOpen = false; onCamera() }) {
                        Text(stringResource(R.string.attach_camera))
                    }
                    IosMenuRow(checkColumn = false, icon = { PhotoIcon(p.label) }, onClick = { plusOpen = false; onPhotos() }) {
                        Text(stringResource(R.string.attach_photos))
                    }
                    IosMenuRow(checkColumn = false, icon = { EmojiIcon(p.label) }, onClick = { plusOpen = false; onStickers() }) {
                        Text(stringResource(R.string.attach_stickers))
                    }
                    IosMenuRow(checkColumn = false, icon = { ContactIcon(p.label) }, onClick = { plusOpen = false; onContact() }) {
                        Text(stringResource(R.string.attach_contact))
                    }
                    IosMenuRow(checkColumn = false, icon = { LocationIcon(p.label) }, onClick = { plusOpen = false; onLocation() }) {
                        Text(stringResource(R.string.attach_location))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .shadow(10.dp, pill, clip = false, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                    .clip(pill)
                    .animateContentSize(Motion.layout())
                    .background(p.cell)
                    .border(Dp.Hairline, p.outline, pill)
            ) {
                BasicTextField(
                    modifier = Modifier
                        .focusRequester(fr)
                        .onFocusChanged { if (it.isFocused && emojiOpen) onEmojiToggle(false) },
                    value = text,
                    onValueChange = onChange,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = TextStyle(fontSize = 17.sp, color = p.label, lineHeight = 22.sp),
                    cursorBrush = SolidColor(p.blue),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, end = 6.dp, top = 11.dp, bottom = 11.dp)
                            ) {
                                if (text.isEmpty()) {
                                    Text(if (attach != null) stringResource(R.string.add_caption) else stringResource(R.string.text_message), color = p.secondary, fontSize = 17.sp)
                                }
                                inner()
                            }
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + scaleIn(initialScale = 0.5f, animationSpec = Motion.pop()),
                                exit = fadeOut(Motion.fade(120)) + scaleOut(Motion.fade(140), targetScale = 0.6f)
                            ) {
                                Box(
                                    Modifier
                                        .padding(end = 4.dp, bottom = 0.dp)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .plainClickable {
                                            if (emojiOpen) {
                                                onEmojiToggle(false)
                                                fr.requestFocus()
                                                kb?.show()
                                            } else {
                                                focus.clearFocus()
                                                kb?.hide()
                                                onEmojiToggle(true)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) { EmojiIcon(if (emojiOpen) p.blue else p.secondary) }
                            }
                            AnimatedVisibility(
                                visible = canSend,
                                enter = fadeIn() + scaleIn(initialScale = 0.5f, animationSpec = Motion.pop()),
                                exit = fadeOut(Motion.fade(120)) + scaleOut(Motion.fade(140), targetScale = 0.5f)
                            ) {
                                val src = remember { MutableInteractionSource() }
                                Box(
                                    Modifier
                                        .padding(end = 5.dp, bottom = 5.dp)
                                        .size(34.dp)
                                        .pressScale(src, 0.84f)
                                        .clip(CircleShape)
                                        .background(sent)
                                        .clickable(interactionSource = src, indication = null, onClick = onSend),
                                    contentAlignment = Alignment.Center
                                ) { SendArrow(Color.White) }
                            }
                        }
                    }
                )
            }
        }
        AnimatedVisibility(
            visible = emojiOpen,
            enter = expandVertically(Motion.layout()) + fadeIn(Motion.fade(200)),
            exit = shrinkVertically(Motion.layout()) + fadeOut(Motion.fade(150))
        ) {
            Box(Modifier.background(p.bg)) { EmojiPanel(onPick = onEmoji, onBackspace = onBackspace) }
        }
    }
}


private fun readContactLine(ctx: android.content.Context, uri: android.net.Uri): String? {
    val id = uri.lastPathSegment ?: return null
    return runCatching {
        ctx.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id),
            null
        )?.use { c -> if (c.moveToFirst()) "${c.getString(0)}: ${c.getString(1)}" else null }
    }.getOrNull()
}

@android.annotation.SuppressLint("MissingPermission")
private fun lastLocationLink(ctx: android.content.Context): String? {
    val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    val loc = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time } ?: return null
    return "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
}


@Composable
private fun ContactSheet(name: String, address: String, onSearch: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val contactUri = remember(address) { findContactUri(ctx, address) }
    val canBlock = remember { app.ios.messages.data.Blocking.canBlock(ctx) }
    var blocked by remember(address) { mutableStateOf(app.ios.messages.data.Blocking.isBlocked(ctx, address)) }
    IosAlert(
        title = name,
        message = if (name != address) address else null,
        header = {
            Avatar(name, 76.dp)
            Spacer(Modifier.height(10.dp))
        },
        actions = listOf(AlertAction(stringResource(R.string.contact_done), bold = true, onClick = onDismiss)),
        onDismiss = onDismiss
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContactTile(stringResource(R.string.call), Modifier.weight(1f), { PhoneIcon(p.blue) }) {
                onDismiss()
                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address)))) }
            }
            ContactTile(
                stringResource(if (contactUri != null) R.string.contact_view else R.string.contact_add),
                Modifier.weight(1f),
                { ContactIcon(p.blue) }
            ) {
                onDismiss()
                runCatching {
                    if (contactUri != null) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, contactUri))
                    } else {
                        ctx.startActivity(
                            Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, address)
                            }
                        )
                    }
                }
            }
            ContactTile(stringResource(R.string.copy_number), Modifier.weight(1f), { CopyIcon(p.blue) }) {
                clipboard.setText(AnnotatedString(address))
                onDismiss()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.fieldFill)
                .iosClickable(onClick = { onDismiss(); onSearch() })
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchIcon(p.blue, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.search_in_chat), color = p.blue, fontSize = 16.sp)
        }
        if (canBlock) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.fieldFill)
                    .iosClickable(onClick = {
                        val ok = if (blocked) app.ios.messages.data.Blocking.unblock(ctx, address)
                        else app.ios.messages.data.Blocking.block(ctx, address)
                        if (ok) blocked = !blocked
                    }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(if (blocked) R.string.unblock_caller else R.string.block_caller),
                    color = if (blocked) p.blue else Red,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ContactTile(label: String, modifier: Modifier, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.fieldFill)
            .iosClickable(onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(label, color = p.blue, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

private fun findContactUri(ctx: android.content.Context, address: String): Uri? = runCatching {
    if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return@runCatching null
    }
    val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
    ctx.contentResolver.query(
        u,
        arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
        null, null, null
    )?.use { c ->
        if (c.moveToFirst()) ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)) else null
    }
}.getOrNull()


@Composable
private fun ChatSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    pos: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onHeight: (Int) -> Unit
) {
    val p = LocalPalette.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(p.fieldFill)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchIcon(p.secondary, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = p.label),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(p.blue),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
                if (query.isEmpty()) Text(stringResource(R.string.search), color = p.secondary, fontSize = 16.sp)
            }
            if (query.isNotBlank()) {
                Text(
                    if (count == 0) "0" else stringResource(R.string.search_count, pos + 1, count),
                    color = p.secondary,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassCircle(36.dp, Modifier, onClick = onPrev) { ChevronLeft(p.blue, Modifier.graphicsLayer { rotationZ = 90f }) }
        Spacer(Modifier.width(6.dp))
        GlassCircle(36.dp, Modifier, onClick = onNext) { ChevronLeft(p.blue, Modifier.graphicsLayer { rotationZ = -90f }) }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.cancel), color = p.blue, fontSize = 17.sp, modifier = Modifier.plainClickable(onClose))
    }
}
