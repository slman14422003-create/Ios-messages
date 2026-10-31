package app.ios.messages.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
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

private const val GROUP_GAP = 60_000L
private const val TIME_GAP = 60L * 60_000L

private fun buildItems(msgs: List<Message>): List<ChatItem> {
    val out = ArrayList<ChatItem>()
    var prev: Message? = null
    var lastWasTime = false
    msgs.forEachIndexed { i, m ->
        val p = prev
        lastWasTime = false
        if (p == null || m.date - p.date > TIME_GAP) {
            val (d, t) = DateFmt.chat(m.date)
            out += ChatItem.Time(d, t, m.id)
            lastWasTime = true
        }
        val next = msgs.getOrNull(i + 1)
        val tight = p != null && !lastWasTime && p.mine == m.mine && m.date - p.date < GROUP_GAP
        val tail = next == null || next.mine != m.mine || next.date - m.date >= GROUP_GAP
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
    val composeMode = target.address == null
    val name = target.name ?: target.address ?: ""
    var to by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val items = remember(messages) { buildItems(messages) }
    val listState = rememberLazyListState()
    var first by remember { mutableStateOf(true) }
    val statusH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barH = if (composeMode) 88.dp else 86.dp
    val toFocus = remember { FocusRequester() }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            if (first) {
                listState.scrollToItem(items.lastIndex); first = false
            } else listState.animateScrollToItem(items.lastIndex)
        }
    }
    LaunchedEffect(composeMode) { if (composeMode) runCatching { toFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(p.bg).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = statusH + barH + 8.dp, bottom = 8.dp)
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Time -> TimeHeader(item)
                        is ChatItem.Bubble -> BubbleRow(item, isDefault, onDeleteMessage)
                        is ChatItem.Status -> Text(
                            item.text,
                            color = p.secondary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }

            // Translucent navigation bar
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(p.bar)
                    .statusBarsPadding()
            ) {
                if (composeMode) {
                    Box(Modifier.fillMaxWidth().height(44.dp)) {
                        Text(
                            "New Message",
                            color = p.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Text(
                            "Cancel",
                            color = p.blue,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .plainClickable(onBack)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    Hairline()
                    Row(
                        Modifier.fillMaxWidth().height(43.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("To:", color = p.secondary, fontSize = 17.sp)
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = to,
                            onValueChange = { to = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 17.sp, color = p.label),
                            cursorBrush = SolidColor(p.blue),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f).focusRequester(toFocus)
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(86.dp)) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .plainClickable(onBack)
                                .padding(start = 10.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
                        ) { ChevronLeft(p.blue) }
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(name, 44.dp)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name,
                                    color = p.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                ChevronRight(p.secondary, width = 4, height = 7, stroke = 1.2f)
                            }
                        }
                    }
                }
                Hairline()
            }
        }

        InputBar(
            text = text,
            onChange = { text = it },
            onPlus = {
                Toast.makeText(ctx, "Attachments (MMS) aren't supported yet", Toast.LENGTH_SHORT).show()
            },
            onSend = {
                val body = text.trim()
                val addr = target.address ?: to.trim()
                if (body.isNotEmpty() && addr.isNotEmpty()) {
                    onSend(addr, body)
                    text = ""
                }
            }
        )
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
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleRow(b: ChatItem.Bubble, isDefault: Boolean, onDelete: (Long) -> Unit) {
    val p = LocalPalette.current
    val mine = b.m.mine
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.76f).dp
    val shape = remember(mine, b.tail) { BubbleShape(mine, b.tail) }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (b.tight) 2.dp else 8.dp, start = 8.dp, end = 8.dp),
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
                    .background(if (mine) SentBubble else p.received, shape)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
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

@Composable
private fun InputBar(text: String, onChange: (String) -> Unit, onPlus: () -> Unit, onSend: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.bg).navigationBarsPadding()) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier
                    .padding(bottom = 1.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(p.fieldFill)
                    .plainClickable(onPlus),
                contentAlignment = Alignment.Center
            ) { PlusIcon(p.secondary) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .border(1.dp, p.outline, RoundedCornerShape(18.dp))
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
                                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                if (text.isEmpty()) {
                                    Text("Text Message", color = p.tertiary, fontSize = 17.sp)
                                }
                                inner()
                            }
                            AnimatedVisibility(
                                visible = text.isNotBlank(),
                                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                                exit = fadeOut() + scaleOut(targetScale = 0.6f)
                            ) {
                                Box(
                                    Modifier
                                        .padding(end = 3.dp, bottom = 3.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(SentBubble)
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
}
