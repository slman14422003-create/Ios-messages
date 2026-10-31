package app.ios.messages.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
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
        val fast = try { fastConversations() } catch (e: Exception) { null }
        if (!fast.isNullOrEmpty()) return fast
        return slowConversations()
    }

    /** Uses the thread table (one row per conversation) instead of scanning every SMS. */
    private fun fastConversations(): List<Conversation> {
        val addrs = HashMap<Long, String>()
        cr.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)
            ?.use { c -> while (c.moveToNext()) addrs[c.getLong(0)] = c.getString(1) ?: "" }
        val unread = HashMap<Long, Int>()
        cr.query(smsUri, arrayOf("thread_id"), "read=0 AND type=1", null, null)
            ?.use { c -> while (c.moveToNext()) { val t = c.getLong(0); unread[t] = (unread[t] ?: 0) + 1 } }
        try {
            cr.query(Uri.parse("content://mms"), arrayOf("thread_id"), "read=0 AND msg_box=1 AND m_type=132", null, null)
                ?.use { c -> while (c.moveToNext()) { val t = c.getLong(0); unread[t] = (unread[t] ?: 0) + 1 } }
        } catch (_: Exception) {
        }
        val out = ArrayList<Conversation>()
        cr.query(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf("_id", "date", "recipient_ids", "snippet"),
            null, null, "date DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val tid = c.getLong(0)
                val list = (c.getString(2) ?: "").trim().split(" ")
                    .mapNotNull { it.toLongOrNull() }.mapNotNull { addrs[it] }
                if (list.isEmpty()) continue
                out += Conversation(
                    tid, list[0], list.joinToString(", ") { contactName(it) ?: it },
                    (c.getString(3) ?: "").ifBlank { "Attachment" }, c.getLong(1), unread[tid] ?: 0,
                    contactName(list[0]) != null
                )
            }
        }
        return out
    }

    private fun slowConversations(): List<Conversation> {
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
                        c.getString(iB) ?: "", c.getLong(iD), 0,
                        contactName(addr) != null
                    )
                }
            }
        }
        return map.values.map { it.copy(unread = unread[it.threadId] ?: 0) }
    }

    fun contacts(): List<Contact> {
        if (!has(Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        try {
            cr.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER),
                null, null, Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    val num = c.getString(2) ?: continue
                    val key = c.getLong(0).toString() + "|" + num.filter { it.isDigit() }.takeLast(9)
                    if (seen.add(key)) out += Contact(c.getLong(0), name, num.trim())
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun messages(threadId: Long): List<Message> {
        if (!canRead()) return emptyList()
        val out = ArrayList<Message>()
        cr.query(
            smsUri,
            arrayOf("_id", "body", "date", "type", "read"),
            "thread_id=?", arrayOf(threadId.toString()), "date DESC LIMIT 300"
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
        try {
            mmsMessages(threadId, out)
        } catch (_: Exception) {
        }
        out.sortBy { it.date }
        return out
    }

    private fun mmsMessages(threadId: Long, out: MutableList<Message>) {
        cr.query(
            Uri.parse("content://mms"),
            arrayOf("_id", "date", "msg_box", "read"),
            "thread_id=? AND (m_type=128 OR m_type=132)", arrayOf(threadId.toString()), "date DESC LIMIT 100"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val texts = ArrayList<String>()
                val images = ArrayList<String>()
                cr.query(
                    Uri.parse("content://mms/part"),
                    arrayOf("_id", "ct", "text"),
                    "mid=?", arrayOf(id.toString()), null
                )?.use { pc ->
                    while (pc.moveToNext()) {
                        val ct = pc.getString(1) ?: ""
                        when {
                            ct == "text/plain" -> pc.getString(2)?.let { texts += it }
                            ct.startsWith("image/") -> images += "content://mms/part/" + pc.getLong(0)
                        }
                    }
                }
                out += Message(
                    id = -id,
                    body = texts.joinToString("\n"),
                    date = c.getLong(1) * 1000L,
                    mine = c.getInt(2) != 1,
                    read = c.getInt(3) != 0,
                    images = images
                )
            }
        }
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

    fun sendMms(address: String, body: String, image: Uri?) {
        Mms.send(ctx, address, body, image)
    }

    fun markRead(threadId: Long) {
        if (!isDefault()) return
        val v = ContentValues().apply { put("read", 1); put("seen", 1) }
        try {
            cr.update(smsUri, v, "thread_id=? AND read=0", arrayOf(threadId.toString()))
        } catch (_: Exception) {
        }
        try {
            cr.update(Uri.parse("content://mms"), v, "thread_id=? AND read=0", arrayOf(threadId.toString()))
        } catch (_: Exception) {
        }
    }

    fun deleteThread(threadId: Long) {
        if (!isDefault()) return
        try {
            cr.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null)
        } catch (_: Exception) {
        }
    }

    fun deleteMessage(id: Long) {
        if (!isDefault()) return
        try {
            if (id < 0) cr.delete(Uri.parse("content://mms/${-id}"), null, null)
            else cr.delete(Uri.parse("content://sms/$id"), null, null)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2
    }
}
