package com.iosuite.core

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.IntentCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Explicit broadcasts between the suite's apps.
 *
 * The apps are not signed with one key (Messages has its own), so a signature permission cannot work.
 * Instead every message carries a PendingIntent: the system reports the package that created it
 * (it cannot be forged), and the receiver accepts it only when that package is one of the three suite
 * packages AND is signed with a certificate we know (the same one as ours, or one of the two pinned below).
 */
object SuiteBridge {
    const val PKG_MESSAGES = "app.ios.messages"
    const val PKG_PHONE = "com.iosphone.app"
    const val PKG_CONTACTS = "com.iosphone.contacts"

    const val ACTION_MISSED_CALL = "com.iosuite.action.MISSED_CALL"
    const val ACTION_READ_STATE = "com.iosuite.action.READ_STATE"
    const val ACTION_ABSORBED = "com.iosuite.action.ABSORBED"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_NAME = "name"
    const val EXTRA_COUNT = "count"
    const val EXTRA_TIME = "time"
    const val EXTRA_KIND = "kind"

    /** READ_STATE kinds. */
    const val KIND_MISSED_READ = "missed_read"
    const val KIND_MISSED_ALL_READ = "missed_all_read"

    private val SUITE = setOf(PKG_MESSAGES, PKG_PHONE, PKG_CONTACTS)

    /** SHA-256 of the release certificates of Messages (app/release.jks) and Phone + Contacts (keystore/release.jks). */
    private val PINNED = listOf(
        "C16DDD8ADAFDEC27F547A8126F22183F2E1D1E3E76439DCABCB2A71B96EF938A",
        "04D9E0A4F519ED3FB122B5401007E07AE0F721BC51E4B7DBF87C5B07081CB346",
    )

    interface Handler {
        /** Phone told Messages about a missed call. Return true when it was merged into a Messages notification. */
        fun onMissedCall(context: Context, number: String, count: Int, whenMs: Long, name: String?): Boolean = false
        fun onReadState(context: Context, kind: String, number: String?) {}
        fun onAbsorbed(context: Context, number: String) {}
    }

    @Volatile var handler: Handler? = null

    fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** True when [pkg] is a suite package signed with a key we trust. */
    fun trusted(context: Context, pkg: String?): Boolean {
        if (pkg == null || pkg !in SUITE) return false
        val pm = context.packageManager
        if (runCatching { pm.checkSignatures(context.packageName, pkg) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)) return true
        return PINNED.any {
            runCatching { pm.hasSigningCertificate(pkg, hex(it), PackageManager.CERT_INPUT_SHA256) }.getOrDefault(false)
        }
    }

    /** Peer is installed and passes the signature check: the only case in which a message is sent. */
    fun peerReady(context: Context, pkg: String): Boolean =
        pkg != context.packageName && installed(context, pkg) && trusted(context, pkg)

    private fun build(context: Context, pkg: String, action: String, fill: Intent.() -> Unit): Intent {
        val token = PendingIntent.getBroadcast(
            context, 0, Intent("com.iosuite.action.TOKEN").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE
        )
        return Intent(action)
            .setComponent(ComponentName(pkg, SuiteReceiver::class.java.name))
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(EXTRA_TOKEN, token)
            .apply(fill)
    }

    /** Fire and forget. Does nothing when the peer is missing or not trusted. */
    fun send(context: Context, pkg: String, action: String, fill: Intent.() -> Unit = {}) {
        if (!peerReady(context, pkg)) return
        runCatching { context.sendBroadcast(build(context, pkg, action, fill)) }
    }

    /**
     * Sends an ordered broadcast and waits (at most [timeoutMs]) for the receiver's answer:
     * true when the receiver answered RESULT_OK. Never call this on the main thread (it then returns false at once).
     */
    fun sendOrdered(context: Context, pkg: String, action: String, timeoutMs: Long = 2500L, fill: Intent.() -> Unit = {}): Boolean {
        if (!peerReady(context, pkg)) return false
        val intent = build(context, pkg, action, fill)
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        if (onMain) return false
        val latch = CountDownLatch(1)
        var ok = false
        val result = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                ok = resultCode == Activity.RESULT_OK
                latch.countDown()
            }
        }
        val sent = runCatching {
            context.sendOrderedBroadcast(intent, null, result, Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null)
        }.isSuccess
        if (!sent) return false
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return ok
    }
}

/** Single exported receiver of the suite: checks who sent the message, then hands it to the app's [SuiteBridge.Handler]. */
class SuiteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val token = IntentCompat.getParcelableExtra(intent, SuiteBridge.EXTRA_TOKEN, PendingIntent::class.java)
        val sender = token?.creatorPackage
        if (!SuiteBridge.trusted(app, sender)) {
            if (isOrderedBroadcast) resultCode = Activity.RESULT_CANCELED
            return
        }
        val h = SuiteBridge.handler
        if (h == null) {
            if (isOrderedBroadcast) resultCode = Activity.RESULT_CANCELED
            return
        }
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    SuiteBridge.ACTION_MISSED_CALL -> {
                        val number = intent.getStringExtra(SuiteBridge.EXTRA_NUMBER).orEmpty()
                        val handled = number.isNotBlank() && h.onMissedCall(
                            app, number,
                            intent.getIntExtra(SuiteBridge.EXTRA_COUNT, 1),
                            intent.getLongExtra(SuiteBridge.EXTRA_TIME, System.currentTimeMillis()),
                            intent.getStringExtra(SuiteBridge.EXTRA_NAME),
                        )
                        pending.resultCode = if (handled) Activity.RESULT_OK else Activity.RESULT_CANCELED
                    }
                    SuiteBridge.ACTION_READ_STATE -> h.onReadState(
                        app,
                        intent.getStringExtra(SuiteBridge.EXTRA_KIND).orEmpty(),
                        intent.getStringExtra(SuiteBridge.EXTRA_NUMBER),
                    )
                    SuiteBridge.ACTION_ABSORBED -> intent.getStringExtra(SuiteBridge.EXTRA_NUMBER)?.let { h.onAbsorbed(app, it) }
                }
            } catch (_: Throwable) {
                if (isOrderedBroadcast) pending.resultCode = Activity.RESULT_CANCELED
            } finally {
                pending.finish()
            }
        }.start()
    }
}
