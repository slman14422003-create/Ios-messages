package app.ios.messages.data

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
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
    fun isDefault(): Boolean {
        val role = runCatching {
            val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
            rm.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS) && rm.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        }.getOrDefault(false)
        return role || Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
    }
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
        val list = if (!fast.isNullOrEmpty()) fast else slowConversations()
        return withSubIds(list)
    }

    /** Tags each thread with the SIM of its newest message (needs the sub_id column). */
    private fun withSubIds(list: List<Conversation>): List<Conversation> {
        if (list.isEmpty()) return list
        val subs = HashMap<Long, Int>()
        try {
            cr.query(smsUri, arrayOf("thread_id", "sub_id"), null, null, "date DESC")?.use { c ->
                while (c.moveToNext()) {
                    val t = c.getLong(0)
                    val sid = c.getInt(1)
                    if (sid >= 0 && !subs.containsKey(t)) subs[t] = sid
                }
            }
        } catch (_: Exception) {
            return list
        }
        return list.map { c -> subs[c.threadId]?.let { c.copy(subId = it) } ?: c }
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
        var out = ArrayList<Message>()
        try {
            smsMessages(threadId, true, out)
        } catch (e: Exception) {
            // Some devices have no sub_id column.
            out = ArrayList()
            smsMessages(threadId, false, out)
        }
        try {
            try {
                mmsMessages(threadId, true, out)
            } catch (e: Exception) {
                mmsMessages(threadId, false, out)
            }
        } catch (_: Exception) {
        }
        out.sortBy { it.date }
        return out
    }

    private fun smsMessages(threadId: Long, withSub: Boolean, out: MutableList<Message>) {
        val cols = if (withSub) arrayOf("_id", "body", "date", "type", "read", "sub_id")
        else arrayOf("_id", "body", "date", "type", "read")
        cr.query(
            smsUri, cols,
            "thread_id=?", arrayOf(threadId.toString()), "date DESC LIMIT 300"
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(3)
                out += Message(
                    id = c.getLong(0),
                    body = c.getString(1) ?: "",
                    date = c.getLong(2),
                    mine = type != TYPE_INBOX,
                    read = c.getInt(4) != 0,
                    subId = if (withSub) c.getInt(5) else -1,
                    failed = type == TYPE_FAILED,
                    sending = type == TYPE_OUTBOX || type == TYPE_QUEUED
                )
            }
        }
    }

    private fun mmsMessages(threadId: Long, withSub: Boolean, out: MutableList<Message>) {
        val mmsCols = if (withSub) arrayOf("_id", "date", "msg_box", "read", "sub_id")
        else arrayOf("_id", "date", "msg_box", "read")
        val added = ArrayList<Message>()
        cr.query(
            Uri.parse("content://mms"),
            mmsCols,
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
                val box = c.getInt(2)
                added += Message(
                    id = -id,
                    body = texts.joinToString("\n"),
                    date = c.getLong(1) * 1000L,
                    mine = box != 1,
                    read = c.getInt(3) != 0,
                    images = images,
                    subId = if (withSub) c.getInt(4) else -1,
                    failed = box == 5,
                    sending = box == 4
                )
            }
        }
        out += added
    }

    fun threadIdFor(address: String): Long = Telephony.Threads.getOrCreateThreadId(ctx, address)

    /** Sends an SMS through [subId] (or the system default when -1). */
    fun send(address: String, body: String, subId: Int = -1) {
        val sm = Sims.manager(ctx, subId)
        val parts = sm.divideMessage(body)
        // The default SMS app writes the message itself: first as "outbox", then the sent-receiver
        // flips it to "sent" or "failed" when the radio answers.
        var rowId = -1L
        if (isDefault()) {
            val v = ContentValues().apply {
                put("address", address)
                put("body", body)
                put("date", System.currentTimeMillis())
                put("read", 1)
                put("type", TYPE_OUTBOX)
                if (subId >= 0) put("sub_id", subId)
            }
            rowId = try {
                cr.insert(Uri.parse("content://sms/outbox"), v)?.let { ContentUris.parseId(it) } ?: -1L
            } catch (_: Exception) {
                -1L
            }
        }
        val sentIntents = ArrayList<PendingIntent?>()
        for (i in parts.indices) {
            sentIntents += if (rowId >= 0) {
                PendingIntent.getBroadcast(
                    ctx,
                    (rowId * 16 + i).toInt(),
                    Intent(ctx, app.ios.messages.SmsSentReceiver::class.java)
                        .putExtra("sms_id", rowId)
                        .putExtra("address", address)
                        .putExtra("sub_id", subId),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else null
        }
        try {
            if (parts.size > 1) {
                sm.sendMultipartTextMessage(address, null, parts, sentIntents, null)
            } else {
                sm.sendTextMessage(address, null, body, sentIntents.firstOrNull(), null)
            }
        } catch (e: Exception) {
            if (rowId >= 0) setType(rowId, TYPE_FAILED)
            throw e
        }
    }

    fun setType(id: Long, type: Int) {
        try {
            cr.update(Uri.parse("content://sms/$id"), ContentValues().apply { put("type", type) }, null, null)
        } catch (_: Exception) {
        }
    }

    fun sendMms(address: String, body: String, image: Uri?, subId: Int = -1) {
        Mms.send(ctx, address, body, image, subId)
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

    /** Marks the newest incoming message of the thread as unread (iOS "Mark as Unread"). */
    fun markUnread(threadId: Long) {
        if (!isDefault()) return
        try {
            var id = -1L
            cr.query(
                smsUri, arrayOf("_id"), "thread_id=? AND type=1", arrayOf(threadId.toString()), "date DESC LIMIT 1"
            )?.use { if (it.moveToFirst()) id = it.getLong(0) }
            if (id >= 0) {
                val v = ContentValues().apply { put("read", 0); put("seen", 0) }
                cr.update(smsUri, v, "_id=?", arrayOf(id.toString()))
            }
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
        const val TYPE_OUTBOX = 4
        const val TYPE_FAILED = 5
        const val TYPE_QUEUED = 6
    }
}
