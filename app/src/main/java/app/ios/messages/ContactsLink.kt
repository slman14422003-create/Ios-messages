package app.ios.messages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

object ContactsLink {
    const val PACKAGE = "com.iosphone.contacts"

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    private fun launch(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Transitions.EXTRA_RETURN, true)
        if (!installed(context)) return runCatching { context.startActivity(intent); true }.getOrDefault(false)
        intent.setPackage(PACKAGE)
        if (Transitions.start(context, intent)) return true
        intent.setPackage(null)
        return Transitions.start(context, intent)
    }

    fun view(context: Context, contactUri: Uri): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, contactUri))

    fun create(context: Context, number: String): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        return launch(context, intent)
    }
}
