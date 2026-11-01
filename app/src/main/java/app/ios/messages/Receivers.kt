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
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import app.ios.messages.data.SmsRepository

/** The SIM an incoming broadcast arrived on (-1 when the system does not say). */
internal fun subIdOf(intent: Intent): Int {
    val a = intent.getIntExtra("subscription", -1)
    if (a >= 0) return a
    return intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
}

/** Incoming SMS (only delivered here while we are the default SMS app). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val address = msgs[0].displayOriginatingAddress ?: return
        if (app.ios.messages.data.Blocking.isBlocked(context, address)) return
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }
        val sub = subIdOf(intent)
        val v = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("date", System.currentTimeMillis())
            put("date_sent", msgs[0].timestampMillis)
            put("read", 0)
            put("seen", 0)
            put("type", 1)
            if (sub >= 0) put("sub_id", sub)
        }
        try {
            context.contentResolver.insert(Uri.parse("content://sms/inbox"), v)
        } catch (_: Exception) {
        }
        Notifier.show(context, address, body, sub)
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        try {
            Mms.download(context, data, subIdOf(intent))
        } catch (_: Exception) {
        }
    }
}

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val id = intent.getLongExtra("mms_id", -1L)
        if (id < 0) return
        Mms.setBox(context, id, if (resultCode == Activity.RESULT_OK) 2 else 5)
        intent.getStringExtra("file")?.let { File(it).delete() }
    }
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val path = intent.getStringExtra("file") ?: return
        val from = intent.getStringExtra("from") ?: ""
        val ok = resultCode == Activity.RESULT_OK
        val sub = intent.getIntExtra("sub_id", -1)
        val pending = goAsync()
        Thread {
            try {
                Mms.onDownloaded(context, path, from, ok, sub)?.let { Notifier.show(context, it.first, it.second, sub) }
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

/** Radio answered for an outgoing SMS: flip the outbox row to sent / failed. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val id = intent.getLongExtra("sms_id", -1L)
        if (id < 0) return
        val ok = resultCode == Activity.RESULT_OK
        val cr = context.contentResolver
        val uri = Uri.parse("content://sms/$id")
        try {
            if (ok) {
                cr.update(uri, ContentValues().apply { put("type", SmsRepository.TYPE_SENT) }, "type=4", null)
            } else {
                cr.update(uri, ContentValues().apply { put("type", SmsRepository.TYPE_FAILED) }, null, null)
                val address = intent.getStringExtra("address") ?: return
                Notifier.showFailed(context, address, intent.getIntExtra("sub_id", -1))
            }
        } catch (_: Exception) {
        }
    }
}

/** "Copy code" action on verification-code notifications. */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val code = intent.getStringExtra("code") ?: return
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("Verification code", code)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        cm.setPrimaryClip(clip)
        Toast.makeText(context, Lang.wrap(context).getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
        context.getSystemService(android.app.NotificationManager::class.java)
            .cancel(intent.getIntExtra("notif_id", 0))
    }
}

class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val tid = intent.getLongExtra("thread_id", -1L)
        val address = intent.getStringExtra("address") ?: return
        val pending = goAsync()
        Thread {
            try {
                if (tid >= 0) SmsRepository(context).markRead(tid)
                Notifier.cancel(context, address)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        intent.getStringExtra("address")?.let { Notifier.forget(it) }
    }
}

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "receiver failed", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address") ?: return
        val text = android.app.RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val sub = intent.getIntExtra("sub_id", -1)
        val tid = intent.getLongExtra("thread_id", -1L)
        val pending = goAsync()
        Thread {
            var ok = true
            try {
                val repo = SmsRepository(context)
                repo.send(address, text, sub)
                if (tid >= 0) repo.markRead(tid)
            } catch (_: Throwable) {
                ok = false
            }
            try {
                Notifier.showReplyResult(context, address, text, ok)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
