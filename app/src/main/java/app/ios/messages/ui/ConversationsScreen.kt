package app.ios.messages.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.data.Conversation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    conversations: List<Conversation>,
    isDefault: Boolean,
    hasPermission: Boolean,
    onOpen: (Conversation) -> Unit,
    onCompose: () -> Unit,
    onDelete: (Conversation) -> Unit,
    onMakeDefault: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter { it.name.contains(query, true) || it.snippet.contains(query, true) }
    }
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex >= 1 } }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(150), label = "bar")
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top + 44.dp, bottom = bottom)
        ) {
            item(key = "title") {
                Text(
                    "Messages",
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
                        "Messages needs permission to read your SMS. If Android says it is blocked: App Settings, then the ⋮ menu, then Allow restricted settings.",
                        "Allow", onGrant, "Open App Settings", onOpenSettings
                    )
                }
            } else if (!isDefault) {
                item(key = "default") {
                    Banner("Make Messages your default SMS app to send, receive and delete messages.", "Set Default", onMakeDefault)
                }
            }

            items(filtered, key = { it.threadId }) { c ->
                val state = rememberSwipeToDismissBoxState(
                    confirmValueChange = { v ->
                        if (v == SwipeToDismissBoxValue.EndToStart && isDefault) {
                            onDelete(c); true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = state,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().background(Red).padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text("Delete", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                ) {
                    Box(Modifier.background(p.bg)) { ConversationRow(c) { onOpen(c) } }
                }
            }

            if (filtered.isEmpty() && hasPermission) {
                item(key = "empty") {
                    Text(
                        if (query.isBlank()) "No Messages" else "No Results",
                        color = p.secondary,
                        fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Navigation bar: transparent while the large title is visible, translucent once collapsed.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(p.bar.copy(alpha = p.bar.alpha * barAlpha))
                .statusBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Text(
                    "Messages",
                    color = p.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center).alpha(barAlpha)
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .plainClickable(onCompose)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) { ComposeIcon(p.blue) }
            }
            Hairline(Modifier.alpha(barAlpha))
        }
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
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().iosClickable(onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.width(26.dp).height(50.dp), contentAlignment = Alignment.Center) {
                if (c.unread > 0) Box(Modifier.size(10.dp).clip(CircleShape).background(p.blue))
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
