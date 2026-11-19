package com.iosuite.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Keeps the splash screen up until the first real data is ready (conversations, contacts, call log),
 * with a hard limit so a missing permission or a slow provider can never leave it stuck.
 */
class SplashGate(private val maxMs: Long = 1500L) {
    private val start = SystemClock.uptimeMillis()
    @Volatile private var ready = false

    fun release() {
        ready = true
    }

    fun install(activity: Activity) {
        val splash = activity.installSplashScreen()
        splash.setKeepOnScreenCondition { !ready && SystemClock.uptimeMillis() - start < maxMs }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Calls reportFullyDrawn once, two frames after [ready] turned true (the first real draw of the data). */
@Composable
fun ReportFullyDrawnWhen(ready: Boolean) {
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(ready) {
        if (!ready || activity == null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { activity.reportFullyDrawn() }
    }
}
