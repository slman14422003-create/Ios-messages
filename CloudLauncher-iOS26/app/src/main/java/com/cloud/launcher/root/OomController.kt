package com.cloud.launcher.root

import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OomController {

    /** نستعمل الرمز § بدل الدولار داخل النصوص الخام ثم نستبدله. */
    private fun sh(script: String): String = script.trimIndent().replace('§', '$')

    fun protectScript(pkg: String): String = sh(
        """
        for PID in §(pidof $pkg); do
          echo -17 > /proc/§PID/oom_adj 2>/dev/null
          echo -1000 > /proc/§PID/oom_score_adj 2>/dev/null
          renice -n -20 -p §PID >/dev/null 2>&1 || renice -20 -p §PID >/dev/null 2>&1
        done
        cmd deviceidle whitelist +$pkg >/dev/null 2>&1
        dumpsys deviceidle whitelist +$pkg >/dev/null 2>&1
        am set-standby-bucket $pkg active >/dev/null 2>&1
        """
    )

    fun unprotectScript(pkg: String): String = sh(
        """
        for PID in §(pidof $pkg); do
          echo 0 > /proc/§PID/oom_adj 2>/dev/null
          echo 0 > /proc/§PID/oom_score_adj 2>/dev/null
          renice -n 0 -p §PID >/dev/null 2>&1 || renice 0 -p §PID >/dev/null 2>&1
        done
        cmd deviceidle whitelist -$pkg >/dev/null 2>&1
        dumpsys deviceidle whitelist -$pkg >/dev/null 2>&1
        """
    )

    /** سكربت المراقبة: يعيد تطبيق oom_score_adj ويطبع "package pid" لكل تطبيق (0 = متوقف). */
    fun monitorScript(packages: List<String>): String = sh(
        """
        for PKG in ${packages.joinToString(" ")}; do
          PIDS=§(pidof §PKG)
          if [ -n "§PIDS" ]; then
            for PID in §PIDS; do
              echo -17 > /proc/§PID/oom_adj 2>/dev/null
              echo -1000 > /proc/§PID/oom_score_adj 2>/dev/null
            done
            echo "§PKG §{PIDS%% *}"
          else
            echo "§PKG 0"
          fi
        done
        """
    )

    suspend fun protectApp(pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        ShellUtils.runAsRoot(protectScript(pkg)).exitCode == 0
    }

    suspend fun unprotectApp(pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        ShellUtils.runAsRoot(unprotectScript(pkg)).exitCode == 0
    }

    /**
     * دورة مراقبة واحدة: تعيد قيم OOM لكل التطبيقات المحمية،
     * وإذا تغيّر PID (أعاد النظام تشغيله) تطبّق سكربت الحماية الكامل من جديد.
     * ترجع خريطة package -> pid (0 = التطبيق لا يعمل).
     */
    suspend fun monitorAndReprotect(
        packages: List<String>,
        lastPids: MutableMap<String, Int>
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val valid = packages.filter { ShellUtils.isValidPackage(it) }
        if (valid.isEmpty()) return@withContext emptyMap<String, Int>()
        val out = ShellUtils.runAsRoot(monitorScript(valid)).stdout
        val current = LinkedHashMap<String, Int>()
        for (line in out.lines()) {
            val parts = line.trim().split(" ")
            if (parts.size == 2) {
                val pid = parts[1].toIntOrNull()
                if (pid != null && parts[0] in valid) current[parts[0]] = pid
            }
        }
        for ((pkg, pid) in current) {
            if (pid > 0 && lastPids[pkg] != pid) {
                ShellUtils.runAsRoot(protectScript(pkg))
            }
            lastPids[pkg] = pid
        }
        lastPids.keys.retainAll(valid.toSet())
        current
    }

    /** يعيد تشغيل تطبيق محمي توقف. */
    suspend fun relaunchApp(pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        ShellUtils.runAsRoot(
            "monkey -p $pkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"
        ).exitCode == 0
    }
}
