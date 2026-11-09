package app.ios.messages.security

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.view.WindowManager
import app.ios.messages.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Local tamper checks. Nothing here talks to a server: the expected signing certificate is pinned
 * at build time (BuildConfig.CERT_SHA256). These checks raise the cost of re-packaging or
 * instrumenting the app; no client-side check can stop a determined attacker who controls the device.
 */
object Integrity {
    enum class Verdict { OK, BAD_SIGNATURE, DEBUGGED, HOOKED }

    @Volatile private var signatureOk: Boolean? = null

    fun verdict(ctx: Context): Verdict {
        if (!signatureValid(ctx)) return Verdict.BAD_SIGNATURE
        if (!BuildConfig.DEBUG) {
            val debuggable = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debuggable || Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return Verdict.DEBUGGED
            if (hooked()) return Verdict.HOOKED
        }
        return Verdict.OK
    }

    private fun signatureValid(ctx: Context): Boolean {
        signatureOk?.let { return it }
        val pinned = BuildConfig.CERT_SHA256.replace(":", "").lowercase()
        val ok = if (pinned.isBlank()) true else runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners ?: return@runCatching false
            signers.isNotEmpty() && signers.all {
                val h = MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .joinToString("") { b -> "%02x".format(b) }
                MessageDigest.isEqual(h.toByteArray(), pinned.toByteArray())
            }
        }.getOrDefault(false)
        signatureOk = ok
        return ok
    }

    @Volatile private var hookCheckedAt = 0L
    @Volatile private var hookResult = false

    private fun hooked(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (hookCheckedAt != 0L && now - hookCheckedAt < 60_000L) return hookResult
        val r = scanHooks()
        hookResult = r
        hookCheckedAt = now
        return r
    }

    private fun scanHooks(): Boolean {
        if (runCatching { Class.forName("de.robv.android.xposed.XposedBridge") }.isSuccess) return true
        return runCatching {
            File("/proc/self/maps").useLines { lines ->
                lines.any { l ->
                    val s = l.lowercase()
                    "frida" in s || "gum-js" in s || "libgadget" in s || "lsposed" in s
                }
            }
        }.getOrDefault(false)
    }

    /** True when the activity window currently blocks screenshots / screen recording. */
    fun isSecure(activity: Activity): Boolean =
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}

/** Turns screenshot / screen-recording / app-switcher-preview blocking on or off for this window. */
fun Activity.applyScreenSecure(on: Boolean) {
    if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!on)
}
