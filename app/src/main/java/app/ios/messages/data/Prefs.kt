package app.ios.messages.data

import android.content.Context

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

    var muted: Set<Long>
        get() = (sp.getStringSet("muted", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { sp.edit().putStringSet("muted", v.map { it.toString() }.toSet()).apply() }

    var pinned: Set<Long>
        get() = (sp.getStringSet("pinned", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { sp.edit().putStringSet("pinned", v.map { it.toString() }.toSet()).apply() }
}
