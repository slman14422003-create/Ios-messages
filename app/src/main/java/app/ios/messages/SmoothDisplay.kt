package app.ios.messages

import android.app.Activity
import android.os.Build
import android.view.Display

/** Asks the system for the highest refresh rate the panel offers at its current resolution (smoother animations). */
object SmoothDisplay {
    fun apply(activity: Activity) {
        runCatching {
            val display: Display = (if (Build.VERSION.SDK_INT >= 30) activity.display else null) ?: return
            val current = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxOfOrNull { it.refreshRate } ?: return
            if (best <= current.refreshRate + 1f) return
            val lp = activity.window.attributes
            lp.preferredRefreshRate = best
            activity.window.attributes = lp
        }
    }
}
