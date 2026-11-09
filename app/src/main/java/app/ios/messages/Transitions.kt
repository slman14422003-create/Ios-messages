package app.ios.messages

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build

object Transitions {
    const val EXTRA_RETURN = "return_to_caller"

    fun start(context: Context, intent: Intent): Boolean = runCatching {
        val options = ActivityOptions.makeCustomAnimation(context, R.anim.ios_push_enter, R.anim.ios_push_exit)
        context.startActivity(intent, options.toBundle())
        true
    }.getOrDefault(false)

    fun install(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.ios_push_enter, R.anim.ios_push_exit)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.ios_pop_enter, R.anim.ios_pop_exit)
        }
    }

    @Suppress("DEPRECATION")
    fun finished(activity: Activity) {
        if (Build.VERSION.SDK_INT < 34) activity.overridePendingTransition(R.anim.ios_pop_enter, R.anim.ios_pop_exit)
    }
}
