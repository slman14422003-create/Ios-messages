package app.ios.messages.ui

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.ios.messages.AppState
import app.ios.messages.BannerEvent
import app.ios.messages.data.Prefs
import kotlinx.coroutines.delay

/** Everything the user can tweak about the banner. [blur] is the banner's own blur, in dp (0 = off). */
data class BannerLook(val color: Color, val alpha: Float, val anim: Int, val seconds: Int, val blur: Int) {
    companion object {
        fun load(prefs: Prefs) = BannerLook(
            Color(prefs.notifColorArgb()), prefs.bannerAlpha, prefs.bannerAnim, prefs.bannerSeconds, prefs.bannerBlur
        )
    }
}

private fun enterSpec(anim: Int) = when (anim) {
    1 -> spring<Float>(dampingRatio = 1f, stiffness = 420f)
    2 -> spring<Float>(dampingRatio = 0.62f, stiffness = 520f)
    3 -> tween<Float>(380)
    4 -> spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    else -> tween<Float>(1)
}

/**
 * Draws incoming-message banners in their OWN window. The blur is the window compositor's
 * background blur (Window.setBackgroundBlurRadius), so it is independent of the app's own glass /
 * backdrop blur, keeps working while screenshot blocking is on, and is clipped to the rounded shape.
 */
@Composable
fun InAppBannerHost(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var event by remember { mutableStateOf<BannerEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    var windowUp by remember { mutableStateOf(false) }
    var look by remember { mutableStateOf(BannerLook.load(Prefs(ctx))) }
    val progress = remember { Animatable(0f) }
    val topInset = WindowInsets.statusBars.getTop(LocalDensity.current)

    LaunchedEffect(Unit) {
        AppState.banners.collect { e ->
            look = BannerLook.load(Prefs(ctx))
            event = e
            visible = true
        }
    }
    LaunchedEffect(event) {
        if (event != null) {
            delay(look.seconds * 1000L)
            visible = false
        }
    }
    LaunchedEffect(visible, event?.id) {
        if (visible) {
            windowUp = true
            progress.snapTo(0f)
            if (look.anim == 0) progress.snapTo(1f) else progress.animateTo(1f, enterSpec(look.anim))
        } else if (windowUp) {
            if (look.anim == 0) progress.snapTo(0f) else progress.animateTo(0f, tween(220))
            windowUp = false
        }
    }

    val e = event
    if (windowUp && e != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val win = (LocalView.current.parent as? DialogWindowProvider)?.window
            LaunchedEffect(win, look) { win?.let { setupWindow(it, look) } }
            LaunchedEffect(win, topInset, look) {
                if (win != null) snapshotFlow { progress.value }.collect { placeWindow(win, it, look, topInset) }
            }
            BannerCard(
                title = e.title,
                body = e.body,
                sub = e.simLabel,
                onClick = { visible = false; onOpen(e.address) },
                onDismiss = { visible = false }
            )
        }
    }
}

private fun setupWindow(win: Window, look: BannerLook) {
    val dm = win.context.resources.displayMetrics
    val bg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 22f * dm.density
        setColor(look.color.copy(alpha = look.alpha).toArgb())
    }
    win.setBackgroundDrawable(bg)
    win.setDimAmount(0f)
    win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    win.addFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )
    win.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    win.setWindowAnimations(0)
    // The banner's own blur; 0 turns it off. Ignored by the system when cross-window blur is disabled.
    win.setBackgroundBlurRadius((look.blur * dm.density).toInt())
}

/** Entrance / exit animation done on the window itself so the blurred plate moves with the text. */
private fun placeWindow(win: Window, p: Float, look: BannerLook, topInset: Int) {
    val dm = win.context.resources.displayMetrics
    val d = dm.density
    val lp = win.attributes
    val margin = (12 * d).toInt()
    val baseY = topInset + (6 * d).toInt()
    val h = win.decorView.height.takeIf { it > 0 } ?: (84 * d).toInt()
    lp.width = dm.widthPixels - 2 * margin
    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
    when (look.anim) {
        1, 4 -> { lp.y = baseY - ((1f - p) * (h + baseY)).toInt(); lp.alpha = 1f }
        2 -> { lp.y = baseY - ((1f - p) * 24 * d).toInt(); lp.alpha = p.coerceIn(0f, 1f) }
        3 -> { lp.y = baseY; lp.alpha = p.coerceIn(0f, 1f) }
        else -> { lp.y = baseY; lp.alpha = 1f }
    }
    win.attributes = lp
}

/** Banner content only; the coloured, blurred plate is the window background. */
@Composable
fun BannerCard(
    title: String,
    body: String,
    sub: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val content = TextStyle(textDirection = TextDirection.Content)
    var drag = 0f
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(onDismiss) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        drag += dy
                        if (drag < -24f) { onDismiss?.invoke(); drag = 0f }
                    }
                )
            }
            .then(if (onClick != null) Modifier.plainClickable(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = content
            )
            Text(
                body, color = Color.White.copy(alpha = 0.95f), fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = content
            )
        }
        if (sub != null) {
            Spacer(Modifier.width(10.dp))
            Text(sub, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
        }
    }
}
