package app.ios.messages.ui

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
import androidx.compose.foundation.ExperimentalFoundationApi
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

private sealed interface ChatItem {
    val key: String

    data class Time(val day: String, val time: String, val id: Long) : ChatItem {
        override val key: String get() = "t$id"
    }

    data class Bubble(val m: Message, val tail: Boolean, val tight: Boolean) : ChatItem {
        override val key: String get() = "m${m.id}"
    }

    data class Status(val text: String) : ChatItem {
        override val key: String get() = "status"
    }
}

private const val TIME_GAP = 60L * 60_000L

/** Like iOS: consecutive messages from the same sender form a group; only the last one gets a tail. */
private fun buildItems(msgs: List<Message>): List<ChatItem> {
    val out = ArrayList<ChatItem>(msgs.size + 8)
    var prev: Message? = null
    msgs.forEachIndexed { i, m ->
        val p = prev
        val header = p == null || m.date - p.date > TIME_GAP
        if (header) {
            val (d, t) = DateFmt.chat(m.date)
            out += ChatItem.Time(d, t, m.id)
        }
        val next = msgs.getOrNull(i + 1)
        val tight = p != null && !header && p.mine == m.mine
        val tail = next == null || next.mine != m.mine || next.date - m.date > TIME_GAP
        out += ChatItem.Bubble(m, tail, tight)
        prev = m
    }
    if (msgs.lastOrNull()?.mine == true) out += ChatItem.Status("Sent as Text Message")
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    target: ChatTarget,
    messages: List<Message>,
    isDefault: Boolean,
    onBack: () -> Unit,
    onSend: (address: String, body: String) -> Unit,
    onDeleteMessage: (Long) -> Unit
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val address = target.address.orEmpty()
    var menu by remember { mutableStateOf(false) }
    val name = target.name ?: target.address ?: ""
    var text by rememberSaveable { mutableStateOf("") }
    val rev = remember(messages) { buildItems(messages).asReversed() }
    val listState = rememberLazyListState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var inputH by remember { mutableIntStateOf(with(density) { (66.dp + navBottom).roundToPx() }) }
    val statusH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barH = 94.dp
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
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
                            scope.launch { reveal.snapTo((reveal.value - dx / revealPx).coerceIn(0f, 1f)) }
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
                        if (listReady) Modifier.animateItemPlacement(spring(dampingRatio = 0.85f, stiffness = 500f)) else Modifier
                    )
                    is ChatItem.Status -> Text(
                        item.text,
                        color = p.secondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp, end = 16.dp)
                    )
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
                                text = { Text("Call") },
                                onClick = {
                                    menu = false
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address))))
                                    }
                                }
                            )
                            IosMenuItem(
                                text = { Text("Add to Contacts") },
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
                                text = { Text("Copy Number") },
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
                        Avatar(name, 48.dp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                color = p.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            ChevronRight(p.secondary, width = 5, height = 8, stroke = 1.3f)
                        }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            InputBar(
                text = text,
                onChange = { text = it },
                onHeight = { inputH = it },
                onPlus = {
                    Toast.makeText(ctx, "Attachments (MMS) aren't supported yet", Toast.LENGTH_SHORT).show()
                },
                onSend = {
                    val body = text.trim()
                    val addr = address
                    if (body.isNotEmpty() && addr.isNotEmpty()) {
                        tick()
                        onSend(addr, body)
                        text = ""
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
private fun BubbleRow(
    b: ChatItem.Bubble,
    isDefault: Boolean,
    onDelete: (Long) -> Unit,
    reveal: () -> Float,
    modifier: Modifier
) {
    val p = LocalPalette.current
    val sent = LocalSentColor.current
    val mine = b.m.mine
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val thud = rememberThud()
    var menu by remember { mutableStateOf(false) }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.76f).dp
    val shape = remember(mine, b.tail) { BubbleShape(mine, b.tail) }
    val linkColor = if (mine) Color.White else p.blue
    val annotated = remember(b.m.body, linkColor) { linkify(b.m.body, linkColor) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val timeLabel = remember(b.m.date) { DateFmt.time(b.m.date) }
    val appear = remember { Animatable(if (System.currentTimeMillis() - b.m.date < 4000L) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 420f))
    }
    val shiftPx = with(LocalDensity.current) { 56.dp.toPx() }
    val padStart = if (mine) 12.dp else 18.dp
    val padEnd = if (mine) 18.dp else 12.dp

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = -reveal() * shiftPx }
                .padding(top = if (b.tight) 2.dp else 9.dp, start = 8.dp, end = 8.dp),
            contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier.graphicsLayer {
                    val v = appear.value
                    val s = 0.6f + 0.4f * v
                    scaleX = s
                    scaleY = s
                    alpha = v.coerceIn(0f, 1f)
                    translationY = (1f - v) * 14.dp.toPx()
                    transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
                }
            ) {
                Text(
                    text = annotated,
                    onTextLayout = { layout = it },
                    color = if (mine) Color.White else p.receivedText,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .widthIn(max = maxW)
                        .background(if (mine) sent else p.received, shape)
                        .pointerInput(annotated) {
                            val ox = padStart.toPx()
                            val oy = 7.dp.toPx()
                            detectTapGestures(
                                onLongPress = {
                                    thud()
                                    menu = true
                                },
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
                        }
                        .padding(start = padStart, end = padEnd, top = 7.dp, bottom = 7.dp)
                )
                IosMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    IosMenuItem(
                        text = { Text("Copy") },
                        onClick = { clipboard.setText(AnnotatedString(b.m.body)); menu = false }
                    )
                    IosMenuItem(
                        text = { Text("Share") },
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
                            text = { Text("Delete", color = Red) },
                            onClick = { onDelete(b.m.id); menu = false }
                        )
                    }
                }
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
}

/** Floating iOS 26 style input: glass "+" circle and glass pill, over a soft gradient. */
@Composable
private fun InputBar(
    text: String,
    onChange: (String) -> Unit,
    onHeight: (Int) -> Unit,
    onPlus: () -> Unit,
    onSend: () -> Unit
) {
    val p = LocalPalette.current
    val sent = LocalSentColor.current
    val pill = RoundedCornerShape(22.dp)
    Row(
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
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        GlassCircle(44.dp, onClick = onPlus) { PlusIcon(p.secondary) }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(pill)
                .background(p.glass)
                .border(Dp.Hairline, p.separator, pill)
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
                            if (text.isEmpty()) Text("Text Message", color = p.secondary, fontSize = 17.sp)
                            inner()
                        }
                        AnimatedVisibility(
                            visible = text.isNotBlank(),
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
