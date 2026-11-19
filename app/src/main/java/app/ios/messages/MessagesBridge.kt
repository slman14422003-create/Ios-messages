package app.ios.messages

import android.content.Context
import app.ios.messages.data.Prefs
import com.iosuite.core.PhoneKey
import com.iosuite.core.SuiteBridge
import com.iosuite.core.SuiteNotif

/** Missed calls reported by the Phone app, kept until the conversation is read (or 24 h pass). */
object MissedState {
    class Rec(val number: String, val count: Int, val time: Long, val name: String)

    private const val TTL = 24 * 60 * 60_000L
    private fun p(ctx: Context) = ctx.getSharedPreferences("suite_missed", Context.MODE_PRIVATE)

    fun record(ctx: Context, number: String, count: Int, time: Long, name: String?) {
        val clean = (name ?: "").replace('|', ' ')
        p(ctx).edit().putString(PhoneKey.of(number), "${number.replace('|', ' ')}|$count|$time|$clean").apply()
    }

    fun get(ctx: Context, key: String): Rec? {
        val raw = p(ctx).getString(key, null) ?: return null
        val f = raw.split('|')
        if (f.size < 4) return null
        val rec = Rec(f[0], f[1].toIntOrNull() ?: 1, f[2].toLongOrNull() ?: 0L, f[3])
        if (System.currentTimeMillis() - rec.time > TTL) {
            clear(ctx, key)
            return null
        }
        return rec
    }

    fun clear(ctx: Context, key: String) {
        p(ctx).edit().remove(key).apply()
    }

    fun clearAll(ctx: Context): List<String> {
        val numbers = p(ctx).all.values.mapNotNull { (it as? String)?.substringBefore('|') }
        p(ctx).edit().clear().apply()
        return numbers
    }

    fun lines(ctx: Context, key: String): List<Pair<String, Long>> {
        val rec = get(ctx, key) ?: return emptyList()
        return listOf(SuiteNotif.missedText(Lang.wrap(ctx), rec.count) to rec.time)
    }

    fun text(ctx: Context, key: String): String = lines(ctx, key).firstOrNull()?.first.orEmpty()
}

/** Phone -> Messages: missed calls to merge, and read-state changes made in the Phone app. */
object MessagesBridge : SuiteBridge.Handler {
    override fun onMissedCall(context: Context, number: String, count: Int, whenMs: Long, name: String?): Boolean {
        MissedState.record(context, number, count, whenMs, name)
        return Notifier.mergeMissed(context, number)
    }

    override fun onReadState(context: Context, kind: String, number: String?) {
        if (kind == SuiteBridge.KIND_MISSED_ALL_READ) {
            MissedState.clearAll(context).forEach { Notifier.repost(context, it) }
        }
    }
}
