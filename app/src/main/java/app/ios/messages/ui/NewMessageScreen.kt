package app.ios.messages.ui

import androidx.compose.ui.res.stringResource
import app.ios.messages.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.data.Contact
import kotlinx.coroutines.launch

private sealed interface NmRow {
    data class Header(val letter: String) : NmRow
    data class Entry(val contact: Contact) : NmRow
}

private fun letterOf(name: String): String {
    val ch = name.trim().firstOrNull() ?: return "#"
    return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewMessageScreen(
    contacts: List<Contact>,
    onCancel: () -> Unit,
    onPick: (address: String, name: String?) -> Unit
) {
    val p = LocalPalette.current
    var to by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = to.trim()
    val digits = q.filter { it.isDigit() }
    val looksNumber = digits.isNotEmpty() && q.all { it.isDigit() || it in "+-() " }

    val sorted = remember(contacts) {
        contacts.sortedWith(compareBy<Contact>({ letterOf(it.name) == "#" }, { it.name.lowercase() }))
    }
    val filtered = remember(sorted, q) { app.ios.messages.data.Intel.filterContacts(sorted, q) }
    val clipNumber = rememberClipboardNumber()
    val clipShown = q.isEmpty() && clipNumber != null
    val rows = remember(filtered) {
        val out = ArrayList<NmRow>()
        var last = ""
        filtered.forEach { c ->
            val l = letterOf(c.name)
            if (l != last) {
                out += NmRow.Header(l)
                last = l
            }
            out += NmRow.Entry(c)
        }
        out
    }
    val letters = remember(rows) { rows.filterIsInstance<NmRow.Header>().map { it.letter } }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Text(
                stringResource(R.string.notif_new_message),
                color = p.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                stringResource(R.string.cancel),
                color = p.blue,
                fontSize = 17.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .plainClickable(onCancel)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.to_label), color = p.secondary, fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = to,
                onValueChange = { to = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = p.label),
                cursorBrush = SolidColor(p.blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (looksNumber) onPick(q, null)
                        else filtered.singleOrNull()?.let { onPick(it.number, it.name) }
                    }
                ),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        Hairline()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (looksNumber) {
                    item(key = "num") {
                        Box(Modifier.fillMaxWidth().iosClickable({ onPick(q, null) })) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar("", 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(q, color = p.label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                    Text(stringResource(R.string.send_to_number), color = p.secondary, fontSize = 14.sp)
                                }
                            }
                            Hairline(Modifier.align(Alignment.BottomStart).padding(start = 68.dp))
                        }
                    }
                }
                if (clipShown && clipNumber != null) {
                    item(key = "clip") {
                        Box(Modifier.fillMaxWidth().iosClickable({ onPick(clipNumber, null) })) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar("", 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(clipNumber, color = p.label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                    Text(stringResource(R.string.clip_number_title), color = p.secondary, fontSize = 14.sp)
                                }
                            }
                            Hairline(Modifier.align(Alignment.BottomStart).padding(start = 68.dp))
                        }
                    }
                }
                rows.forEachIndexed { i, r ->
                    when (r) {
                        is NmRow.Header -> stickyHeader(key = "h${r.letter}") {
                            Text(
                                r.letter,
                                color = p.secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(p.groupBg)
                                    .padding(horizontal = 16.dp, vertical = 3.dp)
                            )
                        }
                        is NmRow.Entry -> item(key = "c$i") {
                            val c = r.contact
                            Box(Modifier.fillMaxWidth().iosClickable({ onPick(c.number, c.name) })) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(c.name, 40.dp, c.photo)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            c.name,
                                            color = p.label,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(c.number, color = p.secondary, fontSize = 14.sp, maxLines = 1)
                                    }
                                }
                                Hairline(Modifier.align(Alignment.BottomStart).padding(start = 68.dp))
                            }
                        }
                    }
                }
                if (rows.isEmpty() && !looksNumber) {
                    item(key = "none") {
                        Text(
                            if (q.isEmpty()) stringResource(R.string.type_name_or_number) else stringResource(R.string.no_contacts_found),
                            color = p.secondary,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp)
                        )
                    }
                }
            }

            if (q.isEmpty() && !imeVisible && letters.size > 3) {
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    letters.forEach { l ->
                        Text(
                            l,
                            color = p.blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .plainClickable {
                                    val idx = rows.indexOfFirst { it is NmRow.Header && it.letter == l }
                                    if (idx >= 0) scope.launch { listState.scrollToItem(idx + (if (clipShown) 1 else 0)) }
                                }
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
