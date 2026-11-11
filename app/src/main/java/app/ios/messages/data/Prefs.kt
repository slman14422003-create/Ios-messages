package app.ios.messages.data

import android.content.Context
import app.ios.messages.security.SecureStore

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var bubbleBlue: Boolean
        get() = sp.getBoolean("bubble_blue", false)
        set(v) { sp.edit().putBoolean("bubble_blue", v).apply() }

    var bubbleConfig: BubbleConfig
        get() = BubbleConfig(
            theme = sp.getString("bubble_theme", null) ?: if (bubbleBlue) "blue" else "green",
            hue = sp.getFloat("bubble_hue", 210f),
            received = sp.getInt("bubble_received", 0),
            tails = sp.getBoolean("bubble_tails", true),
            chatBg = sp.getInt("bubble_chat_bg", 0),
            gradient = sp.getBoolean("bubble_gradient", true)
        )
        set(v) {
            sp.edit()
                .putString("bubble_theme", v.theme)
                .putFloat("bubble_hue", v.hue)
                .putInt("bubble_received", v.received)
                .putBoolean("bubble_tails", v.tails)
                .putInt("bubble_chat_bg", v.chatBg)
                .putBoolean("bubble_gradient", v.gradient)
                .apply()
        }

    /** Theme id chosen for one conversation, or "" to follow the app-wide theme. */
    fun chatTheme(address: String): String = sp.getString("chat_theme_" + key(address), "") ?: ""

    fun setChatTheme(address: String, id: String) {
        val e = sp.edit()
        if (id.isEmpty()) e.remove("chat_theme_" + key(address)) else e.putString("chat_theme_" + key(address), id)
        e.apply()
    }

    var appearance: Int
        get() = sp.getInt("appearance", 0)
        set(v) { sp.edit().putInt("appearance", v).apply() }

    var textScale: Float
        get() = sp.getFloat("textScale", 1f)
        set(v) { sp.edit().putFloat("textScale", v).apply() }

    var keepDays: Int
        get() = sp.getInt("keepDays", 0)
        set(v) { sp.edit().putInt("keepDays", v).apply() }

    var language: String
        get() = sp.getString("language", "system") ?: "system"
        set(v) { sp.edit().putString("language", v).apply() }

    var notifChannelVersion: Int
        get() = sp.getInt("notif_channel_version", 0)
        set(v) { sp.edit().putInt("notif_channel_version", v).apply() }

    /** Smooth mode: lists use cheap static bars instead of live blur. */
    var smoothMode: Boolean
        get() = sp.getBoolean("smooth_mode3", true)
        set(v) { sp.edit().putBoolean("smooth_mode3", v).apply() }

    var sentSound: Boolean
        get() = sp.getBoolean("sent_sound", true)
        set(v) { sp.edit().putBoolean("sent_sound", v).apply() }

    // ---- privacy ------------------------------------------------------------------------------

    /** FLAG_SECURE: blocks screenshots, screen recording and the app-switcher thumbnail. On by default. */
    var screenSecure: Boolean
        get() = sp.getBoolean("screen_secure", true)
        set(v) { sp.edit().putBoolean("screen_secure", v).apply() }

    var muted: Set<Long>
        get() = (sp.getStringSet("muted", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { sp.edit().putStringSet("muted", v.map { it.toString() }.toSet()).apply() }

    var pinned: Set<Long>
        get() = (sp.getStringSet("pinned", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { sp.edit().putStringSet("pinned", v.map { it.toString() }.toSet()).apply() }

    // ---- setup -------------------------------------------------------------------------------

    var setupDismissed: Boolean
        get() = sp.getBoolean("setup_dismissed", false)
        set(v) { sp.edit().putBoolean("setup_dismissed", v).apply() }

    // ---- per-conversation keys ---------------------------------------------------------------

    /** Same person => same key, regardless of "+966", spaces, dashes. */
    fun key(address: String): String = keyOf(address)

    companion object {
        /** Process-wide version of [key] (used by the notifier and the contact cache, which have no Prefs instance). */
        fun keyOf(address: String): String {
            val d = address.filter { it.isDigit() }
            return if (d.length >= 7) d.takeLast(9) else address.trim().lowercase()
        }
    }

    // ---- SIM ---------------------------------------------------------------------------------

    /** Remembered SIM for this conversation, or null if never chosen. */
    fun simFor(address: String): Int? =
        sp.getInt("sim_" + key(address), Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

    fun setSimFor(address: String, subId: Int) {
        sp.edit().putInt("sim_" + key(address), subId).apply()
    }

    /** App-wide default SIM. -1 = follow the system's default SMS SIM. */
    var defaultSub: Int
        get() = sp.getInt("default_sub", -1)
        set(v) { sp.edit().putInt("default_sub", v).apply() }

    fun simLabel(subId: Int): String? = sp.getString("sim_label_$subId", null)?.takeIf { it.isNotBlank() }

    fun simColor(subId: Int): Long? =
        sp.getLong("sim_color_$subId", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }

    fun setSim(subId: Int, label: String, color: Long) {
        sp.edit().putString("sim_label_$subId", label.trim()).putLong("sim_color_$subId", color).apply()
    }

    // ---- drafts ------------------------------------------------------------------------------

    fun draft(address: String): String = SecureStore.open(sp.getString("draft_" + key(address), "") ?: "")

    fun setDraft(address: String, text: String) {
        val k = key(address)
        val e = sp.edit()
        if (text.isBlank()) {
            e.remove("draft_$k").remove("draft_addr_$k").remove("draft_time_$k")
        } else {
            if (draft(address) != text) e.putLong("draft_time_$k", System.currentTimeMillis())
            e.putString("draft_$k", SecureStore.seal(text)).putString("draft_addr_$k", SecureStore.seal(address))
        }
        e.apply()
    }

    fun allDrafts(): List<Triple<String, String, Long>> =
        sp.all.keys
            .filter { it.startsWith("draft_") && !it.startsWith("draft_addr_") && !it.startsWith("draft_time_") }
            .mapNotNull { k ->
                val id = k.removePrefix("draft_")
                val a = SecureStore.open(sp.getString("draft_addr_$id", null) ?: return@mapNotNull null)
                val t = SecureStore.open(sp.getString(k, "") ?: "")
                if (a.isBlank()) return@mapNotNull null
                if (t.isBlank()) null else Triple(a, t, sp.getLong("draft_time_$id", 0L))
            }
}
