package com.cloud.launcher.root

import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RunningApp(val packageName: String, val pid: Int, val rssKb: Long) {
    val rssMb: Long get() = rssKb / 1024L
}

object ProcessManager {

    private const val PS_COMMAND = "ps -A -o PID,RSS,NAME 2>/dev/null || ps"
    private val WHITESPACE = Regex("\\s+")

    suspend fun getRunningApps(installed: Set<String>): List<RunningApp> = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot(PS_COMMAND)
        parsePs(result.stdout, installed)
    }

    /**
     * يحلل مخرجات ps بصيغتين:
     * - الحديثة (toybox): PID RSS NAME
     * - القديمة: USER PID PPID VSIZE RSS WCHAN PC S NAME
     */
    fun parsePs(output: String, installed: Set<String>): List<RunningApp> {
        val pids = HashMap<String, Int>()
        val rss = HashMap<String, Long>()
        for (line in output.lineSequence()) {
            val t = line.trim().split(WHITESPACE)
            val pid: Int?
            val mem: Long?
            val name: String
            if (t.size == 3) {
                pid = t[0].toIntOrNull()
                mem = t[1].toLongOrNull()
                name = t[2]
            } else if (t.size >= 8) {
                pid = t[1].toIntOrNull()
                mem = t[4].toLongOrNull()
                name = t[t.size - 1]
            } else {
                continue
            }
            if (pid == null || mem == null) continue
            val pkg = name.substringBefore(':')
            if (pkg !in installed) continue
            rss[pkg] = (rss[pkg] ?: 0L) + mem
            if (name == pkg || !pids.containsKey(pkg)) pids[pkg] = pid
        }
        return pids.map { RunningApp(it.key, it.value, rss[it.key] ?: 0L) }
    }

    suspend fun getPid(packageName: String): Int? = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(packageName)) return@withContext null
        val direct = ShellUtils.runAsRoot("pidof $packageName").stdout
            .trim().split(WHITESPACE).firstOrNull()?.toIntOrNull()
        if (direct != null) {
            direct
        } else {
            val ps = ShellUtils.runAsRoot(PS_COMMAND).stdout
            parsePs(ps, setOf(packageName)).firstOrNull()?.pid
        }
    }

    fun forceStopScript(packageName: String): String =
        "am force-stop $packageName\nam kill $packageName"

    fun killAllScript(packages: List<String>): String {
        val valid = packages.filter { ShellUtils.isValidPackage(it) }
        val sb = StringBuilder()
        sb.append("for pkg in ").append(valid.joinToString(" ")).append("; do\n")
        sb.append("  am force-stop \$pkg\n")
        sb.append("  am kill \$pkg\n")
        sb.append("done")
        return sb.toString()
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(packageName)) return@withContext false
        ShellUtils.runAsRoot(forceStopScript(packageName)).exitCode == 0
    }

    suspend fun killAll(packages: List<String>): Int = withContext(Dispatchers.IO) {
        val valid = packages.filter { ShellUtils.isValidPackage(it) }
        if (valid.isEmpty()) return@withContext 0
        val result = ShellUtils.runAsRoot(killAllScript(valid))
        if (result.exitCode == 0) valid.size else 0
    }
}
