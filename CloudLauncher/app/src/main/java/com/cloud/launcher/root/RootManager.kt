package com.cloud.launcher.root

import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootManager {

    @Volatile
    private var granted: Boolean = false

    /** آخر حالة معروفة للروت دون تشغيل أي أمر. */
    val isGranted: Boolean get() = granted

    /** يتحقق من توفر الروت عبر `su -c id`. */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot("id", 30_000L)
        val ok = result.stdout.contains("uid=0")
        granted = ok
        ok
    }

    /** يطلب صلاحية الروت لأول مرة (تظهر نافذة Magisk للمستخدم). */
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot("id", 60_000L)
        val ok = result.stdout.contains("uid=0")
        granted = ok
        ok
    }

    suspend fun executeCommand(cmd: String): String = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot(cmd)
        if (result.isSuccess || result.stdout.isNotEmpty()) {
            result.stdout
        } else {
            val reason = if (result.stderr.isNotEmpty()) result.stderr else "exit ${result.exitCode}"
            "ERROR: $reason"
        }
    }

    suspend fun executeCommands(cmds: List<String>): String =
        executeCommand(cmds.joinToString("\n"))
}
