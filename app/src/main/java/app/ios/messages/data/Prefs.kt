package app.ios.messages.data

import android.content.Context
import app.ios.messages.security.SecureStore

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var bubbleBlue: Boolean
        get() = sp.getBoolean("bubble_blue", false)
        set(v) { sp.edit().putBoolean("bubble_blue", v).apply() }

    var notifications: Boolean
        get() = sp.getBoolean("notifications", true)
        set(v) { sp.edit().putBoolean("notifications", v).apply() }

    var previews: Boolean
        get() = sp.getBoolean("previews", true)
        set(v) { sp.edit().putBoolean("previews", v).apply() }

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

    var notifSound: Boolean
        get() = sp.getBoolean("notif_sound", true)
        set(v) { sp.edit().putBoolean("notif_sound", v).apply() }

    var notifVibrate: Boolean
        get() = sp.getBoolean("notif_vibrate", true)
        set(v) { sp.edit().putBoolean("notif_vibrate", v).apply() }

    var notifHeadsUp: Boolean
        get() = sp.getBoolean("notif_heads_up", true)
        set(v) { sp.edit().putBoolean("notif_heads_up", v).apply() }

    var notifLockScreen: Boolean
        get() = sp.getBoolean("notif_lock_screen", true)
        set(v) { sp.edit().putBoolean("notif_lock_screen", v).apply() }

    var notifQuickReply: Boolean
        get() = sp.getBoolean("notif_quick_reply", true)
        set(v) { sp.edit().putBoolean("notif_quick_reply", v).apply() }

    var notifCopyCode: Boolean
        get() = sp.getBoolean("notif_copy_code", true)
        set(v) { sp.edit().putBoolean("notif_copy_code", v).apply() }

    var notifGroup: Boolean
        get() = sp.getBoolean("notif_group", true)
        set(v) { sp.edit().putBoolean("notif_group", v).apply() }

    var notifChannelVersion: Int
        get() = sp.getInt("notif_channel_version", 0)
        set(v) { sp.edit().putInt("notif_channel_version", v).apply() }

    // ---- notification look (in-app banner + system accent) -----------------------------------

    /** Show our own banner while the app is open (the system shade cannot be restyled on Android 12+). */
    var bannerEnabled: Boolean
        get() = sp.getBoolean("banner_enabled", true)
        set(v) { sp.edit().putBoolean("banner_enabled", v).apply() }

    /** ARGB colour of the banner / notification accent. 0 = follow the sent-bubble colour. */
    var notifColor: Long
        get() = sp.getLong("notif_color", 0L)
        set(v) { sp.edit().putLong("notif_color", v).apply() }

    var bannerAlpha: Float
        get() = sp.getFloat("banner_alpha", 0.88f).coerceIn(0.3f, 1f)
        set(v) { sp.edit().putFloat("banner_alpha", v.coerceIn(0.3f, 1f)).apply() }

    /** 0 none, 1 slide, 2 pop, 3 fade, 4 bounce. */
    /** Blur radius (dp) of the banner's own window blur. Independent from the app's glass effects. */
    var bannerBlur: Int
        get() = sp.getInt("banner_blur", 24).coerceIn(0, 60)
        set(v) { sp.edit().putInt("banner_blur", v.coerceIn(0, 60)).apply() }

    var bannerAnim: Int
        get() = sp.getInt("banner_anim", 1).coerceIn(0, 4)
        set(v) { sp.edit().putInt("banner_anim", v).apply() }

    var bannerSeconds: Int
        get() = sp.getInt("banner_seconds", 4).coerceIn(2, 10)
        set(v) { sp.edit().putInt("banner_seconds", v).apply() }

    /** 0 = bundled iPhone-style tone, 1 = the phone's default notification sound. */
    var notifTone: Int
        get() = sp.getInt("notif_tone", 0)
        set(v) { sp.edit().putInt("notif_tone", v).apply() }

    var sentSound: Boolean
        get() = sp.getBoolean("sent_sound", true)
        set(v) { sp.edit().putBoolean("sent_sound", v).apply() }

    /** 0 default, 1 double tap, 2 long. */
    var vibPattern: Int
        get() = sp.getInt("vib_pattern", 0)
        set(v) { sp.edit().putInt("vib_pattern", v).apply() }

    /** Ask the system to let message notifications through Do Not Disturb (needs the user's grant). */
    var bypassDnd: Boolean
        get() = sp.getBoolean("bypass_dnd", false)
        set(v) { sp.edit().putBoolean("bypass_dnd", v).apply() }

    var showSimLabel: Boolean
        get() = sp.getBoolean("show_sim_label", true)
        set(v) { sp.edit().putBoolean("show_sim_label", v).apply() }

    /** The colour actually used (ARGB int). */
    fun notifColorArgb(): Int {
        val c = notifColor
        return if (c != 0L) c.toInt() else if (bubbleBlue) 0xFF007AFF.toInt() else 0xFF34C759.toInt()
    }

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
    fun key(address: String): String {
        val d = address.filter { it.isDigit() }
        return if (d.length >= 7) d.takeLast(9) else address.trim().lowercase()
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
