package com.iosuite.core

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build

/**
 * One window background for all three apps. It follows the app's own appearance setting
 * (0 = auto, 1 = light, 2 = dark), never the system theme alone, so moving between the apps
 * never shows a flash of the wrong colour behind the transition.
 */
object WindowBg {
    val LIGHT: Int = 0xFFFFFFFF.toInt()
    val DARK: Int = 0xFF000000.toInt()

    fun isDark(mode: Int, context: Context): Boolean = when (mode) {
        1 -> false
        2 -> true
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun color(mode: Int, context: Context): Int = if (isDark(mode, context)) DARK else LIGHT

    /** Call right after super.onCreate, before setContent. */
    fun apply(activity: Activity, mode: Int) = applyColor(activity, isDark(mode, activity))

    /** Call again whenever the resolved light/dark state changes. */
    fun applyColor(activity: Activity, dark: Boolean) {
        activity.window.setBackgroundDrawable(ColorDrawable(if (dark) DARK else LIGHT))
    }

    /**
     * Android 13+: remembers which splash background the system should use on the next cold start,
     * so it follows the app's own setting instead of the system night mode. Auto resets to the manifest theme.
     */
    fun persistSplash(activity: Activity, mode: Int) {
        if (Build.VERSION.SDK_INT < 33) return
        runCatching {
            val theme = when (mode) {
                1 -> R.style.Theme_IosSuite_SplashBg_Light
                2 -> R.style.Theme_IosSuite_SplashBg_Dark
                else -> 0
            }
            activity.splashScreen.setSplashScreenTheme(theme)
        }
    }
}
