package app.ios.messages

import android.app.Activity
import android.app.Service
import app.ios.messages.data.Mms
import java.io.File
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

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        try {
            Mms.download(context, data)
        } catch (_: Exception) {
        }
    }
}

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("mms_id", -1L)
        if (id < 0) return
        Mms.setBox(context, id, if (resultCode == Activity.RESULT_OK) 2 else 5)
        intent.getStringExtra("file")?.let { File(it).delete() }
    }
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra("file") ?: return
        val from = intent.getStringExtra("from") ?: ""
        val ok = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        Thread {
            try {
                Mms.onDownloaded(context, path, from, ok)?.let { Notifier.show(context, it.first, it.second) }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Required by the default-SMS-app role ("respond via message" from the dialer). */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
