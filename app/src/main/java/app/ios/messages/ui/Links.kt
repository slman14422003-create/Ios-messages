package app.ios.messages.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

const val TAG_URL = "url"
const val TAG_TEL = "tel"

private val urlRe = Regex("""(?:https?://|www\.)[^\s<>"]+""", RegexOption.IGNORE_CASE)
private val phoneRe = Regex("""\+?\d[\d\- ().]{5,}\d""")
private val dateRe = Regex("""\d{1,4}[/.\-]\d{1,2}[/.\-]\d{1,4}""")

fun linkify(text: String, color: Color): AnnotatedString {
    val taken = ArrayList<IntRange>()
    val spans = ArrayList<Triple<IntRange, String, String>>()
    for (m in urlRe.findAll(text)) {
        var end = m.range.last
        while (end > m.range.first && text[end] in ".,;:!?)]}'") end--
        val r = m.range.first..end
        taken += r
        val u = text.substring(r)
        spans += Triple(r, TAG_URL, if (u.startsWith("www", true)) "https://$u" else u)
    }
    for (m in phoneRe.findAll(text)) {
        if (taken.any { it.first <= m.range.last && m.range.first <= it.last }) continue
        val s = m.value
        if (dateRe.matches(s.trim())) continue
        val digits = s.count { it.isDigit() }
        if (digits < 7 || digits > 15) continue
        spans += Triple(m.range, TAG_TEL, s.filter { it.isDigit() || it == '+' })
    }
    if (spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for ((r, tag, item) in spans) {
            addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), r.first, r.last + 1)
            addStringAnnotation(tag, item, r.first, r.last + 1)
        }
    }
}
