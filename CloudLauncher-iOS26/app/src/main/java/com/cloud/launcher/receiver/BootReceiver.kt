package com.cloud.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloud.launcher.data.ProtectedAppsRepository
import com.cloud.launcher.service.ProtectionService
import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ProtectedAppsRepository(PrefsManager(appContext))
                if (repo.current().isNotEmpty()) ProtectionService.start(appContext)
            } catch (e: Exception) {
                // تجاهل: سيبدأ المستخدم الخدمة يدوياً عند فتح اللانشر
            } finally {
                pending.finish()
            }
        }
    }
}
