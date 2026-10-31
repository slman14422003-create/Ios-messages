package app.ios.messages.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager

/** Thin wrapper over the Telephony provider. Column names are literals on purpose. */
class SmsRepository(private val ctx: Context) {

    private val cr get() = ctx.contentResolver
    private val smsUri: Uri = Uri.parse("content://sms")
    private val nameCache = HashMap<String, String?>()

    private fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun canRead() = has(Manifest.permission.READ_SMS)
    fun isDefault() = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
    fun clearCache() = synchronized(nameCache) { nameCache.clear() }

    fun contactName(address: String): String? = synchronized(nameCache) {
        if (nameCache.containsKey(address)) return nameCache[address]
        var result: String? = null
        if (has(Manifest.permission.READ_CONTACTS) && address.any { it.isDigit() }) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)
                )
                cr.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) result = it.getString(0) }
            } catch (_: Exception) {
            }
        }
        nameCache[address] = result
        result
    }

    fun conversations(): List<Conversation> {
        if (!canRead()) return emptyList()
        val map = LinkedHashMap<Long, Conversation>()
        val unread = HashMap<Long, Int>()
        cr.query(
            smsUri,
            arrayOf("thread_id", "address", "body", "date", "type", "read"),
            null, null, "date DESC"
        )?.use { c ->
            val iT = c.getColumnIndexOrThrow("thread_id")
            val iA = c.getColumnIndexOrThrow("address")
            val iB = c.getColumnIndexOrThrow("body")
            val iD = c.getColumnIndexOrThrow("date")
            val iType = c.getColumnIndexOrThrow("type")
            val iRead = c.getColumnIndexOrThrow("read")
            while (c.moveToNext()) {
                val tid = c.getLong(iT)
                if (c.getInt(iType) == TYPE_INBOX && c.getInt(iRead) == 0) {
                    unread[tid] = (unread[tid] ?: 0) + 1
                }
                if (!map.containsKey(tid)) {
                    val addr = c.getString(iA) ?: ""
                    map[tid] = Conversation(
                        tid, addr, contactName(addr) ?: addr,
                        c.getString(iB) ?: "", c.getLong(iD), 0
                    )
                }
            }
        }
        return map.values.map { it.copy(unread = unread[it.threadId] ?: 0) }
    }

    fun messages(threadId: Long): List<Message> {
        if (!canRead()) return emptyList()
        val out = ArrayList<Message>()
        cr.query(
            smsUri,
            arrayOf("_id", "body", "date", "type", "read"),
            "thread_id=?", arrayOf(threadId.toString()), "date ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                out += Message(
                    id = c.getLong(0),
                    body = c.getString(1) ?: "",
                    date = c.getLong(2),
                    mine = c.getInt(3) != TYPE_INBOX,
                    read = c.getInt(4) != 0
                )
            }
        }
        return out
    }

    fun threadIdFor(address: String): Long = Telephony.Threads.getOrCreateThreadId(ctx, address)

    fun send(address: String, body: String) {
        val sm = ctx.getSystemService(SmsManager::class.java)
        val parts = sm.divideMessage(body)
        if (parts.size > 1) {
            sm.sendMultipartTextMessage(address, null, parts, null, null)
        } else {
            sm.sendTextMessage(address, null, body, null, null)
        }
        // The default SMS app is responsible for writing the sent message itself.
        if (isDefault()) {
            val v = ContentValues().apply {
                put("address", address)
                put("body", body)
                put("date", System.currentTimeMillis())
                put("read", 1)
                put("type", TYPE_SENT)
            }
            try {
                cr.insert(Uri.parse("content://sms/sent"), v)
            } catch (_: Exception) {
            }
        }
    }

    fun markRead(threadId: Long) {
        if (!isDefault()) return
        val v = ContentValues().apply { put("read", 1); put("seen", 1) }
        try {
            cr.update(smsUri, v, "thread_id=? AND read=0", arrayOf(threadId.toString()))
        } catch (_: Exception) {
        }
    }

    fun deleteThread(threadId: Long) {
        if (!isDefault()) return
        try {
            cr.delete(smsUri, "thread_id=?", arrayOf(threadId.toString()))
        } catch (_: Exception) {
        }
    }

    fun deleteMessage(id: Long) {
        if (!isDefault()) return
        try {
            cr.delete(Uri.parse("content://sms/$id"), null, null)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2
    }
}
