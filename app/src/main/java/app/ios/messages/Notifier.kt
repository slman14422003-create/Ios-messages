package app.ios.messages

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.LocusId
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap
import app.ios.messages.data.Otp
import app.ios.messages.data.Prefs
import app.ios.messages.data.SimInfo
import app.ios.messages.data.Sims
import app.ios.messages.data.SmsRepository

/**
 * Notifications are fully owned by Android: the app creates normal channels (messages, verification codes,
 * delivery problems) and never overrides sound, vibration, pop-up, lock-screen visibility or Do Not Disturb.
 * Everything the user wants to change lives in the system's notification settings for this app.
 */
private object SharedRepo {
    @Volatile private var repo: SmsRepository? = null

    fun get(ctx: Context): SmsRepository =
        repo ?: synchronized(this) { repo ?: SmsRepository(ctx.applicationContext).also { repo = it } }
}

object Notifier {
    const val KEY_REPLY = "reply_text"
    const val CH_MESSAGES = "messages_v5"
    const val CH_CODES = "codes_v5"
    const val CH_FAILED = "failed_v3"
    private const val GROUP = "app.ios.messages.GROUP"
    private const val SUMMARY_ID = 0x5A11
    private const val MAX_LINES = 8

    /** Conversation key -> recent lines. The key ignores "+966", spaces and dashes (same person = one notification). */
    private val history = HashMap<String, ArrayList<Pair<String, Long>>>()

    @Volatile private var channelsReady = false

    /** Ids of the conversation notifications this app has posted and that are still showing (no binder call needed to ask). */
    private val posted: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    @Volatile private var seeded = false

    private var simsAt = 0L
    private var simsCache: List<SimInfo> = emptyList()

    /** SIM list for the "SIM 2" sub-text: cached for 30 s so a burst of messages does not hit the telephony binder each time. */
    private fun simsCached(ctx: Context): List<SimInfo> = synchronized(this) {
        val now = System.currentTimeMillis()
        if (now - simsAt > 30_000L) {
            simsCache = runCatching { Sims.list(ctx) }.getOrDefault(emptyList())
            simsAt = now
        }
        simsCache
    }

    /** One-time (per process) look at what is already on screen, so a restarted process still knows its own notifications. */
    private fun seedPosted(nm: NotificationManager) {
        if (seeded) return
        synchronized(this) {
            if (seeded) return
            runCatching {
                nm.activeNotifications.forEach { if (it.id != SUMMARY_ID && it.notification.group == GROUP) posted.add(it.id) }
            }
            seeded = true
        }
    }

    private val shortcutAt = HashMap<String, Long>()
    private const val SHORTCUT_GAP_MS = 30L * 60L * 1000L

    /** Same number written two ways must map to the same notification id. */
    private fun nid(address: String): Int = Prefs.keyOf(address).hashCode()

    private fun open(ctx: Context, address: String): PendingIntent {
        val open = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("smsto:" + Uri.encode(address))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, nid(address), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun ensureChannels(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (channelsReady && nm.getNotificationChannel(CH_MESSAGES) != null) return nm
        val lc = Lang.wrap(ctx)
        val prefs = Prefs(ctx)
        if (prefs.notifChannelVersion < 5) {
            // Older builds left one channel behind for every sound / vibration / pop-up combination.
            val keep = setOf(CH_MESSAGES, CH_CODES, CH_FAILED)
            nm.notificationChannels.forEach { ch ->
                val legacy = ch.id.startsWith("messages") || ch.id.startsWith("codes") ||
                    ch.id.startsWith("quiet") || ch.id.startsWith("failed")
                if (ch.id !in keep && legacy) runCatching { nm.deleteNotificationChannel(ch.id) }
            }
            prefs.notifChannelVersion = 5
        }
        // createNotificationChannel on an existing channel only refreshes its name / description,
        // which keeps them in the app language while leaving the user's own sound / vibration choices alone.
        nm.createNotificationChannel(messageChannel(CH_MESSAGES, lc.getString(R.string.messages), lc.getString(R.string.ch_messages_desc), lc))
        nm.createNotificationChannel(messageChannel(CH_CODES, lc.getString(R.string.ch_codes), lc.getString(R.string.ch_codes_desc), lc))
        nm.createNotificationChannel(
            NotificationChannel(CH_FAILED, lc.getString(R.string.ch_failed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = lc.getString(R.string.ch_failed_desc)
                setShowBadge(false)
            }
        )
        channelsReady = true
        return nm
    }

    /** Starting values only: from then on the user's choice in Android settings wins. */
    private fun messageChannel(id: String, name: String, desc: String, lc: Context): NotificationChannel =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = desc
            // The system's own default notification tone: it follows whatever tone the user picks in Android settings,
            // and the user can still change sound / vibration / pop-up for this channel there.
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

    private fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Opens Android's own notification settings page for this app (sound, pop-up, lock screen, Do Not Disturb...). */
    fun systemSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    /** Opens the system page of one channel (messages / codes) straight on its sound, vibration and pop-up options. */
    fun channelSettingsIntent(ctx: Context, channelId: String = CH_MESSAGES): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

    fun show(ctx: Context, address: String, body: String, subId: Int = -1) {
        if (!allowed(ctx)) return
        val prefs = Prefs(ctx)
        val repo = SharedRepo.get(ctx)
        val tid = runCatching { repo.threadIdFor(address) }.getOrNull()
        if (tid != null && tid in prefs.muted) return
        // The conversation is on screen right now: the message is already visible (and gets marked read).
        if (AppState.foreground && AppState.openKey == prefs.key(address)) return

        // Fast path: the notification (and its sound) goes out right away with whatever name is cheap to get.
        // The slow full address-book search runs afterwards and only refreshes the title / photo silently.
        val quick = runCatching { repo.quickName(address) }.getOrNull()
        post(ctx, address, body, subId, tid, quick ?: address, runCatching { repo.cachedPhoto(address) }.getOrNull(), append = true, alert = true)
        if (quick == null) {
            Work.run {
                val full = runCatching { repo.contactName(address) }.getOrNull() ?: return@run
                if (nid(address) !in posted) return@run
                post(ctx, address, body, subId, tid, full, runCatching { repo.contactPhoto(address) }.getOrNull(), append = false, alert = false)
            }
        }
    }

    private fun post(
        ctx: Context, address: String, body: String, subId: Int, tid: Long?,
        title: String, photo: String?, append: Boolean, alert: Boolean
    ) {
        val prefs = Prefs(ctx)
        val repo = SharedRepo.get(ctx)
        val nm = ensureChannels(ctx)
        seedPosted(nm)
        val lc = Lang.wrap(ctx)
        val id = nid(address)
        val code = Otp.extract(body)
        val key = prefs.key(address)

        val lines = synchronized(history) {
            val l = history.getOrPut(key) { ArrayList() }
            if (l.isEmpty() && tid != null && tid >= 0) {
                // Fresh process: rebuild the earlier unread lines from the message store so the notification matches the inbox.
                runCatching { repo.recentUnread(tid, MAX_LINES) }.getOrNull()?.let { l.addAll(it) }
            }
            if (append && !(l.isNotEmpty() && l.last().first == body)) l.add(body to System.currentTimeMillis())
            if (l.isEmpty()) l.add(body to System.currentTimeMillis())
            while (l.size > MAX_LINES) l.removeAt(0)
            ArrayList(l)
        }

        val photoIcon = avatar(ctx, photo)
        val sender = Person.Builder().setName(title).setKey(key).apply { if (photoIcon != null) setIcon(photoIcon) }.build()
        val shortcutId = "chat_" + key
        val nowMs = System.currentTimeMillis()
        val pushShortcut = synchronized(shortcutAt) {
            val last = shortcutAt[shortcutId] ?: 0L
            if (nowMs - last > SHORTCUT_GAP_MS) { shortcutAt[shortcutId] = nowMs; true } else false
        }
        if (pushShortcut) publishShortcut(ctx, shortcutId, title, sender, photoIcon, address)
        val me = Person.Builder().setName(lc.getString(R.string.notif_me)).build()
        val style = Notification.MessagingStyle(me)
        lines.forEach { (text, time) -> style.addMessage(text, time, sender) }

        // What the lock screen shows when the user keeps "hide sensitive content" on in Android settings.
        val public = Notification.Builder(ctx, if (code != null) CH_CODES else CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(lc.getString(R.string.messages))
            .setContentText(lc.getString(R.string.notif_new_message))
            .build()

        val b = Notification.Builder(ctx, if (code != null) CH_CODES else CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setContentIntent(open(ctx, address))
            .setDeleteIntent(dismissed(ctx, address))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setWhen(lines.last().second)
            .setShowWhen(true)
            .setOnlyAlertOnce(!alert)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setShortcutId(shortcutId)
            .setLocusId(LocusId(shortcutId))

        if (subId >= 0) {
            val sims = simsCached(ctx)
            if (sims.size > 1) sims.firstOrNull { it.subId == subId }?.let { b.setSubText(it.label) }
        }

        if (code != null) {
            val copy = PendingIntent.getBroadcast(
                ctx, id,
                Intent(ctx, CopyCodeReceiver::class.java)
                    .putExtra("code", code)
                    .putExtra("notif_id", id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(Notification.Action.Builder(null, lc.getString(R.string.notif_copy_code, code), copy).build())
        }

        if (tid != null) {
            val read = PendingIntent.getBroadcast(
                ctx, id xor 0x1111,
                Intent(ctx, MarkReadReceiver::class.java)
                    .putExtra("thread_id", tid)
                    .putExtra("address", address)
                    .putExtra("notif_id", id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(
                Notification.Action.Builder(null, lc.getString(R.string.mark_as_read), read)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .build()
            )
        }

        if (repo.isDefault()) {
            val input = RemoteInput.Builder(KEY_REPLY).setLabel(lc.getString(R.string.notif_reply_hint)).build()
            val reply = PendingIntent.getBroadcast(
                ctx, id xor 0x2222,
                Intent(ctx, ReplyReceiver::class.java)
                    .putExtra("address", address)
                    .putExtra("sub_id", subId)
                    .putExtra("thread_id", tid ?: -1L)
                    .putExtra("notif_id", id),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val action = Notification.Action.Builder(null, lc.getString(R.string.notif_reply), reply)
                .addRemoteInput(input)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                .setAllowGeneratedReplies(true)
                .build()
            b.addAction(action)
        }

        try {
            nm.notify(id, b.build())
            posted.add(id)
            if (alert) postSummary(ctx, nm)
        } catch (e: Throwable) {
            android.util.Log.e("IosMessages", "notify failed", e)
        }
    }

    private fun avatar(ctx: Context, photo: String?): Icon? {
        if (photo.isNullOrEmpty()) return null
        return runCatching {
            val uri = Uri.parse(photo)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 128 && bounds.outHeight / (sample * 2) >= 128) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?.let { Icon.createWithBitmap(it) }
        }.getOrNull()
    }

    private fun publishShortcut(ctx: Context, id: String, title: String, person: Person, icon: Icon?, address: String) {
        runCatching {
            val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return@runCatching
            val intent = Intent(ctx, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("smsto:" + Uri.encode(address)))
            val info = ShortcutInfo.Builder(ctx, id)
                .setShortLabel(title)
                .setLongLived(true)
                .setPerson(person)
                .setIntent(intent)
                .apply { if (icon != null) setIcon(icon) }
                .build()
            sm.pushDynamicShortcut(info)
        }
    }

    private fun dismissed(ctx: Context, address: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, nid(address) xor 0x3333,
            Intent(ctx, NotificationDismissedReceiver::class.java).putExtra("address", address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun postSummary(ctx: Context, nm: NotificationManager) {
        val active = posted.size
        if (active < 2) {
            if (active == 0) nm.cancel(SUMMARY_ID)
            return
        }
        val lc = Lang.wrap(ctx)
        val label = lc.getString(R.string.notif_conversations, active)
        val open = PendingIntent.getActivity(
            ctx, SUMMARY_ID, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val summary = Notification.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(lc.getString(R.string.messages))
            .setContentText(label)
            .setStyle(Notification.InboxStyle().setSummaryText(label))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        nm.notify(SUMMARY_ID, summary)
    }

    fun forget(address: String) {
        synchronized(history) { history.remove(Prefs.keyOf(address)) }
        posted.remove(nid(address))
    }

    fun cancel(ctx: Context, address: String) {
        forget(address)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(nid(address))
        cleanSummary(nm)
    }

    /** Removes the notifications of conversations that are now fully read (read in the app, on another device, or by another app). */
    fun syncRead(ctx: Context, readAddresses: List<String>) {
        if (readAddresses.isEmpty()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        seedPosted(nm)
        if (posted.isEmpty()) return
        var changed = false
        readAddresses.forEach { a ->
            val id = nid(a)
            if (posted.remove(id)) {
                nm.cancel(id)
                synchronized(history) { history.remove(Prefs.keyOf(a)) }
                changed = true
            }
        }
        if (changed) cleanSummary(nm)
    }

    fun cancelAll(ctx: Context) {
        synchronized(history) { history.clear() }
        posted.clear()
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun cleanSummary(nm: NotificationManager) {
        if (posted.isEmpty()) nm.cancel(SUMMARY_ID)
    }

    fun showReplyResult(ctx: Context, address: String, sentText: String, ok: Boolean) {
        val nm = ensureChannels(ctx)
        if (ok) {
            forget(address)
            nm.cancel(nid(address))
            cleanSummary(nm)
        } else {
            showFailed(ctx, address, -1)
        }
    }

    fun showFailed(ctx: Context, address: String, subId: Int) {
        if (!allowed(ctx)) return
        val nm = ensureChannels(ctx)
        val lc = Lang.wrap(ctx)
        val title = SharedRepo.get(ctx).contactName(address) ?: address
        val sims = simsCached(ctx)
        val other = sims.firstOrNull { it.subId != subId }
        val text = if (other != null) lc.getString(R.string.failed_retry_sim, other.label)
        else lc.getString(R.string.failed_retry)
        val n = Notification.Builder(ctx, CH_FAILED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open(ctx, address))
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        nm.notify(nid(address) xor 0x5EED, n)
    }
}
