package app.ios.messages

import android.app.Application
import android.util.Log
import java.io.File

/** Saves the stack trace of any crash so the next launch can show it instead of closing silently. */
class MessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "crash.txt").writeText(Log.getStackTraceString(e))
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
        app.ios.messages.ui.Perf.cheapBlur = app.ios.messages.data.Prefs(this).smoothMode
        Thread { Sounds.warmUp(this) }.start()
    }
}
