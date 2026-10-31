package app.ios.messages.ui

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
    val rev = remember(messages) { buildItems(itemsCtx, messages).asReversed() }
    val listState = rememberLazyListState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var inputH by remember { mutableIntStateOf(with(density) { (66.dp + navBottom).roundToPx() }) }
    val statusH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barH = 108.dp
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
        tween(240),
        label = "list"
    )

    Box(Modifier.fillMaxSize().background(p.bg).imePadding()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = listAlpha }
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
            items(rev, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    is ChatItem.Time -> TimeHeader(item)
                    is ChatItem.Bubble -> BubbleRow(
                        item,
                        isDefault,
                        onDeleteMessage,
                        { reveal.value },
                        { viewing = it },
                        if (listReady) Modifier.animateItemPlacement(spring(dampingRatio = 0.85f, stiffness = 500f)) else Modifier
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
                .background(
                    Brush.verticalGradient(
                        0f to p.bg.copy(alpha = 0.97f),
                        0.82f to p.bg.copy(alpha = 0.95f),
                        1f to p.bg.copy(alpha = 0f)
                    )
                )
                .statusBarsPadding()
        ) {
            run {
                Box(Modifier.fillMaxWidth().height(barH)) {
                    GlassCircle(
                        44.dp,
                        Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                        onClick = onBack
                    ) { ChevronLeft(p.blue, Modifier.padding(end = 2.dp)) }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        IosMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            IosMenuItem(
                                text = { Text(stringResource(R.string.call)) },
                                onClick = {
                                    menu = false
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address))))
                                    }
                                }
                            )
                            IosMenuItem(
                                text = { Text(stringResource(R.string.add_to_contacts)) },
                                onClick = {
                                    menu = false
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                                putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                            }
                                        )
                                    }
                                }
                            )
                            IosMenuItem(
                                text = { Text(stringResource(R.string.copy_number)) },
                                onClick = {
                                    menu = false
                                    clipboard.setText(AnnotatedString(address))
                                }
                            )
                        }
                    }
                    Column(
                        Modifier.align(Alignment.Center).plainClickable { menu = true },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Avatar(name, 52.dp)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                color = p.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            ChevronRight(p.secondary, width = 5, height = 9, stroke = 1.4f)
                        }
                        val headerSim = sims.firstOrNull { s -> s.subId == messages.lastOrNull { !it.mine && it.subId >= 0 }?.subId }
                            ?: sims.firstOrNull { it.subId == simSel }
                        if (headerSim != null) {
                            Spacer(Modifier.height(1.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                AlertDialog(
                    onDismissRequest = { wrongSim = false },
                    title = { Text(stringResource(R.string.different_sim)) },
                    text = { Text(stringResource(R.string.different_sim_msg, a.label, b.label)) },
                    confirmButton = {
                        TextButton(onClick = {
                            wrongSim = false
                            warned = true
                            doSend(simSel)
                        }) { Text(stringResource(R.string.send_from, b.label)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            wrongSim = false
                            picked = a.subId
                            onPickSim(a.subId)
                            doSend(a.subId)
                        }) { Text(stringResource(R.string.use_sim, a.label)) }
                    }
                )
            } else wrongSim = false
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            InputBar(
                text = text,
                attach = attach,
                sims = sims,
                simSel = simSel,
                onSim = { picked = it; onPickSim(it) },
                onChange = { text = it },
                onHeight = { inputH = it },
                onPlus = { picker.launch("image/*") },
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
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
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
        if (hasText) {
            if (b.m.images.isNotEmpty()) Spacer(Modifier.height(2.dp))
            Text(
                text = annotated,
                onTextLayout = { layout = it },
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
                                    val l = layout ?: return@detectTapGestures
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
    val timeLabel = remember(b.m.date) { DateFmt.time(b.m.date) }
    val appear = remember { Animatable(if (System.currentTimeMillis() - b.m.date < 4000L) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 420f))
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
                        val s = 0.6f + 0.4f * v
                        scaleX = s
                        scaleY = s
                        alpha = v.coerceIn(0f, 1f)
                        translationY = (1f - v) * 14.dp.toPx()
                        transformOrigin = TransformOrigin(if (mine == (bdir > 0f)) 1f else 0f, 1f)
                    }
                    .onGloballyPositioned { boundsRef[0] = it.boundsInWindow() }
            ) {
                BubbleContent(
                    b = b,
                    maxW = maxW,
                    interactive = true,
                    onLongPress = {
                        thud()
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
    onPlus: () -> Unit,
    onClearAttach: () -> Unit,
    onSend: () -> Unit
) {
    val p = LocalPalette.current
    val sent = LocalSentColor.current
    val pill = RoundedCornerShape(22.dp)
    val canSend = text.isNotBlank() || attach != null
    Column(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .background(
                Brush.verticalGradient(
                    0f to p.bg.copy(alpha = 0f),
                    0.35f to p.bg.copy(alpha = 0.95f),
                    1f to p.bg
                )
            )
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(
            visible = attach != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
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
        AnimatedVisibility(
            visible = sims.size > 1,
            enter = fadeIn() + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f)),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            SimSwitch(
                sims, simSel, onSim,
                Modifier.padding(start = 58.dp, top = if (attach != null) 4.dp else 12.dp)
            )
        }
        Row(
            Modifier.padding(
                start = 12.dp, end = 12.dp,
                top = if (attach != null || sims.size > 1) 6.dp else 14.dp, bottom = 8.dp
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
                    .plainClickable(onPlus),
                contentAlignment = Alignment.Center
            ) { PlusIcon(p.secondary) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .shadow(10.dp, pill, clip = false, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                    .clip(pill)
                    .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))
                    .background(p.cell)
                    .border(Dp.Hairline, p.outline, pill)
            ) {
                BasicTextField(
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
                                visible = !canSend,
                                enter = fadeIn() + scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)),
                                exit = fadeOut() + scaleOut(targetScale = 0.6f)
                            ) {
                                Box(Modifier.padding(end = 12.dp, bottom = 12.dp)) { MicIcon(p.secondary) }
                            }
                            AnimatedVisibility(
                                visible = canSend,
                                enter = fadeIn() + scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)),
                                exit = fadeOut() + scaleOut(targetScale = 0.5f)
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
    }
}
