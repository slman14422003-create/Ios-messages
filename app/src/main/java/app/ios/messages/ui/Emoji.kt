package app.ios.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private class EmojiCat(val icon: String, val title: String, val items: List<String>)

private fun e(s: String) = s.trim().split(Regex("\\s+"))

private val Cats: List<EmojiCat> = listOf(
    EmojiCat("😀", "Smileys & Emotion", e("""
        😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔
        🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐
        😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️
        💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊 💋 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ 💔
        ❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💯 💢 💥 💫 💦 💨 🕳️ 💬 👁️‍🗨️ 🗨️ 🗯️ 💭 💤
    """)),
    EmojiCat("👋", "People & Body", e("""
        👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 🖕 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️
        💅 🤳 💪 🦾 🦵 🦶 👂 🦻 👃 🧠 🫀 🫁 🦷 🦴 👀 👁️ 👅 👄 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎
        🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 🥷 👷 🤴 👸 👳 👲 🧕 🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝
        🧞 🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 👯 🧖 🧘 👭 👫 👬 💏 💑 👪
    """)),
    EmojiCat("🐶", "Animals & Nature", e("""
        🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🐒 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋
        🐌 🐞 🐜 🦟 🦗 🕷️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏
        🐪 🐫 🦒 🦘 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🕊️ 🐇 🐁 🐀 🐿️ 🦔 🌵 🎄 🌲 🌳 🌴 🌱 🌿
        ☘️ 🍀 🎍 🍃 🍂 🍁 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔 🌙 🌎 🌍
        🌏 🪐 💫 ⭐ 🌟 ✨ ⚡ ☄️ 💥 🔥 🌪️ 🌈 ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️ 🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 💨 💧 💦 ☔ 🌊
    """)),
    EmojiCat("🍔", "Food & Drink", e("""
        🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯
        🍞 🥖 🥨 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🥙 🧆 🌮 🌯 🥗 🥘 🥫 🍝 🍜 🍲 🍛 🍣 🍱 🥟
        🍤 🍙 🍚 🍘 🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 ☕ 🍵 🧃 🥤
        🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️ 🥣 🥡 🥢
    """)),
    EmojiCat("⚽", "Activity", e("""
        ⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛷 ⛸️ 🥌 🎿
        ⛷️ 🏂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭
        🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🪕 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩
    """)),
    EmojiCat("🚗", "Travel & Places", e("""
        🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛴 🚲 🛵 🏍️ 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈
        🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🗺️ 🗿 🗽 🗼 🏰
        🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏚️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫
        🏩 💒 🏛️ ⛪ 🕌 🕍 🕋 ⛩️ 🌃 🌄 🌅 🌆 🌇 🌉 🌌 🎆 🎇
    """)),
    EmojiCat("💡", "Objects", e("""
        ⌚ 📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📷 📸 📹 🎥 📞 ☎️ 📟 📠 📺 📻 🎙️ ⏰ ⏱️ ⏲️ 🕰️ ⌛ ⏳ 📡 🔋
        🔌 💡 🔦 🕯️ 🧯 🛢️ 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🧰 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🧱 ⛓️ 🧲 🔫 💣 🧨 🪓 🔪
        🗡️ ⚔️ 🛡️ 🚬 ⚰️ ⚱️ 🏺 🔮 📿 🧿 💈 ⚗️ 🔭 🔬 🕳️ 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡️ 🧹 🧺 🧻 🚽 🚰 🚿 🛁 🔑
        🗝️ 🚪 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 🎈 🎏 🎀 🎊 🎉 🎎 🏮 🎐 ✉️ 📩 📨 📧 📦 🏷️ 📜 📃 📄 📊 📈 📉 📆 📅 📌
        📍 📎 🖇️ 📏 📐 ✂️ 🗑️ 🔒 🔓 🔏 🔐 📚 📖 📝 ✏️ 🖊️ 🖋️ 🔍 🔎
    """)),
    EmojiCat("❤️", "Symbols", e("""
        ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉
        ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ 🉑 ☢️ ☣️ 📴 📳 🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 🈹 🈲 🅰️
        🅱️ 🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸
        🔱 ⚜️ 🔰 ♻️ ✅ 🈯 💹 ❇️ ✳️ ❎ 🌐 💠 Ⓜ️ 🌀 💤 🏧 🚾 ♿ 🅿️ 🈳 🈂️ 🛂 🛃 🛄 🛅 🚹 🚺 🚼 🚻 🚮 🎦 📶 🈁
        🔣 ℹ️ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 ▶️ ⏸️ ⏯️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪
        ✔️ ➕ ➖ ➗ ✖️ ♾️ 💲 ➰ ➿ 〰️ ©️ ®️ ™️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔶 🔷 🔸 🔹 🔳 🔲
    """)),
    EmojiCat("🏳️", "Flags", e("""
        🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🏴‍☠️ 🇸🇾 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇧🇭 🇴🇲 🇾🇪 🇮🇶 🇯🇴 🇱🇧 🇵🇸 🇪🇬 🇱🇾 🇹🇳 🇩🇿 🇲🇦 🇸🇩 🇹🇷
        🇮🇷 🇺🇸 🇬🇧 🇨🇦 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇷🇺 🇺🇦 🇵🇱 🇬🇷 🇨🇳 🇯🇵 🇰🇷 🇮🇳 🇵🇰
        🇧🇩 🇮🇩 🇲🇾 🇸🇬 🇹🇭 🇻🇳 🇵🇭 🇦🇺 🇳🇿 🇧🇷 🇦🇷 🇲🇽 🇨🇴 🇨🇱 🇿🇦 🇳🇬 🇰🇪 🇪🇹
    """))
)

private val recents = ArrayList<String>()

private sealed class Cell {
    class Title(val text: String) : Cell()
    class Line(val items: List<String>) : Cell()
}

private const val COLS = 8

/** Full-height emoji keyboard: category jump bar at the bottom, delete key, recents on top. */
@Composable
fun EmojiPanel(
    onPick: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    val cats = remember {
        buildList {
            add(EmojiCat("🕘", "Frequently Used", emptyList()))
            addAll(Cats)
        }
    }
    val cells = remember(recents.size, recents.firstOrNull()) {
        buildList<Cell> {
            val rec = if (recents.isEmpty()) Cats[0].items.take(COLS * 2) else recents.take(COLS * 2)
            add(Cell.Title(cats[0].title))
            rec.chunked(COLS).forEach { add(Cell.Line(it)) }
            Cats.forEach { c ->
                add(Cell.Title(c.title))
                c.items.chunked(COLS).forEach { add(Cell.Line(it)) }
            }
        }
    }
    val titleIdx = remember(cells) { cells.mapIndexedNotNull { i, c -> if (c is Cell.Title) i else null } }
    val current by remember(titleIdx) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            titleIdx.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }
    val tick = rememberTick()

    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(p.cell)
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state) {
            items(cells) { cell ->
                when (cell) {
                    is Cell.Title -> Text(
                        cell.text.uppercase(),
                        color = p.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 4.dp)
                    )
                    is Cell.Line -> Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                        cell.items.forEach { em ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .plainClickable {
                                        tick()
                                        recents.remove(em)
                                        recents.add(0, em)
                                        while (recents.size > COLS * 2) recents.removeAt(recents.lastIndex)
                                        onPick(em)
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(em, fontSize = 28.sp) }
                        }
                        repeat(COLS - cell.items.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp.Hairline).background(p.separator))
        Row(
            Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            cats.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .plainClickable { scope.launch { state.animateScrollToItem(titleIdx[i]) } },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (current == i) p.pressed else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { Text(c.icon, fontSize = 18.sp) }
                }
            }
            Box(
                Modifier.weight(1f).fillMaxSize().plainClickable { tick(); onBackspace() },
                contentAlignment = Alignment.Center
            ) { BackspaceIcon(p.secondary) }
        }
    }
}

/** Removes the last user-visible character (handles surrogate pairs, flags, ZWJ sequences). */
fun dropLastGrapheme(s: String): String {
    if (s.isEmpty()) return s
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(s)
    val start = it.preceding(s.length)
    return if (start <= 0) "" else s.substring(0, start)
}

/** 1..3 when the text is only that many emoji (jumbo display, like iMessage), otherwise 0. */
fun emojiOnlyCount(s: String): Int {
    val t = s.trim()
    if (t.isEmpty() || t.length > 40) return 0
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        val ok = cp >= 0x1F000 || cp in 0x2190..0x2BFF || cp == 0x200D || cp == 0xFE0F ||
            cp == 0x20E3 || cp == 0xA9 || cp == 0xAE || cp == 0x203C || cp == 0x2049 ||
            cp == 0x2122 || cp == 0x2139 || cp in 0x3030..0x3030 || cp == 0x303D ||
            cp == 0x3297 || cp == 0x3299 || cp in 0x30..0x39 || cp == 0x23 || cp == 0x2A ||
            Character.isWhitespace(cp)
        if (!ok) return 0
        i += Character.charCount(cp)
    }
    if (t.none { c -> !(c.isDigit() || c == '#' || c == '*' || c.isWhitespace()) }) return 0
    val bi = android.icu.text.BreakIterator.getCharacterInstance()
    bi.setText(t)
    var n = 0
    var start = bi.first()
    var end = bi.next()
    while (end != android.icu.text.BreakIterator.DONE) {
        if (t.substring(start, end).isNotBlank()) n++
        start = end
        end = bi.next()
    }
    return if (n in 1..3) n else 0
}
