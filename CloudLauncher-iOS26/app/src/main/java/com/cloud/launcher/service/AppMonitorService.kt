package com.cloud.launcher.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.cloud.launcher.data.RecentAppsRepository
import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repository: RecentAppsRepository? = null
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = RecentAppsRepository(PrefsManager(applicationContext))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastPackage) return
        if (pkg == applicationContext.packageName || pkg == "com.android.systemui") return
        // نسجّل فقط التطبيقات التي لها أيقونة تشغيل (تطبيقات المستخدم)
        val launchable = try {
            packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }
        if (!launchable) return
        lastPackage = pkg
        val repo = repository ?: return
        scope.launch { repo.record(pkg) }
    }

    override fun onInterrupt() {
        // لا شيء: الخدمة لا تقدم ملاحظات صوتية
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
