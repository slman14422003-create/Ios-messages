package com.cloud.launcher.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cloud.launcher.MainActivity
import com.cloud.launcher.R
import com.cloud.launcher.data.ProtectedAppsRepository
import com.cloud.launcher.root.OomController
import com.cloud.launcher.root.RootManager
import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProtectionService : Service() {

    companion object {
        private const val TAG = "ProtectionService"
        private const val CHANNEL_ID = "cloud_protection"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVAL_MS = 3_000L
        private const val RELAUNCH_COOLDOWN_MS = 30_000L
        private const val ROOT_RETRY_MS = 15_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ProtectionService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start protection service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ProtectionService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop protection service", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notif_channel))
                .build()
        )
        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        val repo = ProtectedAppsRepository(PrefsManager(applicationContext))
        val lastPids = HashMap<String, Int>()
        val deadChecks = HashMap<String, Int>()
        val lastRelaunch = HashMap<String, Long>()
        var shownCount = -1
        var rootOk = false

        while (scope.isActive) {
            try {
                val packages = repo.current()
                if (packages.isEmpty()) {
                    stopSelf()
                    return
                }
                if (packages.size != shownCount) {
                    shownCount = packages.size
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification(shownCount))
                }

                if (!rootOk) rootOk = RootManager.isRootAvailable()
                if (!rootOk) {
                    delay(ROOT_RETRY_MS)
                    continue
                }

                val state = OomController.monitorAndReprotect(packages, lastPids)
                val now = System.currentTimeMillis()
                for (pkg in packages) {
                    val pid = state[pkg] ?: continue
                    if (pid > 0) {
                        deadChecks[pkg] = 0
                    } else {
                        val dead = (deadChecks[pkg] ?: 0) + 1
                        deadChecks[pkg] = dead
                        val last = lastRelaunch[pkg] ?: 0L
                        if (dead >= 2 && now - last >= RELAUNCH_COOLDOWN_MS) {
                            lastRelaunch[pkg] = now
                            OomController.relaunchApp(pkg)
                        }
                    }
                }
                deadChecks.keys.retainAll(packages.toSet())
            } catch (e: Exception) {
                Log.w(TAG, "monitor iteration failed", e)
            }
            delay(INTERVAL_MS)
        }
    }

    private fun buildNotification(count: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cloud)
            .setContentTitle(getString(R.string.notif_title, count))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
