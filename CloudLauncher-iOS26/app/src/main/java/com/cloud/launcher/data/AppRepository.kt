package com.cloud.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.cloud.launcher.utils.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            val isSystem = (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppInfo(activity.packageName, info.loadLabel(pm).toString(), isSystem)
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                false
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun openAppInfo(packageName: String) = PermissionUtils.openAppDetails(context, packageName)

    fun uninstall(packageName: String) = PermissionUtils.uninstall(context, packageName)
}
