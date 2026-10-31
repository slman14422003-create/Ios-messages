package app.ios.messages.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import app.ios.messages.MmsDownloadedReceiver
import app.ios.messages.MmsSentReceiver
import java.io.ByteArrayOutputStream
import java.io.File

private class PduOut {
    val o = ByteArrayOutputStream()

    fun b(v: Int) = o.write(v)

    fun bytes(a: ByteArray) = o.write(a)

    fun text(s: String) {
        val a = s.toByteArray(Charsets.UTF_8)
        if (a.isNotEmpty() && (a[0].toInt() and 0xFF) >= 0x80) o.write(0x7F)
        o.write(a)
        o.write(0)
    }

    fun uintvar(v: Int) {
        var x = v ushr 7
        val tmp = ArrayList<Int>()
        tmp.add(v and 0x7F)
        while (x > 0) {
            tmp.add((x and 0x7F) or 0x80)
            x = x ushr 7
        }
        for (i in tmp.indices.reversed()) o.write(tmp[i])
    }

    fun toBytes(): ByteArray = o.toByteArray()
}

private class PduIn(val d: ByteArray, var p: Int = 0) {
    fun more() = p < d.size

    fun u8(): Int = d[p++].toInt() and 0xFF

    fun peek(): Int = d[p].toInt() and 0xFF

    fun uintvar(): Int {
        var r = 0
        while (p < d.size) {
            val b = u8()
            r = (r shl 7) or (b and 0x7F)
            if (b and 0x80 == 0) break
        }
        return r
    }

    fun text(): String {
        if (p < d.size && peek() == 0x7F) p++
        val s = p
        while (p < d.size && d[p].toInt() != 0) p++
        val r = String(d, s, p - s, Charsets.UTF_8)
        if (p < d.size) p++
        return r
    }

    fun value(): ByteArray {
        val b = peek()
        if (b >= 0x80) {
            p++
            return byteArrayOf(b.toByte())
        }
        if (b == 0x1F) {
            p++
            val len = uintvar()
            return take(len)
        }
        if (b <= 0x1E) {
            p++
            return take(b)
        }
        return text().toByteArray(Charsets.UTF_8)
    }

    private fun take(len: Int): ByteArray {
        val end = minOf(d.size, p + len)
        val r = d.copyOfRange(p, end)
        p = end
        return r
    }
}

class MmsPart(val type: String, val name: String?, val data: ByteArray)

class MmsNotification(val location: String?, val from: String?)

class MmsRetrieved(val from: String?, val date: Long, val parts: List<MmsPart>)

object Mms {
    private val wspTypes = arrayOf(
        "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml", "text/x-vCalendar",
        "text/x-vCard", "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event",
        "multipart/*", "multipart/mixed", "multipart/form-data", "multipart/byteranges",
        "multipart/alternative", "application/*", "application/java-vm",
        "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc",
        "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof",
        "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate",
        "application/x-x509-ca-cert", "application/x-x509-user-cert", "image/*", "image/gif",
        "image/jpeg", "image/tiff", "image/png", "image/vnd.wap.wbmp", "application/vnd.wap.multipart.*",
        "application/vnd.wap.multipart.mixed", "application/vnd.wap.multipart.form-data",
        "application/vnd.wap.multipart.byteranges", "application/vnd.wap.multipart.alternative",
        "application/xml", "text/xml", "application/vnd.wap.wbxml", "application/x-x968-cross-cert-request",
        "application/x-x968-ca-cert", "application/x-x968-user-cert", "text/vnd.wap.si",
        "application/vnd.wap.sic", "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co",
        "application/vnd.wap.coc", "application/vnd.wap.multipart.related"
    )

    private fun typeOf(raw: ByteArray): String {
        if (raw.isEmpty()) return "application/octet-stream"
        val f = raw[0].toInt() and 0xFF
        if (f >= 0x80) return wspTypes.getOrNull(f and 0x7F) ?: "application/octet-stream"
        val end = raw.indexOf(0.toByte()).let { if (it < 0) raw.size else it }
        return String(raw, 0, end, Charsets.UTF_8).lowercase()
    }

    private fun addrOf(v: ByteArray): String? {
        if (v.isEmpty() || (v[0].toInt() and 0xFF) != 0x80) return null
        var s = String(v, 1, v.size - 1, Charsets.UTF_8).trimEnd('\u0000')
        val i = s.indexOf("/TYPE")
        if (i >= 0) s = s.substring(0, i)
        return s.ifBlank { null }
    }

    private fun longOf(v: ByteArray): Long {
        if (v.size == 1 && (v[0].toInt() and 0xFF) >= 0x80) return (v[0].toInt() and 0x7F).toLong()
        var r = 0L
        for (x in v) r = (r shl 8) or (x.toLong() and 0xFF)
        return r
    }

    fun parseNotification(pdu: ByteArray): MmsNotification? {
        return try {
            val r = PduIn(pdu)
            var loc: String? = null
            var from: String? = null
            while (r.more()) {
                val h = r.peek()
                if (h < 0x80) break
                r.p++
                when (h) {
                    0x8C -> r.u8()
                    0x98 -> r.text()
                    0x83 -> loc = r.text()
                    0x89 -> from = addrOf(r.value())
                    else -> r.value()
                }
            }
            MmsNotification(loc, from)
        } catch (e: Exception) {
            null
        }
    }

    fun parseRetrieve(pdu: ByteArray): MmsRetrieved? {
        return try {
            val r = PduIn(pdu)
            var from: String? = null
            var date = 0L
            while (r.more()) {
                val h = r.peek()
                if (h < 0x80) break
                r.p++
                when (h) {
                    0x84 -> {
                        r.value()
                        return MmsRetrieved(from, date, parts(r))
                    }
                    0x89 -> from = addrOf(r.value())
                    0x85 -> date = longOf(r.value())
                    0x98 -> r.text()
                    else -> r.value()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parts(r: PduIn): List<MmsPart> {
        val out = ArrayList<MmsPart>()
        val n = r.uintvar()
        repeat(n) {
            val hl = r.uintvar()
            val dl = r.uintvar()
            val hs = r.p
            val he = hs + hl
            val sub = PduIn(r.d, hs)
            val type = typeOf(sub.value())
            var name: String? = null
            while (sub.p < he) {
                val hb = sub.peek()
                if (hb < 0x80) break
                sub.p++
                when (hb) {
                    0x8E -> name = sub.text()
                    0xC0 -> sub.text()
                    else -> sub.value()
                }
            }
            val end = minOf(r.d.size, he + dl)
            out += MmsPart(type, name, r.d.copyOfRange(he, end))
            r.p = end
        }
        return out
    }

    fun buildSendReq(to: String, text: String?, image: ByteArray?): ByteArray {
        val h = PduOut()
        h.b(0x8C); h.b(0x80)
        h.b(0x98); h.text("T" + java.lang.Long.toHexString(System.nanoTime()))
        h.b(0x8D); h.b(0x92)
        h.b(0x89); h.b(0x01); h.b(0x81)
        h.b(0x97); h.text("$to/TYPE=PLMN")
        h.b(0x86); h.b(0x81)
        h.b(0x90); h.b(0x81)
        h.b(0x8F); h.b(0x81)
        h.b(0x84); h.b(0xA3)

        val bodyParts = ArrayList<Pair<ByteArray, ByteArray>>()
        if (!text.isNullOrBlank()) {
            val ph = PduOut()
            ph.b(0x03); ph.b(0x83); ph.b(0x81); ph.b(0xEA)
            ph.b(0x8E); ph.text("text_0.txt")
            bodyParts += ph.toBytes() to text.toByteArray(Charsets.UTF_8)
        }
        if (image != null) {
            val ph = PduOut()
            ph.b(0x9E)
            ph.b(0x8E); ph.text("image_0.jpg")
            bodyParts += ph.toBytes() to image
        }
        h.uintvar(bodyParts.size)
        for ((hd, data) in bodyParts) {
            h.uintvar(hd.size)
            h.uintvar(data.size)
            h.bytes(hd)
            h.bytes(data)
        }
        return h.toBytes()
    }

    fun compressImage(ctx: Context, uri: Uri, maxSide: Int = 1280, maxBytes: Int = 300 * 1024): ByteArray {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp: Bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("decode")
        val orientation = cr.openInputStream(uri)?.use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        } ?: 1
        val deg = when (orientation) {
            6 -> 90f
            3 -> 180f
            8 -> 270f
            else -> 0f
        }
        val longest = maxOf(bmp.width, bmp.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        if (scale < 1f || deg != 0f) {
            val m = Matrix().apply {
                postScale(scale, scale)
                postRotate(deg)
            }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        var q = 85
        var tries = 0
        while (true) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            tries++
            if (out.size() <= maxBytes || tries > 14) return out.toByteArray()
            if (q > 45) {
                q -= 10
            } else {
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * 0.8f).toInt().coerceAtLeast(64),
                    (bmp.height * 0.8f).toInt().coerceAtLeast(64),
                    true
                )
            }
        }
    }

    private fun grant(ctx: Context, uri: Uri, flags: Int) {
        for (pkg in listOf("com.android.phone", "com.android.mms.service", "com.android.providers.telephony")) {
            runCatching { ctx.grantUriPermission(pkg, uri, flags) }
        }
    }

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "mms").apply { mkdirs() }

    private fun partValues(mid: Long, type: String, name: String?, text: String?) = ContentValues().apply {
        put("mid", mid)
        put("ct", type)
        if (name != null) {
            put("name", name)
            put("cl", name)
            put("cid", "<$name>")
        }
        if (text != null) {
            put("text", text)
            put("chset", 106)
        }
    }

    fun setBox(ctx: Context, id: Long, box: Int) {
        runCatching {
            ctx.contentResolver.update(
                Uri.parse("content://mms/$id"),
                ContentValues().apply { put("msg_box", box) },
                null, null
            )
        }
    }

    fun send(ctx: Context, address: String, text: String, image: Uri?) {
        val cr = ctx.contentResolver
        val bytes = image?.let { compressImage(ctx, it) }
        val pdu = buildSendReq(address, text.ifBlank { null }, bytes)
        val tid = Telephony.Threads.getOrCreateThreadId(ctx, address)
        val v = ContentValues().apply {
            put("thread_id", tid)
            put("date", System.currentTimeMillis() / 1000)
            put("msg_box", 4)
            put("read", 1)
            put("seen", 1)
            put("m_type", 128)
            put("v", 18)
            put("ct_t", "application/vnd.wap.multipart.mixed")
            put("pri", 129)
            put("rr", 129)
            put("d_rpt", 129)
            put("m_size", pdu.size)
        }
        val mu = cr.insert(Uri.parse("content://mms/outbox"), v) ?: throw IllegalStateException("insert")
        val id = ContentUris.parseId(mu)
        cr.insert(
            Uri.parse("content://mms/$id/addr"),
            ContentValues().apply {
                put("address", "insert-address-token")
                put("type", 137)
                put("charset", 106)
                put("msg_id", id)
            }
        )
        cr.insert(
            Uri.parse("content://mms/$id/addr"),
            ContentValues().apply {
                put("address", address)
                put("type", 151)
                put("charset", 106)
                put("msg_id", id)
            }
        )
        val partUri = Uri.parse("content://mms/$id/part")
        if (text.isNotBlank()) cr.insert(partUri, partValues(id, "text/plain", "text_0.txt", text))
        if (bytes != null) {
            val pu = cr.insert(partUri, partValues(id, "image/jpeg", "image_0.jpg", null))
            if (pu != null) cr.openOutputStream(pu)?.use { it.write(bytes) }
        }
        val f = File(dir(ctx), "send_$id.dat")
        f.writeBytes(pdu)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        grant(ctx, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pi = PendingIntent.getBroadcast(
            ctx,
            id.toInt(),
            Intent(ctx, MmsSentReceiver::class.java)
                .putExtra("mms_id", id)
                .putExtra("file", f.absolutePath),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            ctx.getSystemService(SmsManager::class.java).sendMultimediaMessage(ctx, uri, null, null, pi)
        } catch (e: Exception) {
            setBox(ctx, id, 5)
            throw e
        }
    }

    fun download(ctx: Context, notification: ByteArray) {
        val n = parseNotification(notification) ?: return
        val loc = n.location ?: return
        val f = File(dir(ctx), "dl_" + System.currentTimeMillis() + ".dat")
        f.writeBytes(ByteArray(0))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        grant(ctx, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val pi = PendingIntent.getBroadcast(
            ctx,
            f.name.hashCode(),
            Intent(ctx, MmsDownloadedReceiver::class.java)
                .putExtra("file", f.absolutePath)
                .putExtra("from", n.from ?: ""),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        ctx.getSystemService(SmsManager::class.java).downloadMultimediaMessage(ctx, loc, uri, null, pi)
    }

    fun onDownloaded(ctx: Context, path: String, from: String, ok: Boolean): Pair<String, String>? {
        val f = File(path)
        try {
            if (!ok) return null
            val r = parseRetrieve(f.readBytes()) ?: return null
            val addr = (r.from ?: from).ifBlank { return null }
            storeIncoming(ctx, addr, r)
            val text = r.parts.firstOrNull { it.type.startsWith("text/plain") }
                ?.let { String(it.data, Charsets.UTF_8).trim() }
            val hasImage = r.parts.any { it.type.startsWith("image/") }
            return addr to (if (!text.isNullOrBlank()) text else if (hasImage) "Photo" else "Attachment")
        } finally {
            f.delete()
        }
    }

    private fun storeIncoming(ctx: Context, from: String, r: MmsRetrieved) {
        val cr = ctx.contentResolver
        val tid = Telephony.Threads.getOrCreateThreadId(ctx, from)
        val v = ContentValues().apply {
            put("thread_id", tid)
            put("date", System.currentTimeMillis() / 1000)
            put("date_sent", r.date)
            put("msg_box", 1)
            put("read", 0)
            put("seen", 0)
            put("m_type", 132)
            put("v", 18)
            put("ct_t", "application/vnd.wap.multipart.related")
            put("m_size", r.parts.sumOf { it.data.size })
        }
        val mu = cr.insert(Uri.parse("content://mms/inbox"), v) ?: return
        val id = ContentUris.parseId(mu)
        cr.insert(
            Uri.parse("content://mms/$id/addr"),
            ContentValues().apply {
                put("address", from)
                put("type", 137)
                put("charset", 106)
                put("msg_id", id)
            }
        )
        val partUri = Uri.parse("content://mms/$id/part")
        for (part in r.parts) {
            if (part.type.contains("smil")) continue
            val isText = part.type.startsWith("text/")
            val pu = cr.insert(
                partUri,
                partValues(id, part.type, part.name, if (isText) String(part.data, Charsets.UTF_8) else null)
            )
            if (!isText && pu != null) cr.openOutputStream(pu)?.use { it.write(part.data) }
        }
    }
}
