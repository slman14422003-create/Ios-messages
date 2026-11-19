package com.iosuite.core

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

/** JankStats that only runs (and only logs) in debuggable builds. */
object Jank {
    private const val TAG = "IosJank"
    private val holders = java.util.WeakHashMap<Activity, JankStats>()

    fun attach(activity: Activity, screen: String = activity.javaClass.simpleName) {
        val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        synchronized(holders) { if (holders.containsKey(activity)) return }
        runCatching {
            val state = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView).state
            state?.putState("Screen", screen)
            val listener = object : JankStats.OnFrameListener {
                override fun onFrame(volatileFrameData: FrameData) {
                    if (volatileFrameData.isJank) {
                        val ms = volatileFrameData.frameDurationUiNanos / 1_000_000.0
                        Log.w(
                            TAG,
                            "slow frame %.1f ms in %s %s".format(
                                ms, screen, volatileFrameData.states.joinToString { "${it.key}=${it.value}" }
                            )
                        )
                    }
                }
            }
            val stats = JankStats.createAndTrack(activity.window, listener)
            stats.isTrackingEnabled = true
            synchronized(holders) { holders[activity] = stats }
        }
    }

    fun setEnabled(activity: Activity, enabled: Boolean) {
        synchronized(holders) { holders[activity]?.isTrackingEnabled = enabled }
    }
}
