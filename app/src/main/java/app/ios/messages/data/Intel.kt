package app.ios.messages.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import app.ios.messages.R
import java.text.Normalizer

/** On-device helpers: tolerant name search, phone-number detection, clipboard number, quick copy. */
object Intel {
    private val clipShape = Regex("""^\+?[\d\s().\-]{6,24}$""")
    private val inText = Regex("""(?<![\d])\+?\d[\d\s().\-]{5,18}\d(?![\d])""")

    fun normalize(s: String): String {
        if (s.isEmpty()) return s
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(d.length)
        for (ch in d) {
            val code = ch.code
            if (code in 0x0300..0x036F) continue
            if (code in 0x064B..0x065F || code == 0x0640 || code == 0x0670) continue
            when (code) {
                0x0623, 0x0625, 0x0622, 0x0671 -> sb.append(0x0627.toChar())
                0x0649, 0x0626 -> sb.append(0x064A.toChar())
                0x0629 -> sb.append(0x0647.toChar())
                0x0624 -> sb.append(0x0648.toChar())
                in 0x0660..0x0669 -> sb.append('0' + (code - 0x0660))
                in 0x06F0..0x06F9 -> sb.append('0' + (code - 0x06F0))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun editWithin(a: String, b: String, max: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > max) return false
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return false
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length] <= max
    }

    private fun tokenScore(q: String, tokens: List<String>): Int {
        var best = 0
        for (t in tokens) {
            val s = when {
                t.startsWith(q) -> 100 - (t.length - q.length).coerceAtMost(30)
                q.length >= 2 && t.contains(q) -> 60
                q.length >= 3 && fuzzy(q, t) -> 40
                else -> 0
            }
            if (s > best) best = s
        }
        return best
    }

    private fun fuzzy(q: String, t: String): Boolean {
        val max = if (q.length <= 5) 1 else 2
        for (len in (q.length - 1)..(q.length + 1)) {
            if (len < 2 || len > t.length) continue
            if (editWithin(q, t.substring(0, len), max)) return true
        }
        return false
    }

    /** Ranks contacts by name (prefix, substring, typo, initials) and number. Input order is kept for ties. */
    fun filterContacts(all: List<Contact>, query: String): List<Contact> {
        val raw = query.trim()
        if (raw.isEmpty()) return all
        val q = normalize(raw)
        val qTokens = q.split(' ').filter { it.isNotEmpty() }
        val qJoined = qTokens.joinToString("")
        val qDigits = q.filter { it.isDigit() }
        val scored = ArrayList<Pair<Int, Contact>>()
        for (c in all) {
            val tokens = normalize(c.name).split(' ', '-', '.', '_').filter { it.isNotEmpty() }
            var score = 0
            if (qTokens.isNotEmpty()) {
                var sum = 0
                var every = true
                for (qt in qTokens) {
                    val s = tokenScore(qt, tokens)
                    if (s == 0) { every = false; break }
                    sum += s
                }
                if (every) score = sum / qTokens.size
                if (score == 0 && qJoined.length in 2..4 && tokens.joinToString("") { it.take(1) }.startsWith(qJoined)) score = 55
            }
            if (qDigits.length >= 2 && qDigits.length == qJoined.length) {
                val d = normalize(c.number).filter { it.isDigit() }
                val s = if (d.startsWith(qDigits)) 80 else if (d.contains(qDigits)) 70 else 0
                if (s > score) score = s
            }
            if (score > 0) scored.add(score to c)
        }
        return scored.sortedByDescending { it.first }.map { it.second }
    }

    fun digits(raw: String): String = raw.filter { it.isDigit() || it == '+' }

    fun numbers(text: String): List<String> {
        if (text.length < 7) return emptyList()
        val out = LinkedHashSet<String>()
        for (m in inText.findAll(text)) {
            val d = digits(m.value)
            if (d.count { it.isDigit() } in 7..15) out.add(d)
        }
        return out.toList()
    }

    fun clipboardNumber(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val desc = cm.primaryClipDescription ?: return null
        if (!desc.hasMimeType("text/*")) return null
        val raw = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()?.trim().orEmpty()
        if (raw.length < 6 || raw.length > 26 || !clipShape.matches(raw)) return null
        val d = digits(raw)
        return if (d.count { it.isDigit() } in 7..15) d else null
    }

    fun copy(context: Context, value: String) {
        if (value.isEmpty()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching { cm.setPrimaryClip(ClipData.newPlainText("text", value)) }
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}
