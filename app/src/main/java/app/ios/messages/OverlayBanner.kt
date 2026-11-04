package app.ios.messages

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import app.ios.messages.data.Prefs

/**
 * Our own banner shown while the app is CLOSED. The system notification shade cannot be restyled, so this draws
 * a real window on top of other apps (needs "Display over other apps"). The normal notification is still posted
 * (quietly) so the message stays in the shade. Plain Views on purpose: no Compose / lifecycle needed here.
 */
object OverlayBanner {
    private val main = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var hideTask: Runnable? = null

    fun canShow(ctx: Context): Boolean {
        if (!Settings.canDrawOverlays(ctx)) return false
        val pm = ctx.getSystemService(PowerManager::class.java)
        val km = ctx.getSystemService(KeyguardManager::class.java)
        // Over the lock screen / with the screen off the system notification is used instead.
        return pm?.isInteractive == true && km?.isKeyguardLocked != true
    }

    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun show(ctx: Context, address: String, title: String, body: String, sub: String?) {
        val app = ctx.applicationContext
        main.post {
            try {
                remove(app)
                val d = app.resources.displayMetrics.density
                val prefs = Prefs(app)
                val color = prefs.notifColorArgb()
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 22f * d
                    val a = (prefs.bannerAlpha * 255).toInt().coerceIn(80, 255)
                    setColor((color and 0x00FFFFFF) or (a shl 24))
                }
                val row = LinearLayout(app).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = bg
                    elevation = 8f * d
                    setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                }
                val initial = TextView(app).apply {
                    text = title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 18f
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x38FFFFFF) }
                }
                row.addView(initial, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()))
                val col = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
                col.addView(TextView(app).apply {
                    text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END; textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                })
                col.addView(TextView(app).apply {
                    text = body; setTextColor(0xF2FFFFFF.toInt()); textSize = 14f
                    maxLines = 2; ellipsize = TextUtils.TruncateAt.END; textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                })
                val cp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                cp.marginStart = (12 * d).toInt()
                row.addView(col, cp)
                if (sub != null) {
                    row.addView(TextView(app).apply {
                        text = sub; setTextColor(0xD9FFFFFF.toInt()); textSize = 12f; maxLines = 1
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = (10 * d).toInt()
                    })
                }

                var downY = 0f
                var moved = false
                row.setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { downY = e.rawY; moved = false; true }
                        MotionEvent.ACTION_MOVE -> {
                            val dy = e.rawY - downY
                            if (dy < 0) { moved = true; v.translationY = dy }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (moved && e.rawY - downY < -24 * d) remove(app)
                            else if (!moved) {
                                remove(app)
                                runCatching {
                                    app.startActivity(
                                        Intent(app, MainActivity::class.java).apply {
                                            action = Intent.ACTION_VIEW
                                            data = Uri.parse("smsto:" + Uri.encode(address))
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        }
                                    )
                                }
                            } else v.animate().translationY(0f).setDuration(150).start()
                            true
                        }
                        else -> true
                    }
                }

                val wm = app.getSystemService(WindowManager::class.java)
                val margin = (12 * d).toInt()
                val lp = WindowManager.LayoutParams(
                    app.resources.displayMetrics.widthPixels - 2 * margin,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (36 * d).toInt()
                }
                row.translationY = -200f * d
                wm.addView(row, lp)
                current = row
                row.animate().translationY(0f).setDuration(280)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                val t = Runnable { remove(app) }
                hideTask = t
                main.postDelayed(t, prefs.bannerSeconds * 1000L)
            } catch (_: Throwable) {
            }
        }
    }

    private fun remove(ctx: Context) {
        hideTask?.let { main.removeCallbacks(it) }
        hideTask = null
        val v = current ?: return
        current = null
        runCatching { ctx.getSystemService(WindowManager::class.java).removeViewImmediate(v) }
    }
}
