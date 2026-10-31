package app.ios.messages.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Snapshot of the app window taken when a menu opens; menus blur it to look like iOS materials. */
class Backdrop(
    val image: ImageBitmap,
    val hostLeft: Int,
    val hostTop: Int,
    val width: Int,
    val height: Int
)

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

suspend fun captureBackdrop(view: View, scale: Float = 0.5f): Backdrop? =
    suspendCancellableCoroutine { cont ->
        val win = view.context.findActivity()?.window
        val decor = win?.decorView
        val w = decor?.width ?: 0
        val h = decor?.height ?: 0
        if (win == null || decor == null || w <= 0 || h <= 0) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val bmp = Bitmap.createBitmap(
            (w * scale).roundToInt().coerceAtLeast(1),
            (h * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        try {
            PixelCopy.request(
                win,
                bmp,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (!cont.isActive) return@OnPixelCopyFinishedListener
                    if (result == PixelCopy.SUCCESS) {
                        val loc = IntArray(2)
                        decor.getLocationOnScreen(loc)
                        cont.resume(Backdrop(bmp.asImageBitmap(), loc[0], loc[1], w, h))
                    } else cont.resume(null)
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }

/** Tracks where a composable sits on the physical screen (popup and dialog windows move around). */
@Stable
class ScreenPos {
    var coords: LayoutCoordinates? = null
    var pos by mutableStateOf(IntOffset.Zero)
    private val loc = IntArray(2)

    fun refresh(view: View) {
        val c = coords ?: return
        if (!c.isAttached) return
        view.getLocationOnScreen(loc)
        val r = c.positionInRoot()
        val n = IntOffset(loc[0] + r.x.roundToInt(), loc[1] + r.y.roundToInt())
        if (n != pos) pos = n
    }
}

@Composable
fun rememberScreenPos(): ScreenPos {
    val s = remember { ScreenPos() }
    val view = LocalView.current
    LaunchedEffect(s) {
        repeat(45) {
            withFrameNanos { }
            s.refresh(view)
        }
    }
    return s
}

fun Modifier.screenPos(s: ScreenPos): Modifier = onGloballyPositioned { s.coords = it }

/** The blurred, saturated copy of the screen, aligned so it lines up exactly with what is behind this view. */
@Composable
fun GlassBackdrop(
    snap: Backdrop,
    origin: IntOffset,
    modifier: Modifier,
    blurDp: Float = 18f,
    saturation: Float = 2.0f
) {
    val d = LocalDensity.current
    val r = with(d) { blurDp.dp.toPx() }
    val effect = remember(r) { BlurEffect(r, r, TileMode.Clamp) }
    val cf = remember(saturation) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }
    Spacer(
        modifier
            .layout { m, c ->
                val p = m.measure(Constraints.fixed(snap.width, snap.height))
                val w = if (c.hasBoundedWidth) c.maxWidth else 0
                val h = if (c.hasBoundedHeight) c.maxHeight else 0
                layout(w, h) { p.place(snap.hostLeft - origin.x, snap.hostTop - origin.y) }
            }
            .graphicsLayer { renderEffect = effect }
            .drawBehind {
                drawImage(
                    snap.image,
                    dstSize = IntSize(snap.width, snap.height),
                    colorFilter = cf,
                    filterQuality = FilterQuality.Low
                )
            }
    )
}

/** iOS "thick material": blurred backdrop + translucent tint. Falls back to the old solid fill until the snapshot is ready. */
@Composable
fun GlassFill(
    snap: Backdrop?,
    origin: IntOffset,
    dark: Boolean,
    modifier: Modifier,
    blurDp: Float = 18f
) {
    val ready by animateFloatAsState(if (snap != null) 1f else 0f, Motion.fade(200), label = "glass")
    val opaque = if (dark) Color(0xFF2C2C2E) else Color(0xFFF8F8F8)
    val tint = if (dark) Color(0x883A3A3C) else Color(0x92F7F7F9)
    Box(modifier) {
        if (snap != null) GlassBackdrop(snap, origin, Modifier.matchParentSize(), blurDp)
        Box(Modifier.matchParentSize().background(tint))
        Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - ready }.background(opaque))
    }
}
