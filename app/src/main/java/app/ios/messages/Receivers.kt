package app.ios.messages

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony

/** Incoming SMS (only delivered here while we are the default SMS app). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val address = msgs[0].displayOriginatingAddress ?: return
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }
        val v = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("date", System.currentTimeMillis())
            put("date_sent", msgs[0].timestampMillis)
            put("read", 0)
            put("seen", 0)
            put("type", 1)
        }
        try {
            context.contentResolver.insert(Uri.parse("content://sms/inbox"), v)
        } catch (_: Exception) {
        }
        Notifier.show(context, address, body)
    }
}

/** Required by the default-SMS-app role. MMS download/parsing is not implemented. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

/** Required by the default-SMS-app role ("respond via message" from the dialer). */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
