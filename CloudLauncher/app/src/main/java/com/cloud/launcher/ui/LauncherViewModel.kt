package com.cloud.launcher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.data.AppRepository
import com.cloud.launcher.data.ProtectedAppsRepository
import com.cloud.launcher.data.RecentAppsRepository
import com.cloud.launcher.root.OomController
import com.cloud.launcher.root.ProcessManager
import com.cloud.launcher.root.RootManager
import com.cloud.launcher.root.RunningApp
import com.cloud.launcher.service.ProtectionService
import com.cloud.launcher.ui.components.IconCache
import com.cloud.launcher.utils.PermissionUtils
import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_FAVORITES = 20
    }

    data class SetupState(
        val usageAccess: Boolean = false,
        val accessibility: Boolean = false,
        val notifications: Boolean = false
    )

    private val ctx: Context = application.applicationContext
    private val prefs = PrefsManager(ctx)
    private val appRepo = AppRepository(ctx)
    private val protectedRepo = ProtectedAppsRepository(prefs)
    private val recentRepo = RecentAppsRepository(prefs)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    val appMap: StateFlow<Map<String, AppInfo>> = _apps
        .map { list -> list.associateBy { it.packageName } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val favorites: StateFlow<List<String>> =
        prefs.favoriteApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val protectedPkgs: StateFlow<List<String>> =
        protectedRepo.protectedApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentPkgs: StateFlow<List<String>> =
        recentRepo.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortByUsage: StateFlow<Boolean> =
        prefs.sortByUsage.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val launchCounts: StateFlow<Map<String, Long>> =
        prefs.launchCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val drawerApps: StateFlow<List<AppInfo>> = combine(_apps, _query, sortByUsage, launchCounts) { list, q, byUsage, counts ->
        val text = q.trim()
        val filtered = if (text.isEmpty()) list else list.filter { it.label.contains(text, ignoreCase = true) }
        if (byUsage) {
            filtered.sortedWith(
                compareByDescending<AppInfo> { counts[it.packageName] ?: 0L }.thenBy { it.label.lowercase() }
            )
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _rootAvailable = MutableStateFlow<Boolean?>(null)
    val rootAvailable: StateFlow<Boolean?> = _rootAvailable.asStateFlow()

    private val _running = MutableStateFlow<Map<String, RunningApp>>(emptyMap())
    val running: StateFlow<Map<String, RunningApp>> = _running.asStateFlow()

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _showSetup = MutableStateFlow(false)
    val showSetup: StateFlow<Boolean> = _showSetup.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshApps()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        refreshApps()
        viewModelScope.launch {
            val shown = prefs.setupShown.first()
            if (!shown) {
                refreshPermissions()
                _showSetup.value = true
            } else {
                _rootAvailable.value = RootManager.isRootAvailable()
                if (_rootAvailable.value == true && protectedRepo.current().isNotEmpty()) {
                    ProtectionService.start(ctx)
                }
            }
        }
    }

    override fun onCleared() {
        try {
            ctx.unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // غير مسجّل
        }
        super.onCleared()
    }

    private fun toast(message: String) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    private fun str(id: Int): String = ctx.getString(id)

    // ---------- التطبيقات ----------

    fun refreshApps() {
        viewModelScope.launch {
            IconCache.clear()
            _apps.value = appRepo.loadApps()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSortByUsage(value: Boolean) {
        viewModelScope.launch { prefs.setSortByUsage(value) }
    }

    fun launchApp(packageName: String) {
        if (!appRepo.launch(packageName)) {
            toast(str(R.string.toast_launch_failed))
            return
        }
        viewModelScope.launch {
            prefs.incrementLaunch(packageName)
            recentRepo.record(packageName)
        }
    }

    fun openAppInfo(packageName: String) = appRepo.openAppInfo(packageName)

    fun uninstall(packageName: String) = appRepo.uninstall(packageName)

    // ---------- المفضلة ----------

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch {
            val current = favorites.value
            when {
                packageName in current -> prefs.setFavorites(current - packageName)
                current.size >= MAX_FAVORITES -> toast(str(R.string.toast_home_limit))
                else -> prefs.setFavorites(current + packageName)
            }
        }
    }

    // ---------- الأخيرة المخصصة ----------

    fun addToRecents(packageName: String) {
        viewModelScope.launch { recentRepo.record(packageName) }
    }

    fun removeFromRecents(packageName: String) {
        viewModelScope.launch { recentRepo.remove(packageName) }
    }

    fun syncRecents() {
        viewModelScope.launch { recentRepo.syncFromUsageStats(ctx) }
    }

    // ---------- الروت ----------

    fun requestRoot() {
        viewModelScope.launch {
            val ok = RootManager.requestRoot()
            _rootAvailable.value = ok
            if (ok && protectedRepo.current().isNotEmpty()) ProtectionService.start(ctx)
        }
    }

    suspend fun refreshRunning() {
        if (!RootManager.isGranted) return
        val installed = _apps.value.map { it.packageName }.toSet()
        _running.value = ProcessManager.getRunningApps(installed).associateBy { it.packageName }
    }

    fun forceStop(packageName: String) {
        viewModelScope.launch {
            if (!RootManager.isGranted) {
                toast(str(R.string.root_required))
                return@launch
            }
            if (packageName in protectedPkgs.value) {
                toast(str(R.string.toast_cannot_close_protected))
                return@launch
            }
            ProcessManager.forceStop(packageName)
            refreshRunning()
        }
    }

    fun killAll() {
        viewModelScope.launch {
            if (!RootManager.isGranted) {
                toast(str(R.string.root_required))
                return@launch
            }
            val protectedSet = protectedPkgs.value.toSet()
            val targets = recentPkgs.value.filter { it !in protectedSet }
            val closed = ProcessManager.killAll(targets)
            toast(ctx.getString(R.string.toast_killed, closed))
            refreshRunning()
        }
    }

    // ---------- الحماية ----------

    fun toggleProtect(packageName: String) {
        viewModelScope.launch {
            if (packageName in protectedPkgs.value) {
                protectedRepo.remove(packageName)
                if (RootManager.isGranted) OomController.unprotectApp(packageName)
            } else {
                when (protectedRepo.add(packageName)) {
                    ProtectedAppsRepository.AddResult.LIMIT_REACHED ->
                        toast(str(R.string.toast_protected_limit))
                    ProtectedAppsRepository.AddResult.ADDED -> {
                        if (RootManager.isGranted) {
                            OomController.protectApp(packageName)
                        } else {
                            toast(str(R.string.toast_no_root_protect))
                        }
                        ProtectionService.start(ctx)
                    }
                    ProtectedAppsRepository.AddResult.ALREADY -> Unit
                }
            }
        }
    }

    fun reapplyProtection() {
        viewModelScope.launch {
            if (!RootManager.isGranted) {
                toast(str(R.string.root_required))
                return@launch
            }
            for (pkg in protectedRepo.current()) OomController.protectApp(pkg)
            ProtectionService.start(ctx)
            toast(str(R.string.toast_protection_applied))
            refreshRunning()
        }
    }

    // ---------- الإعداد ----------

    fun refreshPermissions() {
        _setup.value = SetupState(
            usageAccess = PermissionUtils.hasUsageAccess(ctx),
            accessibility = PermissionUtils.isAccessibilityEnabled(ctx),
            notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        )
    }

    fun openSetup() {
        refreshPermissions()
        _showSetup.value = true
    }

    fun finishSetup() {
        _showSetup.value = false
        viewModelScope.launch {
            prefs.setSetupShown(true)
            if (_rootAvailable.value == null) _rootAvailable.value = RootManager.isRootAvailable()
        }
    }
}
