package app.ios.messages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashItem(
    val id: String,
    val address: String,
    val name: String,
    val snippet: String,
    val deletedAt: Long,
    val count: Int
)

/** "Recently Deleted": SMS threads are copied here before deletion and kept for 30 days. */
object TrashStore {
    private const val DAY = 24L * 60 * 60 * 1000
    private const val KEEP_DAYS = 30

    private fun file(ctx: Context) = File(ctx.filesDir, "trash.json")

    private fun readAll(ctx: Context): JSONArray =
        runCatching { JSONArray(file(ctx).readText()) }.getOrDefault(JSONArray())

    private fun writeAll(ctx: Context, arr: JSONArray) {
        runCatching { file(ctx).writeText(arr.toString()) }
    }

    fun daysLeft(item: TrashItem): Int =
        (KEEP_DAYS - (System.currentTimeMillis() - item.deletedAt) / DAY).toInt().coerceIn(0, KEEP_DAYS)

    @Synchronized
    fun list(ctx: Context): List<TrashItem> {
        val now = System.currentTimeMillis()
        val all = readAll(ctx)
        val keep = JSONArray()
        val out = ArrayList<TrashItem>()
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            val at = o.optLong("at")
            if (now - at > KEEP_DAYS * DAY) continue
            keep.put(o)
            out += TrashItem(
                o.optString("id"), o.optString("address"), o.optString("name"),
                o.optString("snippet"), at, o.optJSONArray("msgs")?.length() ?: 0
            )
        }
        if (keep.length() != all.length()) writeAll(ctx, keep)
        return out.sortedByDescending { it.deletedAt }
    }

    /** Copies the SMS of a thread into the trash. Returns false when there is nothing restorable (e.g. MMS only). */
    @Synchronized
    fun put(ctx: Context, threadId: Long, name: String): Boolean = runCatching {
        val msgs = JSONArray()
        var address = ""
        var snippet = ""
        ctx.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf("address", "body", "date", "date_sent", "type", "read"),
            "thread_id=?", arrayOf(threadId.toString()), "date ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(4)
                if (type != 1 && type != 2 && type != 5) continue
                if (address.isEmpty()) address = c.getString(0) ?: ""
                snippet = c.getString(1) ?: ""
                msgs.put(
                    JSONObject()
                        .put("a", c.getString(0) ?: "")
                        .put("b", c.getString(1) ?: "")
                        .put("d", c.getLong(2))
                        .put("s", c.getLong(3))
                        .put("t", type)
                        .put("r", c.getInt(5))
                )
            }
        }
        if (msgs.length() == 0) return@runCatching false
        val all = readAll(ctx)
        all.put(
            JSONObject()
                .put("id", java.util.UUID.randomUUID().toString())
                .put("address", address)
                .put("name", name.ifBlank { address })
                .put("snippet", snippet)
                .put("at", System.currentTimeMillis())
                .put("msgs", msgs)
        )
        writeAll(ctx, all)
        true
    }.getOrDefault(false)

    @Synchronized
    fun purge(ctx: Context, id: String) {
        val all = readAll(ctx)
        val keep = JSONArray()
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if (o.optString("id") != id) keep.put(o)
        }
        writeAll(ctx, keep)
    }

    /** Puts the messages back into the system SMS store (needs the default SMS app role). */
    @Synchronized
    fun restore(ctx: Context, id: String): Int {
        val all = readAll(ctx)
        var entry: JSONObject? = null
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i)
            if (o != null && o.optString("id") == id) entry = o
        }
        val msgs = entry?.optJSONArray("msgs") ?: return 0
        val cr = ctx.contentResolver
        var added = 0
        for (i in 0 until msgs.length()) {
            val m = msgs.optJSONObject(i) ?: continue
            val t = m.optInt("t", 1)
            val uri = when (t) {
                1 -> "content://sms/inbox"
                2 -> "content://sms/sent"
                else -> "content://sms/failed"
            }
            val v = ContentValues().apply {
                put("address", m.optString("a"))
                put("body", m.optString("b"))
                put("date", m.optLong("d"))
                put("date_sent", m.optLong("s", 0L))
                put("read", 1)
                put("seen", 1)
                put("type", t)
            }
            try {
                if (cr.insert(Uri.parse(uri), v) != null) added++
            } catch (_: Exception) {
            }
        }
        if (added > 0) purge(ctx, id)
        return added
    }
}
