package app.ios.messages.data

import android.content.Context
import android.content.res.Configuration
import app.ios.messages.R
import java.util.Locale

/** Quick-reply suggestions picked from the shape of the last incoming message. Texts are always Arabic. */
object SmartReply {
    private val timeRe = Regex("""\b\d{1,2}[:.]\d{2}\b|\b\d{1,2}\s?(am|pm)\b""")
    private val yesNoStart = Regex("""^(are|is|do|does|did|can|could|will|would|should|shall|have|has|am|was|were)\b""")
    private val greeting = Regex("""^(hi|hello|hey|yo|good (morning|afternoon|evening))\b""")
    private val thanks = Regex("""\b(thanks|thank you|thx|ty|appreciate)\b""")
    private val callWords = Regex("""\b(call me|call you|give me a call|ring me|can we talk|free to talk)\b""")
    private val whereWords = Regex("""\b(where are you|where r u|your location|send location|how far|eta)\b""")
    private val planWords = Regex("""\b(tonight|tomorrow|today|meet|lunch|dinner|coffee|what time|are you coming|join us)\b""")

    private val arPeace = Regex("""السلام عليكم|سلام عليكم""")
    private val arGreeting = Regex("""مرحبا|اهلا|هلا|صباح الخير|مساء الخير|صباح النور|مساء النور""")
    private val arThanks = Regex("""شكرا|مشكور|تسلم|يعطيك العافيه|الله يعطيك""")
    private val arCall = Regex("""اتصل|كلمني|رن علي|نتكلم|اتكلم معك""")
    private val arWhere = Regex("""وينك|وين انت|اين انت|وصلت|ارسل الموقع|موقعك""")
    private val arPlan = Regex("""اليوم|بكره|غدا|الليله|نتقابل|الغداء|العشاء|القهوه|متى|الساعه|موعد|تعال""")
    private val arYesNo = Regex("""^(هل|ممكن|تقدر|بتقدر|عندك|فيك|تبي|تبغى|تريد|ايش|شو)""")

    fun suggest(body: String?, address: String): List<Int> {
        val raw = body?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > 300) return emptyList()
        if (address.count { it.isDigit() } < 7 || address.any { it.isLetter() }) return emptyList()
        if (Otp.extract(raw) != null) return emptyList()
        val t = Intel.normalize(raw)
        if (t.contains("http://") || t.contains("https://") || t.contains("www.")) return emptyList()
        val question = raw.contains('?') || raw.contains(0x061F.toChar())
        val isThanks = thanks.containsMatchIn(t) || arThanks.containsMatchIn(t)
        val isGreeting = greeting.containsMatchIn(t) || arGreeting.containsMatchIn(t)
        val isCall = callWords.containsMatchIn(t) || arCall.containsMatchIn(t)
        val isWhere = whereWords.containsMatchIn(t) || arWhere.containsMatchIn(t)
        val isPlan = planWords.containsMatchIn(t) || arPlan.containsMatchIn(t)
        val isTime = timeRe.containsMatchIn(t)
        val isYesNo = yesNoStart.containsMatchIn(t) || arYesNo.containsMatchIn(t)
        val out: List<Int> = when {
            raw.length < 60 && arPeace.containsMatchIn(t) -> listOf(R.string.reply_peace, R.string.reply_how_are_you)
            isThanks && !question -> listOf(R.string.reply_welcome, R.string.reply_no_problem)
            raw.length < 40 && isGreeting && !question -> listOf(R.string.reply_hello, R.string.reply_how_are_you)
            isCall -> listOf(R.string.reply_calling_now, R.string.reply_call_later, R.string.reply_cant_talk)
            isWhere -> listOf(R.string.reply_on_my_way, R.string.reply_sending_location, R.string.reply_few_minutes)
            question && (isTime || isPlan) -> listOf(R.string.reply_sounds_good, R.string.reply_cant_make_it, R.string.reply_get_back)
            question && isYesNo -> listOf(R.string.reply_yes, R.string.reply_no, R.string.reply_maybe)
            question -> listOf(R.string.reply_one_moment, R.string.reply_ok, R.string.reply_get_back)
            isPlan || isTime -> listOf(R.string.reply_sounds_good, R.string.reply_cant_make_it)
            raw.length <= 140 -> listOf(R.string.reply_ok, R.string.reply_thanks, R.string.reply_got_it)
            else -> emptyList()
        }
        return out.take(3)
    }

    @Volatile private var arabic: Context? = null

    /** Resolves a suggestion string in Arabic no matter which language the app itself is using. */
    fun text(ctx: Context, id: Int): String {
        val c = arabic ?: synchronized(this) {
            arabic ?: run {
                val base = ctx.applicationContext
                val cfg = Configuration(base.resources.configuration)
                cfg.setLocale(Locale("ar"))
                base.createConfigurationContext(cfg).also { arabic = it }
            }
        }
        return c.getString(id)
    }
}
