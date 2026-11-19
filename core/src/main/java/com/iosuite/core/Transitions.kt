package com.iosuite.core

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build

object Transitions {
    const val EXTRA_RETURN = "return_to_caller"

    /** Regular iOS-style push. This is also the fallback when the other app is not installed. */
    fun start(context: Context, intent: Intent): Boolean = runCatching {
        val options = ActivityOptions.makeCustomAnimation(context, R.anim.ios_push_enter, R.anim.ios_push_exit)
        context.startActivity(intent, options.toBundle())
        true
    }.getOrDefault(false)

    /**
     * Shared-element hop: the new window only fades in while the contact's photo and name fly from
     * [hero]'s source position to their place on the next screen (see HeroHost). Apps that do not
     * understand the extra simply show the fade.
     */
    fun startHero(context: Context, intent: Intent, hero: Hero?): Boolean {
        if (hero == null) return start(context, intent)
        return runCatching {
            HeroIntent.put(intent, hero)
            val options = ActivityOptions.makeCustomAnimation(context, R.anim.ios_hero_enter, R.anim.ios_hero_exit)
            context.startActivity(intent, options.toBundle())
            true
        }.getOrDefault(false)
    }

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
