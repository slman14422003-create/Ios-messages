package app.ios.messages

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.SoundPool
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

    // SoundPool keeps both clips decoded in memory: tapping Send no longer waits for MediaPlayer.create()
    // (file open + decode on the main thread), which used to cause a visible hitch on every send.
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()
    private val ready = HashSet<Int>()

    @Synchronized
    private fun ensurePool(ctx: Context): SoundPool {
        pool?.let { return it }
        val sp = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        sp.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(this) { ready += sampleId }
        }
        val app = ctx.applicationContext
        for (res in intArrayOf(R.raw.msg_received, R.raw.msg_sent)) {
            ids[res] = sp.load(app, res, 1)
        }
        pool = sp
        return sp
    }

    /** Call once at start-up so the clips are decoded before the first message. */
    fun warmUp(ctx: Context) {
        runCatching { ensurePool(ctx) }
    }

    private fun play(ctx: Context, res: Int) {
        runCatching {
            val sp = ensurePool(ctx)
            val id = ids[res] ?: return
            val loaded = synchronized(this) { id in ready }
            if (loaded) {
                sp.play(id, 1f, 1f, 1, 0, 1f)
            } else {
                // First play right after launch: fall back to MediaPlayer once.
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
