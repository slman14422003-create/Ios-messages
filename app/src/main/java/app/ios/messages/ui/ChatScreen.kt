package app.ios.messages.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var inputH by remember { mutableIntStateOf(0) }
    val statusH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barH = 94.dp

    val newestKey = rev.firstOrNull()?.key
    LaunchedEffect(newestKey) { if (rev.isNotEmpty()) listState.animateScrollToItem(0) }

    Box(Modifier.fillMaxSize().background(p.bg).imePadding()) {
        // Newest message at index 0 + reverseLayout => opens at the bottom with no scroll jump.
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusH + barH + 6.dp,
                bottom = with(density) { inputH.toDp() } + 6.dp
            )
        ) {
            items(rev, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    is ChatItem.Time -> TimeHeader(item)
                    is ChatItem.Bubble -> BubbleRow(item, isDefault, onDeleteMessage)
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
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Call") },
                                onClick = {
                                    menu = false
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address))))
                                    }
                                }
                            )
                            DropdownMenuItem(
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
                            DropdownMenuItem(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleRow(b: ChatItem.Bubble, isDefault: Boolean, onDelete: (Long) -> Unit) {
    val p = LocalPalette.current
    val sent = LocalSentColor.current
    val mine = b.m.mine
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.76f).dp
    val shape = remember(mine, b.tail) { BubbleShape(mine, b.tail) }
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (b.tight) 2.dp else 9.dp, start = 8.dp, end = 8.dp),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box {
            Text(
                text = b.m.body,
                color = if (mine) Color.White else p.receivedText,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .widthIn(max = maxW)
                    .background(if (mine) sent else p.received, shape)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {},
                        onLongClick = { menu = true }
                    )
                    .padding(
                        start = if (mine) 12.dp else 18.dp,
                        end = if (mine) 18.dp else 12.dp,
                        top = 7.dp,
                        bottom = 7.dp
                    )
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = { clipboard.setText(AnnotatedString(b.m.body)); menu = false }
                )
                if (isDefault) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = Red) },
                        onClick = { onDelete(b.m.id); menu = false }
                    )
                }
            }
        }
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
                            enter = fadeIn() + scaleIn(initialScale = 0.6f),
                            exit = fadeOut() + scaleOut(targetScale = 0.6f)
                        ) {
                            Box(
                                Modifier
                                    .padding(end = 5.dp, bottom = 5.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(sent)
                                    .plainClickable(onSend),
                                contentAlignment = Alignment.Center
                            ) { SendArrow(Color.White) }
                        }
                    }
                }
            )
        }
    }
}
