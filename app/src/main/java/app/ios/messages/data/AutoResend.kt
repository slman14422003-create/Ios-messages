package app.ios.messages.data

import android.content.Context
import android.net.Uri
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.Executors

/** Retries recently failed SMS once the phone gets signal again. */
object AutoResend {
    private const val MAX_AGE = 24L * 60 * 60 * 1000
    private const val MAX_BATCH = 10
    private val executor = Executors.newSingleThreadExecutor()
    private var registered = false
    private var wasOut = false

    private class Listener(private val ctx: Context) : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
        override fun onServiceStateChanged(serviceState: ServiceState) {
            val inService = serviceState.state == ServiceState.STATE_IN_SERVICE
            if (!inService) {
                wasOut = true
                return
            }
            if (wasOut) {
                wasOut = false
                executor.execute { retry(ctx) }
            }
        }
    }

    @Synchronized
    fun start(ctx: Context) {
        if (registered) return
        val app = ctx.applicationContext
        try {
            val tm = app.getSystemService(TelephonyManager::class.java) ?: return
            tm.registerTelephonyCallback(executor, Listener(app))
            registered = true
        } catch (_: Throwable) {
        }
    }

    fun retry(ctx: Context) {
        if (!Prefs(ctx).autoResend) return
        val repo = SmsRepository(ctx)
        if (!repo.isDefault()) return
        val cutoff = System.currentTimeMillis() - MAX_AGE
        val pending = ArrayList<Triple<Long, String, Pair<String, Int>>>()
        try {
            ctx.contentResolver.query(
                Uri.parse("content://sms/failed"),
                arrayOf("_id", "address", "body", "sub_id"),
                "date>?", arrayOf(cutoff.toString()), "date ASC"
            )?.use { c ->
                while (c.moveToNext() && pending.size < MAX_BATCH) {
                    val address = c.getString(1) ?: continue
                    val body = c.getString(2) ?: continue
                    pending += Triple(c.getLong(0), address, body to c.getInt(3))
                }
            }
        } catch (_: Exception) {
            return
        }
        for ((id, address, rest) in pending) {
            try {
                repo.send(address, rest.first, rest.second)
                repo.deleteMessage(id)
            } catch (_: Exception) {
            }
        }
    }
}
