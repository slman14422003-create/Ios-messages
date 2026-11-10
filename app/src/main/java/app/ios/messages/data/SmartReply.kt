package app.ios.messages.data

import app.ios.messages.R

/** Offline quick-reply suggestions picked from the shape of the last incoming message. */
object SmartReply {
    private val timeRe = Regex("""\b\d{1,2}[:.]\d{2}\b|\b\d{1,2}\s?(am|pm)\b""")
    private val yesNoStart = Regex("""^(are|is|do|does|did|can|could|will|would|should|shall|have|has|am|was|were)\b""")
    private val greeting = Regex("""^(hi|hello|hey|yo|good (morning|afternoon|evening))\b""")
    private val thanks = Regex("""\b(thanks|thank you|thx|ty|appreciate)\b""")
    private val callWords = Regex("""\b(call me|call you|give me a call|ring me|can we talk|free to talk)\b""")
    private val whereWords = Regex("""\b(where are you|where r u|your location|send location|how far|eta)\b""")
    private val planWords = Regex("""\b(tonight|tomorrow|today|meet|lunch|dinner|coffee|what time|are you coming|join us)\b""")

    fun suggest(body: String?, address: String): List<Int> {
        val raw = body?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > 300) return emptyList()
        if (address.count { it.isDigit() } < 7 || address.any { it.isLetter() }) return emptyList()
        if (Otp.extract(raw) != null) return emptyList()
        val t = raw.lowercase()
        if (t.contains("http://") || t.contains("https://") || t.contains("www.")) return emptyList()
        val question = raw.contains('?') || raw.contains(0x061F.toChar())
        val out: List<Int> = when {
            thanks.containsMatchIn(t) && !question -> listOf(R.string.reply_welcome, R.string.reply_no_problem)
            raw.length < 40 && greeting.containsMatchIn(t) && !question -> listOf(R.string.reply_hello, R.string.reply_how_are_you)
            callWords.containsMatchIn(t) -> listOf(R.string.reply_calling_now, R.string.reply_call_later, R.string.reply_cant_talk)
            whereWords.containsMatchIn(t) -> listOf(R.string.reply_on_my_way, R.string.reply_sending_location, R.string.reply_few_minutes)
            question && (timeRe.containsMatchIn(t) || planWords.containsMatchIn(t)) ->
                listOf(R.string.reply_sounds_good, R.string.reply_cant_make_it, R.string.reply_get_back)
            question && yesNoStart.containsMatchIn(t) -> listOf(R.string.reply_yes, R.string.reply_no, R.string.reply_maybe)
            question -> listOf(R.string.reply_one_moment, R.string.reply_ok, R.string.reply_get_back)
            planWords.containsMatchIn(t) || timeRe.containsMatchIn(t) -> listOf(R.string.reply_sounds_good, R.string.reply_cant_make_it)
            raw.length <= 140 -> listOf(R.string.reply_ok, R.string.reply_thanks, R.string.reply_got_it)
            else -> emptyList()
        }
        return out.take(3)
    }
}
