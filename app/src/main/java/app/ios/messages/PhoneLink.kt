package app.ios.messages

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hand-off to the companion iOS Phone app: the number opens in its keypad (with its own SIM sheet when the
 * call is placed). Without it, the system dialer is used.
 */
object PhoneLink {
    const val PACKAGE = "com.iosphone.app"

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun dial(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (installed(context)) intent.setPackage(PACKAGE)
        if (runCatching { context.startActivity(intent) }.isFailure && intent.`package` != null) {
            intent.setPackage(null)
            runCatching { context.startActivity(intent) }
        }
    }
}
