package app.ios.messages

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import app.ios.messages.data.Prefs

/**
 * Message sounds. Tone 0 plays the bundled iPhone-style sounds from res/raw (msg_received / msg_sent):
 * drop your own files with the same names there to replace them. Tone 1 uses the phone's default.
 */
object Sounds {
    fun receivedUri(ctx: Context): Uri =
        Uri.parse("android.resource://" + ctx.packageName + "/" + R.raw.msg_received)

    private fun ringerNormal(ctx: Context): Boolean =
        ctx.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    private fun play(ctx: Context, res: Int) {
        runCatching {
            val mp = MediaPlayer.create(
                ctx, res,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
            ) ?: return
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { p, _, _ -> p.release(); true }
            mp.start()
        }
    }

    /** Message arrived while the app is open (the system plays the channel sound otherwise). */
    fun playReceived(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.notifSound || !ringerNormal(ctx)) return
        if (prefs.notifTone == 0) play(ctx, R.raw.msg_received)
        else runCatching {
            RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
    }

    fun playSent(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.sentSound || !ringerNormal(ctx)) return
        play(ctx, R.raw.msg_sent)
    }
}
