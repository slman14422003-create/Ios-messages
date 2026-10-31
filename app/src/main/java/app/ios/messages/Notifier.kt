package app.ios.messages

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.ios.messages.data.SmsRepository

object Notifier {
    private const val CHANNEL = "messages"

    fun show(ctx: Context, address: String, body: String) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
        val title = SmsRepository(ctx).contactName(address) ?: address
        val open = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("smsto:" + Uri.encode(address))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, address.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()
        nm.notify(address.hashCode(), n)
    }
}
