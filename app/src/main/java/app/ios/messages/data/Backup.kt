package app.ios.messages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Backup {
    class BadPassword : Exception()
    class BadFile : Exception()

    private val MAGIC = "IMSGBK1".toByteArray()
    private const val ITER = 150_000

    private fun key(pw: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = f.generateSecret(PBEKeySpec(pw, salt, ITER, 256)).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun export(ctx: Context, out: OutputStream, password: CharArray): Int {
        val arr = JSONArray()
        val cols = arrayOf("address", "body", "date", "date_sent", "type", "read")
        ctx.contentResolver.query(Uri.parse("content://sms"), cols, null, null, "date ASC")?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(4)
                if (type != 1 && type != 2 && type != 5) continue
                arr.put(
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
        val root = JSONObject().put("v", 1).put("created", System.currentTimeMillis()).put("sms", arr)
        val raw = ByteArrayOutputStream()
        GZIPOutputStream(raw).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(raw.toByteArray())
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(enc)
        out.flush()
        return arr.length()
    }

    fun restore(ctx: Context, input: InputStream, password: CharArray): Int {
        val all = input.readBytes()
        if (all.size < MAGIC.size + 28 || !all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw BadFile()
        var o = MAGIC.size
        val salt = all.copyOfRange(o, o + 16); o += 16
        val iv = all.copyOfRange(o, o + 12); o += 12
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(all, o, all.size - o)
        } catch (e: AEADBadTagException) {
            throw BadPassword()
        }
        val json = GZIPInputStream(ByteArrayInputStream(plain)).use { String(it.readBytes(), Charsets.UTF_8) }
        val arr = JSONObject(json).getJSONArray("sms")
        val cr = ctx.contentResolver
        val existing = HashSet<String>()
        cr.query(Uri.parse("content://sms"), arrayOf("address", "date", "type"), null, null, null)?.use { c ->
            while (c.moveToNext()) existing += "${c.getString(0)}|${c.getLong(1)}|${c.getInt(2)}"
        }
        var added = 0
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val a = m.getString("a")
            val d = m.getLong("d")
            val t = m.getInt("t")
            if (!existing.add("$a|$d|$t")) continue
            val uri = when (t) {
                1 -> "content://sms/inbox"
                2 -> "content://sms/sent"
                else -> "content://sms/failed"
            }
            val v = ContentValues().apply {
                put("address", a)
                put("body", m.getString("b"))
                put("date", d)
                put("date_sent", m.optLong("s", 0L))
                put("read", m.optInt("r", 1))
                put("seen", 1)
                put("type", t)
            }
            try {
                if (cr.insert(Uri.parse(uri), v) != null) added++
            } catch (_: Exception) {
            }
        }
        return added
    }
}
