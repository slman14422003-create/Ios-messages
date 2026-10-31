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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import app.ios.messages.data.Otp
import app.ios.messages.data.Prefs
import app.ios.messages.data.Sims
import app.ios.messages.data.SmsRepository

object Notifier {
    const val KEY_REPLY = "reply_text"
    const val CH_FAILED = "failed_v3"
    private const val GROUP = "app.ios.messages.GROUP"
    private const val SUMMARY_ID = 0x5A11
    private const val MAX_LINES = 8

    private val history = HashMap<String, ArrayList<Pair<String, Long>>>()

    private fun open(ctx: Context, address: String): PendingIntent {
        val open = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("smsto:" + Uri.encode(address))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, address.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun ensureChannels(ctx: Context): NotificationManager {
        val lc = Lang.wrap(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val prefs = Prefs(ctx)
        if (prefs.notifChannelVersion < 3) {
            listOf("messages", "codes", "failed", "messages_v2", "codes_v2", "failed_v2").forEach {
                runCatching { nm.deleteNotificationChannel(it) }
            }
            prefs.notifChannelVersion = 3
        }
        channelFor(nm, prefs, false, lc)
        channelFor(nm, prefs, true, lc)
        if (nm.getNotificationChannel(CH_FAILED) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_FAILED, lc.getString(R.string.ch_failed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = lc.getString(R.string.ch_failed_desc)
                setShowBadge(false)
            })
        }
        return nm
    }

    fun channelId(prefs: Prefs, codes: Boolean): String =
        (if (codes) "codes" else "messages") +
            "_s" + (if (prefs.notifSound) 1 else 0) +
            "v" + (if (prefs.notifVibrate) 1 else 0) +
            "h" + (if (prefs.notifHeadsUp) 1 else 0)

    private fun channelFor(nm: NotificationManager, prefs: Prefs, codes: Boolean, lc: Context): String {
        val id = channelId(prefs, codes)
        val importance = when {
            prefs.notifHeadsUp -> NotificationManager.IMPORTANCE_HIGH
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        val ch = NotificationChannel(id, if (codes) lc.getString(R.string.ch_codes) else lc.getString(R.string.messages), importance)
        ch.description = if (codes) lc.getString(R.string.ch_codes_desc) else lc.getString(R.string.ch_messages_desc)
        if (prefs.notifSound) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
        } else {
            ch.setSound(null, null)
        }
        ch.enableVibration(prefs.notifVibrate)
        ch.enableLights(true)
        ch.setShowBadge(true)
        ch.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        nm.createNotificationChannel(ch)
        return id
    }

    private fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channelSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId(Prefs(ctx), false))

    fun show(ctx: Context, address: String, body: String, subId: Int = -1) {
        if (!allowed(ctx)) return
        val prefs = Prefs(ctx)
        if (!prefs.notifications) return
        val repo = SmsRepository(ctx)
        val tid = runCatching { repo.threadIdFor(address) }.getOrNull()
        if (tid != null && tid in prefs.muted) return
        val nm = ensureChannels(ctx)
        val lc = Lang.wrap(ctx)
        val title = repo.contactName(address) ?: address
        val id = address.hashCode()
        val code = if (prefs.notifCopyCode) Otp.extract(body) else null
        val channel = channelId(prefs, code != null)
        val previews = prefs.previews
        val shownBody = if (previews) body else lc.getString(R.string.notif_new_message)

        val lines = synchronized(history) {
            val l = history.getOrPut(address) { ArrayList() }
            l.add(shownBody to System.currentTimeMillis())
            while (l.size > MAX_LINES) l.removeAt(0)
            ArrayList(l)
        }

        val sender = Person.Builder().setName(title).setKey(prefs.key(address)).build()
        val me = Person.Builder().setName(lc.getString(R.string.notif_me)).build()
        val style = Notification.MessagingStyle(me)
        lines.forEach { (text, time) -> style.addMessage(text, time, sender) }

        val b = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(shownBody)
            .setStyle(style)
            .setContentIntent(open(ctx, address))
            .setDeleteIntent(dismissed(ctx, address))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup(if (prefs.notifGroup) GROUP else null)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setVisibility(if (prefs.notifLockScreen && previews) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_SECRET)

        if (subId >= 0) {
            val sims = runCatching { Sims.list(ctx) }.getOrDefault(emptyList())
            if (sims.size > 1) sims.firstOrNull { it.subId == subId }?.let { b.setSubText(it.label) }
        }

        if (code != null && previews) {
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
            b.addAction(Notification.Action.Builder(null, lc.getString(R.string.mark_as_read), read).build())
        }

        if (prefs.notifQuickReply && repo.isDefault()) {
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

        nm.notify(id, b.build())
        if (prefs.notifGroup) postSummary(ctx, nm, prefs)
    }

    private fun dismissed(ctx: Context, address: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, address.hashCode() xor 0x3333,
            Intent(ctx, NotificationDismissedReceiver::class.java).putExtra("address", address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun postSummary(ctx: Context, nm: NotificationManager, prefs: Prefs) {
        val active = nm.activeNotifications.count { it.id != SUMMARY_ID && it.notification.group == GROUP }
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
        val summary = Notification.Builder(ctx, channelId(prefs, false))
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
        synchronized(history) { history.remove(address) }
    }

    fun cancel(ctx: Context, address: String) {
        forget(address)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(address.hashCode())
        cleanSummary(nm)
    }

    fun syncRead(ctx: Context, readAddresses: List<String>) {
        if (readAddresses.isEmpty()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val activeIds = nm.activeNotifications.map { it.id }.toSet()
        var changed = false
        readAddresses.forEach { a ->
            if (a.hashCode() in activeIds) {
                nm.cancel(a.hashCode())
                forget(a)
                changed = true
            }
        }
        if (changed) cleanSummary(nm)
    }

    fun cancelAll(ctx: Context) {
        synchronized(history) { history.clear() }
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun cleanSummary(nm: NotificationManager) {
        val left = nm.activeNotifications.count { it.id != SUMMARY_ID && it.notification.group == GROUP }
        if (left == 0) nm.cancel(SUMMARY_ID)
    }

    fun showReplyResult(ctx: Context, address: String, sentText: String, ok: Boolean) {
        val nm = ensureChannels(ctx)
        val id = address.hashCode()
        if (ok) {
            forget(address)
            nm.cancel(id)
            cleanSummary(nm)
        } else {
            showFailed(ctx, address, -1)
        }
    }

    fun showFailed(ctx: Context, address: String, subId: Int) {
        if (!allowed(ctx)) return
        val nm = ensureChannels(ctx)
        val lc = Lang.wrap(ctx)
        val title = SmsRepository(ctx).contactName(address) ?: address
        val sims = runCatching { Sims.list(ctx) }.getOrDefault(emptyList())
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
        nm.notify(address.hashCode() xor 0x5EED, n)
    }
}
