package com.cloud.launcher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.cloud.launcher.utils.PermissionUtils
import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RecentAppsRepository(private val prefs: PrefsManager) {

    companion object {
        const val MAX_RECENTS = 40
        private const val USAGE_WINDOW_MS = 24L * 60L * 60L * 1000L
    }

    val recents: Flow<List<String>> = prefs.recentApps

    suspend fun record(packageName: String) {
        val current = recents.first()
        val updated = (listOf(packageName) + current.filter { it != packageName }).take(MAX_RECENTS)
        prefs.setRecents(updated)
    }

    suspend fun remove(packageName: String) {
        prefs.setRecents(recents.first().filter { it != packageName })
        val dismissed = prefs.dismissedRecents.first().toMutableMap()
        dismissed[packageName] = System.currentTimeMillis()
        prefs.setDismissed(dismissed)
    }

    suspend fun clear() {
        val now = System.currentTimeMillis()
        val dismissed = prefs.dismissedRecents.first().toMutableMap()
        for (pkg in recents.first()) dismissed[pkg] = now
        prefs.setDismissed(dismissed)
        prefs.setRecents(emptyList())
    }

    /** يكمل القائمة من UsageStatsManager (آخر 24 ساعة) إذا كانت الصلاحية ممنوحة. */
    suspend fun syncFromUsageStats(context: Context) = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasUsageAccess(context)) return@withContext
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - USAGE_WINDOW_MS, end)
            val event = UsageEvents.Event()
            val lastForeground = HashMap<String, Long>()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForeground[event.packageName] = event.timeStamp
                }
            }
            val pm = context.packageManager
            val dismissed = prefs.dismissedRecents.first()
            val fromUsage = lastForeground.entries
                .sortedByDescending { it.value }
                .filter { entry ->
                    entry.key != context.packageName &&
                        pm.getLaunchIntentForPackage(entry.key) != null &&
                        entry.value > (dismissed[entry.key] ?: 0L)
                }
                .map { it.key }
            val current = recents.first()
            val merged = (current + fromUsage.filter { it !in current }).take(MAX_RECENTS)
            if (merged != current) prefs.setRecents(merged)
        } catch (e: Exception) {
            // تجاهل: الصلاحية قد تُسحب أثناء التنفيذ
        }
    }
}
