package app.ios.messages

import android.content.Context
import android.content.res.Configuration
import app.ios.messages.data.Prefs
import java.util.Locale

object Lang {
    fun wrap(ctx: Context): Context {
        val tag = Prefs(ctx).language
        if (tag == "system") {
            Locale.setDefault(ctx.resources.configuration.locales[0])
            return ctx
        }
        val loc = Locale.forLanguageTag(tag)
        Locale.setDefault(loc)
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        return ctx.createConfigurationContext(cfg)
    }
}
