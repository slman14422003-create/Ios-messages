package com.iosuite.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.Person
import android.content.Context

/** The look every notification of the suite shares: one group, one small icon, one accent, one MessagingStyle. */
object SuiteNotif {
    const val GROUP_ID = "ios_suite"
    val ACCENT: Int = 0xFF007AFF.toInt()

    val smallIcon: Int get() = R.drawable.ic_suite_notification

    /** Same id and label in all three apps, so Android shows one group in the notification settings. */
    fun ensureGroup(context: Context) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, context.getString(R.string.suite_notif_group)))
        }
    }

    fun inGroup(channel: NotificationChannel): NotificationChannel {
        channel.group = GROUP_ID
        return channel
    }

    fun brand(builder: Notification.Builder): Notification.Builder =
        builder.setSmallIcon(smallIcon).setColor(ACCENT)

    class Line(val text: String, val time: Long)

    fun me(context: Context): Person = Person.Builder().setName(context.getString(R.string.suite_me)).build()

    fun messagingStyle(me: Person, sender: Person, lines: List<Line>): Notification.MessagingStyle {
        val style = Notification.MessagingStyle(me)
        lines.forEach { style.addMessage(it.text, it.time, sender) }
        return style
    }

    fun missedText(context: Context, count: Int): String =
        if (count <= 1) context.getString(R.string.suite_missed_one) else context.getString(R.string.suite_missed_many, count)
}
