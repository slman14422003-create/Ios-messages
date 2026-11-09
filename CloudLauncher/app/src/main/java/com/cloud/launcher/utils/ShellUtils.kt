package com.cloud.launcher.utils

import java.io.IOException

object ShellUtils {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    private val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    /** يمنع حقن الأوامر: لا يُسمح إلا بأسماء حزم صحيحة. */
    fun isValidPackage(name: String): Boolean = PACKAGE_REGEX.matches(name)

    fun runAsRoot(script: String, timeoutMs: Long = 20_000L): Result =
        run(arrayOf("su"), script, timeoutMs)

    fun runAsShell(script: String, timeoutMs: Long = 10_000L): Result =
        run(arrayOf("sh"), script, timeoutMs)

    private fun run(command: Array<String>, script: String, timeoutMs: Long): Result {
        return try {
            val process = ProcessBuilder(*command).start()

            val watchdog = Thread {
                try {
                    Thread.sleep(timeoutMs)
                    process.destroy()
                } catch (ignored: InterruptedException) {
                    // انتهى الأمر قبل المهلة
                }
            }
            watchdog.isDaemon = true
            watchdog.start()

            val errBuilder = StringBuilder()
            val errThread = Thread {
                try {
                    process.errorStream.bufferedReader().use { errBuilder.append(it.readText()) }
                } catch (ignored: IOException) {
                    // تجاهل
                }
            }
            errThread.isDaemon = true
            errThread.start()

            try {
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(script)
                    writer.write("\nexit\n")
                    writer.flush()
                }
            } catch (ignored: IOException) {
                // العملية أُغلقت مبكراً
            }

            val out = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            errThread.join(500)
            watchdog.interrupt()
            Result(code, out.trim(), errBuilder.toString().trim())
        } catch (e: Exception) {
            Result(-1, "", e.message ?: "shell error")
        }
    }
}
