package app.ios.messages.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.data.Conversation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    conversations: List<Conversation>,
    pinned: Set<Long>,
    muted: Set<Long>,
    isDefault: Boolean,
    hasPermission: Boolean,
    onOpen: (Conversation) -> Unit,
    onCompose: () -> Unit,
    onSettings: () -> Unit,
    onDelete: (List<Long>) -> Unit,
    onMarkRead: (List<Long>) -> Unit,
    onTogglePin: (Long) -> Unit,
    onToggleMute: (Long) -> Unit,
    onMakeDefault: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var filterIdx by rememberSaveable { mutableIntStateOf(0) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var editMenu by remember { mutableStateOf(false) }
    var rowMenu by remember { mutableStateOf<Long?>(null) }
    val thud = rememberThud()
    val tick = rememberTick()

    val filtered = remember(conversations, filterIdx, query) {
        conversations.filter { c ->
            val pass = when (filterIdx) {
                1 -> c.unread > 0
                2 -> !c.known
                else -> true
            }
            pass && (query.isBlank() || c.name.contains(query, true) || c.snippet.contains(query, true))
        }
    }
    val pins = remember(filtered, pinned, query) {
        if (query.isBlank()) filtered.filter { it.threadId in pinned } else emptyList()
    }
    val rest = remember(filtered, pinned, query) {
        if (query.isBlank()) filtered.filter { it.threadId !in pinned } else filtered
    }
    val title = when (filterIdx) {
        1 -> "Unread Messages"
        2 -> "Unknown Senders"
        else -> "Messages"
    }

    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex >= 1 } }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(150), label = "bar")
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun endSelecting() {
        selecting = false
        selected = emptySet()
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top + 44.dp, bottom = bottom + if (selecting) 56.dp else 0.dp)
        ) {
            item(key = "title") {
                Text(
                    title,
                    color = p.label,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp)
                )
            }
            item(key = "search") { SearchField(query) { query = it } }

            if (!hasPermission) {
                item(key = "perm") {
                    Banner(
                        "Messages needs permission to read your SMS. If Android says it is blocked: App Settings, then the \u22EE menu, then Allow restricted settings.",
                        "Allow", onGrant, "Open App Settings", onOpenSettings
                    )
                }
            } else if (!isDefault) {
                item(key = "default") {
                    Banner("Make Messages your default SMS app to send, receive and delete messages.", "Set Default", onMakeDefault)
                }
            }

            if (pins.isNotEmpty()) {
                item(key = "pins") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        pins.chunked(3).forEach { rowItems ->
                            Row(Modifier.fillMaxWidth()) {
                                rowItems.forEach { c ->
                                    PinTile(
                                        c = c,
                                        modifier = Modifier.weight(1f),
                                        onClick = { if (selecting) toggle(c.threadId) else onOpen(c) },
                                        onLongClick = { if (!selecting) { thud(); rowMenu = c.threadId } },
                                        menu = {
                                            RowMenu(
                                                c, true, c.threadId in muted, isDefault, rowMenu == c.threadId,
                                                onDismiss = { rowMenu = null },
                                                onToggleMute = { onToggleMute(c.threadId) },
                                                onTogglePin = { onTogglePin(c.threadId) },
                                                onMarkRead = { onMarkRead(listOf(c.threadId)) },
                                                onDelete = { onDelete(listOf(c.threadId)) }
                                            )
                                        }
                                    )
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                item(key = "pins_sep") { Hairline(Modifier.padding(start = 16.dp)) }
            }

            items(rest, key = { it.threadId }) { c ->
                val state = rememberSwipeToDismissBoxState(
                    confirmValueChange = { v ->
                        when {
                            v == SwipeToDismissBoxValue.EndToStart && isDefault -> {
                                onDelete(listOf(c.threadId)); true
                            }
                            v == SwipeToDismissBoxValue.StartToEnd -> {
                                tick(); onTogglePin(c.threadId); false
                            }
                            else -> false
                        }
                    }
                )
                SwipeToDismissBox(
                    state = state,
                    modifier = Modifier.animateItemPlacement(spring(dampingRatio = 0.85f, stiffness = 500f)),
                    enableDismissFromStartToEnd = !selecting,
                    enableDismissFromEndToStart = !selecting,
                    backgroundContent = {
                        val toPin = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(if (toPin) PinOrange else Red)
                                .padding(horizontal = 24.dp),
                            contentAlignment = if (toPin) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Text(
                                if (toPin) (if (c.threadId in pinned) "Unpin" else "Pin") else "Delete",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                ) {
                    Box(Modifier.background(p.bg)) {
                        ConversationRow(
                            c = c,
                            selecting = selecting,
                            selected = c.threadId in selected,
                            muted = c.threadId in muted,
                            onClick = { if (selecting) toggle(c.threadId) else onOpen(c) },
                            onLongClick = { if (!selecting) { thud(); rowMenu = c.threadId } }
                        )
                        RowMenu(
                            c, c.threadId in pinned, c.threadId in muted, isDefault, rowMenu == c.threadId,
                            onDismiss = { rowMenu = null },
                            onToggleMute = { onToggleMute(c.threadId) },
                            onTogglePin = { onTogglePin(c.threadId) },
                            onMarkRead = { onMarkRead(listOf(c.threadId)) },
                            onDelete = { onDelete(listOf(c.threadId)) }
                        )
                    }
                }
            }

            if (filtered.isEmpty() && hasPermission) {
                item(key = "empty") {
                    Text(
                        if (query.isBlank()) "No Messages" else "No Results",
                        color = p.secondary,
                        fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(p.bar.copy(alpha = p.bar.alpha * barAlpha))
                .statusBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    Text(
                        if (selecting) "Done" else "Edit",
                        color = p.blue,
                        fontSize = 17.sp,
                        fontWeight = if (selecting) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .dimClickable { if (selecting) endSelecting() else editMenu = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    DropdownMenu(expanded = editMenu, onDismissRequest = { editMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Select Messages") },
                            onClick = { editMenu = false; selecting = true; selected = emptySet() }
                        )
                        listOf("All Messages", "Unread Messages", "Unknown Senders").forEachIndexed { i, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { filterIdx = i; editMenu = false },
                                trailingIcon = { if (filterIdx == i) CheckMark(p.blue, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
                Text(
                    title,
                    color = p.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center).alpha(barAlpha)
                )
                if (!selecting) {
                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .dimClickable(onSettings)
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) { GearIcon(p.blue) }
                        Box(
                            Modifier
                                .dimClickable(onCompose)
                                .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                        ) { ComposeIcon(p.blue) }
                    }
                }
            }
            Hairline(Modifier.alpha(barAlpha))
        }

        if (selecting) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(p.bar)
                    .navigationBarsPadding()
            ) {
                Hairline()
                Row(
                    Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Read All",
                        color = p.blue,
                        fontSize = 17.sp,
                        modifier = Modifier.plainClickable {
                            val ids = if (selected.isEmpty()) conversations.filter { it.unread > 0 }.map { it.threadId }
                            else selected.toList()
                            if (ids.isNotEmpty()) onMarkRead(ids)
                            endSelecting()
                        }
                    )
                    val canDelete = selected.isNotEmpty() && isDefault
                    Text(
                        "Delete",
                        color = if (canDelete) Red else p.tertiary,
                        fontSize = 17.sp,
                        modifier = Modifier.plainClickable {
                            if (canDelete) {
                                onDelete(selected.toList())
                                endSelecting()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowMenu(
    c: Conversation,
    isPinned: Boolean,
    isMuted: Boolean,
    isDefault: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isPinned) "Unpin" else "Pin") },
            onClick = { onTogglePin(); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text(if (isMuted) "Show Alerts" else "Hide Alerts") },
            onClick = { onToggleMute(); onDismiss() }
        )
        if (c.unread > 0) {
            DropdownMenuItem(
                text = { Text("Mark as Read") },
                onClick = { onMarkRead(); onDismiss() }
            )
        }
        if (isDefault) {
            DropdownMenuItem(
                text = { Text("Delete", color = Red) },
                onClick = { onDelete(); onDismiss() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinTile(
    c: Conversation,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: @Composable () -> Unit
) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    Column(
        modifier
            .padding(vertical = 6.dp)
            .pressScale(src, 0.92f)
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Avatar(c.name, 76.dp)
            if (c.unread > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(p.blue)
                )
            }
            menu()
        }
        Text(
            c.name,
            color = p.label,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.fieldFill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchIcon(p.secondary)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, color = p.label),
            cursorBrush = SolidColor(p.blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("Search", color = p.secondary, fontSize = 17.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit, second: String? = null, onSecond: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.fieldFill)
            .padding(14.dp)
    ) {
        Text(text, color = p.label, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            action,
            color = p.blue,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.plainClickable(onClick)
        )
        if (second != null && onSecond != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                second,
                color = p.blue,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.plainClickable(onSecond)
            )
        }
    }
}

@Composable
private fun SelectCircle(selected: Boolean) {
    val p = LocalPalette.current
    val base = Modifier.size(22.dp).clip(CircleShape)
    Box(
        if (selected) base.background(p.blue) else base.border(1.5.dp, p.tertiary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) CheckMark(Color.White, Modifier.size(12.dp))
    }
}

@Composable
private fun ConversationRow(
    c: Conversation,
    selecting: Boolean,
    selected: Boolean,
    muted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().iosClickable(onClick, onLongClick)) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.width(26.dp).height(50.dp), contentAlignment = Alignment.Center) {
                if (selecting) SelectCircle(selected)
                else if (c.unread > 0) Box(Modifier.size(10.dp).clip(CircleShape).background(p.blue))
            }
            Avatar(c.name, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        c.name,
                        color = p.label,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (muted) {
                        BellSlash(p.tertiary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(DateFmt.list(c.date), color = p.secondary, fontSize = 15.sp, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    ChevronRight(p.tertiary)
                }
                Text(
                    c.snippet,
                    color = p.secondary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Hairline(Modifier.align(Alignment.BottomStart).padding(start = 88.dp))
    }
}

private val PinOrange = Color(0xFFFF9F0A)
