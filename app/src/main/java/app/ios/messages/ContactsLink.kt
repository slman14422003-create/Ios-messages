package app.ios.messages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.iosuite.core.Hero

object ContactsLink {
    const val PACKAGE = "com.iosphone.contacts"

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Contacts app installed: shared-element hop (photo + name fly across). Otherwise the regular push. */
    private fun launch(context: Context, intent: Intent, hero: Hero?): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Transitions.EXTRA_RETURN, true)
        if (!installed(context)) return Transitions.start(context, intent)
        intent.setPackage(PACKAGE)
        if (Transitions.startHero(context, intent, hero)) return true
        intent.setPackage(null)
        return Transitions.start(context, intent)
    }

    fun view(context: Context, contactUri: Uri, hero: Hero? = null): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, contactUri), hero)

    fun create(context: Context, number: String, hero: Hero? = null): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        return launch(context, intent, hero)
    }
}
