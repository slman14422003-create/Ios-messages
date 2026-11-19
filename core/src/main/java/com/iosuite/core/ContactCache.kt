package com.iosuite.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import java.io.File
import java.util.concurrent.Executors

/**
 * Small on-disk mirror of the address book (name, photo, numbers) used by every app of the suite.
 *
 * - Lookups are served from memory, then SQLite; the contacts provider is only touched by [sync].
 * - [sync] is a delta: it asks the provider only for contacts whose CONTACT_LAST_UPDATED_TIMESTAMP is newer
 *   than the last sync, and re-reads the numbers of those contacts only.
 * - Each app keeps its own copy in its own private storage (apps cannot share a file without a provider).
 */
class ContactCache private constructor(private val app: Context) {

    class Entry(
        val contactId: Long,
        val lookupKey: String?,
        val name: String,
        val thumb: String?,
        val photo: String?,
        val updated: Long,
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, "iosuite_contacts.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.enableWriteAheadLogging()
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE contacts(contact_id INTEGER PRIMARY KEY, lookup_key TEXT, name TEXT NOT NULL, " +
                    "thumb TEXT, photo TEXT, updated INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE numbers(key TEXT NOT NULL, contact_id INTEGER NOT NULL, number TEXT NOT NULL, " +
                    "PRIMARY KEY(key, contact_id))"
            )
            db.execSQL("CREATE INDEX numbers_by_contact ON numbers(contact_id)")
            db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS numbers")
            db.execSQL("DROP TABLE IF EXISTS contacts")
            db.execSQL("DROP TABLE IF EXISTS meta")
            onCreate(db)
        }
    }

    private val db: SQLiteDatabase by lazy { Helper(app).writableDatabase }
    private val thumbDir: File by lazy { File(app.cacheDir, "contact_thumbs").also { it.mkdirs() } }
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "contact-cache").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val NONE = Any()

    private val memory = object : LinkedHashMap<String, Any>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean = size > 512
    }

    @Volatile var version = 0
        private set

    @Volatile private var readyFlag: Boolean? = null
    @Volatile private var watching = false

    private fun canRead() = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun meta(key: String): String? =
        db.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun setMeta(key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO meta(k, v) VALUES(?, ?)", arrayOf(key, value))
    }

    /** True once a first full sync finished: until then lookups fall back to a live provider query. */
    fun ready(): Boolean {
        readyFlag?.let { return it }
        val r = runCatching { meta("since") != null }.getOrDefault(false)
        if (r) readyFlag = true
        return r
    }

    /** Name / photo of the contact owning [number], or null. Safe on any thread. */
    fun find(number: String): Entry? {
        if (number.none { it.isDigit() }) return null
        val key = PhoneKey.of(number)
        synchronized(memory) {
            val hit = memory[key]
            if (hit != null) return if (hit === NONE) null else hit as Entry
        }
        if (!ready()) return live(number)
        val found = runCatching { queryDb(key) }.getOrNull()
        synchronized(memory) { memory[key] = found ?: NONE }
        return found
    }

    fun name(number: String): String? = find(number)?.name

    /** Memory-only lookup (no disk, no provider): safe on the main thread. Null when not loaded yet or unknown. */
    fun peek(number: String): Entry? = synchronized(memory) {
        val hit = memory[PhoneKey.of(number)]
        if (hit == null || hit === NONE) null else hit as Entry
    }

    private fun queryDb(key: String): Entry? =
        db.rawQuery(
            "SELECT c.contact_id, c.lookup_key, c.name, c.thumb, c.photo, c.updated FROM numbers n " +
                "JOIN contacts c ON c.contact_id = n.contact_id WHERE n.key=? LIMIT 1",
            arrayOf(key)
        ).use {
            if (it.moveToFirst()) Entry(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getLong(5))
            else null
        }

    /** Used only before the first sync finished (or without permission): one quick PhoneLookup query. */
    private fun live(number: String): Entry? {
        if (!canRead()) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            app.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.CONTACT_ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                ),
                null, null, null
            )?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(2)?.takeIf { n -> n.isNotBlank() } ?: return@use null
                    Entry(it.getLong(0), it.getString(1), name, it.getString(3), it.getString(4), 0L)
                } else null
            }
        }.getOrNull()
    }

    /** Small JPEG kept on disk next to the cache: decoded from the provider once, then read from the file. */
    fun bitmap(entry: Entry, px: Int = 128): Bitmap? {
        val file = File(thumbDir, "${entry.contactId}_${entry.updated}_$px.jpg")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { return it }
        }
        val src = entry.photo ?: entry.thumb ?: return null
        val bmp = runCatching {
            val uri = Uri.parse(src)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return null
        runCatching {
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }
        return bmp
    }

    /** Delta sync. Returns true when something in the mirror changed. Call off the main thread. */
    @Synchronized
    fun sync(): Boolean {
        if (!canRead()) return false
        val since = runCatching { meta("since")?.toLongOrNull() }.getOrNull()
        val full = since == null
        val resolver = app.contentResolver
        val changedIds = ArrayList<Long>()
        var newest = since ?: 0L
        var changed = false
        try {
            db.beginTransaction()
            try {
                val selection = StringBuilder(ContactsContract.Contacts.HAS_PHONE_NUMBER + "=1")
                var args: Array<String>? = null
                if (!full) {
                    selection.append(" AND ").append(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP).append(">=?")
                    args = arrayOf(since.toString())
                }
                val insert = db.compileStatement(
                    "INSERT OR REPLACE INTO contacts(contact_id, lookup_key, name, thumb, photo, updated) VALUES(?,?,?,?,?,?)"
                )
                resolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                        ContactsContract.Contacts.PHOTO_URI,
                        ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
                    ),
                    selection.toString(), args,
                    ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + " ASC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val ts = c.getLong(5)
                        insert.clearBindings()
                        insert.bindLong(1, id)
                        c.getString(1)?.let { insert.bindString(2, it) } ?: insert.bindNull(2)
                        insert.bindString(3, c.getString(2) ?: "")
                        c.getString(3)?.let { insert.bindString(4, it) } ?: insert.bindNull(4)
                        c.getString(4)?.let { insert.bindString(5, it) } ?: insert.bindNull(5)
                        insert.bindLong(6, ts)
                        insert.executeInsert()
                        changedIds += id
                        if (ts > newest) newest = ts
                    }
                }
                if (changedIds.isNotEmpty()) {
                    changed = true
                    if (full) db.execSQL("DELETE FROM numbers") else deleteNumbers(changedIds)
                    writeNumbers(if (full) null else changedIds)
                    dropThumbs(changedIds)
                }
                if (reconcile()) changed = true
                setMeta("since", newest.toString())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            return false
        }
        readyFlag = true
        if (changed) {
            synchronized(memory) { memory.clear() }
            version++
        } else if (full) {
            synchronized(memory) { memory.clear() }
        }
        return changed
    }

    private fun deleteNumbers(ids: List<Long>) {
        ids.chunked(400).forEach { chunk ->
            db.execSQL("DELETE FROM numbers WHERE contact_id IN (${chunk.joinToString(",")})")
        }
    }

    private fun writeNumbers(ids: List<Long>?) {
        val insert = db.compileStatement("INSERT OR IGNORE INTO numbers(key, contact_id, number) VALUES(?,?,?)")
        fun readRows(selection: String?) {
            app.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                selection, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(1) ?: continue
                    if (number.none { it.isDigit() }) continue
                    insert.clearBindings()
                    insert.bindString(1, PhoneKey.of(number))
                    insert.bindLong(2, c.getLong(0))
                    insert.bindString(3, number.trim())
                    insert.executeInsert()
                }
            }
        }
        if (ids == null) {
            readRows(null)
        } else {
            ids.chunked(300).forEach { chunk ->
                readRows(ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " IN (" + chunk.joinToString(",") + ")")
            }
        }
    }

    /** Contacts deleted (or left without numbers) since the last sync: compare ids only, which is cheap. */
    private fun reconcile(): Boolean {
        val live = HashSet<Long>()
        app.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            ContactsContract.Contacts.HAS_PHONE_NUMBER + "=1", null, null
        )?.use { while (it.moveToNext()) live += it.getLong(0) }
        val stored = ArrayList<Long>()
        db.rawQuery("SELECT contact_id FROM contacts", null).use { while (it.moveToNext()) stored += it.getLong(0) }
        val gone = stored.filter { it !in live }
        if (gone.isEmpty()) return false
        gone.chunked(400).forEach { chunk ->
            val list = chunk.joinToString(",")
            db.execSQL("DELETE FROM numbers WHERE contact_id IN ($list)")
            db.execSQL("DELETE FROM contacts WHERE contact_id IN ($list)")
        }
        dropThumbs(gone)
        return true
    }

    private fun dropThumbs(ids: List<Long>) {
        val names = thumbDir.list() ?: return
        val set = ids.map { "${it}_" }.toHashSet()
        names.forEach { f -> if (set.contains(f.substringBefore('_') + "_")) File(thumbDir, f).delete() }
    }

    /** Runs a delta sync on the cache's own thread; [onChanged] runs on the main thread when something changed. */
    fun syncAsync(onChanged: (() -> Unit)? = null) {
        executor.execute {
            val changed = runCatching { sync() }.getOrDefault(false)
            if (changed && onChanged != null) main.post(onChanged)
        }
    }

    /** Keeps the mirror current: any contacts-provider change triggers a debounced delta sync. */
    fun watch() {
        if (watching) return
        watching = true
        val run = Runnable { syncAsync() }
        runCatching {
            app.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true,
                object : ContentObserver(main) {
                    override fun onChange(selfChange: Boolean) {
                        main.removeCallbacks(run)
                        main.postDelayed(run, 700)
                    }
                }
            )
        }
        syncAsync()
    }

    fun clearMemory() = synchronized(memory) { memory.clear() }

    companion object {
        @Volatile private var instance: ContactCache? = null

        fun get(context: Context): ContactCache =
            instance ?: synchronized(this) {
                instance ?: ContactCache(context.applicationContext).also { instance = it }
            }
    }
}
