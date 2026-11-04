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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.drawWithContent
import android.graphics.RenderNode
import android.graphics.RenderEffect
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
    val image: ImageBitmap?,
    val node: RenderNode?,
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
        // Screenshot protection is on: PixelCopy returns a black frame for secure windows, so record the
        // window into a RenderNode instead. It never leaves the app's own window, so it stays protected.
        if (win.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0) {
            cont.resume(recordBackdrop(decor, w, h))
            return@suspendCancellableCoroutine
        }
        try {
            val bmp = Bitmap.createBitmap(
                (w * scale).roundToInt().coerceAtLeast(1),
                (h * scale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            PixelCopy.request(
                win,
                bmp,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (!cont.isActive) return@OnPixelCopyFinishedListener
                    if (result == PixelCopy.SUCCESS) {
                        val loc = IntArray(2)
                        decor.getLocationOnScreen(loc)
                        cont.resume(Backdrop(bmp.asImageBitmap(), null, loc[0], loc[1], w, h))
                    } else cont.resume(recordBackdrop(decor, w, h))
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }

private fun recordBackdrop(decor: View, w: Int, h: Int): Backdrop? = try {
    val node = RenderNode("backdropLive")
    node.setPosition(0, 0, w, h)
    val rc = node.beginRecording(w, h)
    try {
        decor.draw(rc)
    } finally {
        node.endRecording()
    }
    val loc = IntArray(2)
    decor.getLocationOnScreen(loc)
    Backdrop(null, node, loc[0], loc[1], w, h)
} catch (e: Throwable) {
    null
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
    remember(snap, saturation) {
        snap.node?.setRenderEffect(
            RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(saturation) })
            )
        )
        0
    }
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
                val img = snap.image
                val node = snap.node
                if (img != null) {
                    drawImage(
                        img,
                        dstSize = IntSize(snap.width, snap.height),
                        colorFilter = cf,
                        filterQuality = FilterQuality.Low
                    )
                } else if (node != null) {
                    drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
                }
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

/** Global switch: true = lists use cheap static bars instead of live blur (far smoother, esp. at 90/120 Hz). */
object Perf {
    @Volatile var cheapBlur = true
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheapBar(
    bottom: Boolean, hh: Float, a: Float, bg: Color, fade: Boolean
) {
    val c = bg.copy(alpha = 0.95f * a)
    val y = if (bottom) size.height - hh else 0f
    val sz = Size(size.width, hh)
    if (!fade) {
        drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = sz)
        return
    }
    val brush = if (bottom) {
        Brush.verticalGradient(0f to Color.Transparent, 0.3f to c, 1f to c, startY = y, endY = y + hh)
    } else {
        Brush.verticalGradient(0f to c, 0.7f to c, 1f to Color.Transparent, startY = y, endY = y + hh)
    }
    drawRect(brush, topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = sz)
}

/** Holds the two render nodes used for live backdrop blur of a scrolling list. */
class LiveBlur {
    val src = RenderNode("lbSrc")
    val blur = RenderNode("lbBlur")
    val blur2 = RenderNode("lbBlur2")
    val layerPaint = Paint()
    var radius = -1f
    var radius2 = -1f
    var broken = false
}

/** The strip under a bar is blurred at 1/4 size (16x fewer pixels) and scaled back up: same look, far cheaper per frame. */
private const val BLUR_DOWN = 4f

/**
 * Live "bar material": the list is recorded once, then the strip under a bar (top or bottom)
 * is drawn again, blurred, with a translucent tint. Updates every frame while scrolling.
 */
fun Modifier.liveBlurEdge(
    h: LiveBlur,
    bottom: Boolean,
    heightPx: () -> Float,
    amount: () -> Float,
    bg: Color,
    tint: Color,
    radiusPx: Float,
    fade: Boolean = false
): Modifier = drawWithContent {
    val a = amount().coerceIn(0f, 1f)
    val hh = heightPx().coerceAtMost(size.height)
    if (h.broken || a <= 0.01f || hh <= 1f || size.width < 1f) {
        drawContent()
        return@drawWithContent
    }
    if (Perf.cheapBlur) {
        drawContent()
        drawCheapBar(bottom, hh, a, bg, fade)
        return@drawWithContent
    }
    var contentDrawn = false
    try {
    val w = size.width.toInt()
    val fullH = size.height.toInt()
    val bh = hh.toInt().coerceAtLeast(1)

    h.src.setPosition(0, 0, w, fullH)
    val rc = h.src.beginRecording()
    val old = drawContext.canvas
    try {
        drawContext.canvas = Canvas(rc)
        drawContent()
    } finally {
        drawContext.canvas = old
        h.src.endRecording()
    }
    drawIntoCanvas { it.nativeCanvas.drawRenderNode(h.src) }
    contentDrawn = true

    val oy = if (bottom) (fullH - bh).toFloat() else 0f
    val sw = ((w + 3) / 4).coerceAtLeast(1)
    val sh = ((bh + 3) / 4).coerceAtLeast(1)
    val smallRadius = (radiusPx / BLUR_DOWN).coerceAtLeast(0.5f)
    h.blur.setPosition(0, 0, sw, sh)
    if (h.radius != smallRadius) {
        val cm = android.graphics.ColorMatrix().apply { setSaturation(1.8f) }
        h.blur.setRenderEffect(
            RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(cm),
                RenderEffect.createBlurEffect(smallRadius, smallRadius, Shader.TileMode.CLAMP)
            )
        )
        h.radius = smallRadius
    }
    val rb = h.blur.beginRecording()
    try {
        rb.drawColor(bg.toArgb())
        rb.scale(1f / BLUR_DOWN, 1f / BLUR_DOWN)
        rb.translate(0f, -oy)
        rb.drawRenderNode(h.src)
    } finally {
        h.blur.endRecording()
    }

    val bounds = Rect(0f, 0f, size.width, hh)
    h.layerPaint.alpha = a
    drawIntoCanvas { c ->
        c.save()
        c.translate(0f, oy)
        c.saveLayer(bounds, h.layerPaint)
        c.save()
        c.scale(BLUR_DOWN, BLUR_DOWN)
        c.nativeCanvas.drawRenderNode(h.blur)
        c.restore()
        drawRect(tint, size = Size(size.width, hh))
        if (fade) {
            drawRect(
                brush = if (bottom) {
                    Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black, 1f to Color.Black, startY = 0f, endY = hh)
                } else {
                    Brush.verticalGradient(0f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent, startY = 0f, endY = hh)
                },
                size = Size(size.width, hh),
                blendMode = BlendMode.DstIn
            )
        }
        c.restore()
        c.restore()
    }
    } catch (e: Throwable) {
        h.broken = true
        if (!contentDrawn) drawContent()
    }
}


/**
 * Same look as two chained [liveBlurEdge] calls (top bar + bottom bar), but the list is recorded ONCE per
 * frame instead of twice (the nested version recorded the content, then recorded the recording again).
 * On mid-range phones this halves the per-frame cost while scrolling a chat.
 */
fun Modifier.liveBlurEdges(
    h: LiveBlur,
    topHeightPx: () -> Float,
    topAmount: () -> Float,
    bottomHeightPx: () -> Float,
    bottomAmount: () -> Float,
    bg: Color,
    tint: Color,
    radiusPx: Float,
    fade: Boolean = false
): Modifier = drawWithContent {
    val ta = topAmount().coerceIn(0f, 1f)
    val ba = bottomAmount().coerceIn(0f, 1f)
    val th = topHeightPx().coerceAtMost(size.height)
    val bh = bottomHeightPx().coerceAtMost(size.height)
    val doTop = ta > 0.01f && th > 1f
    val doBottom = ba > 0.01f && bh > 1f
    if (h.broken || size.width < 1f || (!doTop && !doBottom)) {
        drawContent()
        return@drawWithContent
    }
    if (Perf.cheapBlur) {
        drawContent()
        if (doBottom) drawCheapBar(true, bh, ba, bg, fade)
        if (doTop) drawCheapBar(false, th, ta, bg, fade)
        return@drawWithContent
    }
    var contentDrawn = false
    try {
        val w = size.width.toInt()
        val fullH = size.height.toInt()
        h.src.setPosition(0, 0, w, fullH)
        val rc = h.src.beginRecording()
        val old = drawContext.canvas
        try {
            drawContext.canvas = Canvas(rc)
            drawContent()
        } finally {
            drawContext.canvas = old
            h.src.endRecording()
        }
        drawIntoCanvas { it.nativeCanvas.drawRenderNode(h.src) }
        contentDrawn = true
        if (doBottom) drawBlurStrip(h, h.blur, 0, true, bh, ba, bg, tint, radiusPx, fade)
        if (doTop) drawBlurStrip(h, h.blur2, 1, false, th, ta, bg, tint, radiusPx, fade)
    } catch (e: Throwable) {
        h.broken = true
        if (!contentDrawn) drawContent()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlurStrip(
    h: LiveBlur,
    node: RenderNode,
    slot: Int,
    bottom: Boolean,
    hh: Float,
    a: Float,
    bg: Color,
    tint: Color,
    radiusPx: Float,
    fade: Boolean
) {
    val w = size.width.toInt()
    val fullH = size.height.toInt()
    val bh = hh.toInt().coerceAtLeast(1)
    val oy = if (bottom) (fullH - bh).toFloat() else 0f
    val sw = ((w + 3) / 4).coerceAtLeast(1)
    val sh = ((bh + 3) / 4).coerceAtLeast(1)
    val smallRadius = (radiusPx / BLUR_DOWN).coerceAtLeast(0.5f)
    node.setPosition(0, 0, sw, sh)
    val cur = if (slot == 0) h.radius else h.radius2
    if (cur != smallRadius) {
        val cm = android.graphics.ColorMatrix().apply { setSaturation(1.8f) }
        node.setRenderEffect(
            RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(cm),
                RenderEffect.createBlurEffect(smallRadius, smallRadius, Shader.TileMode.CLAMP)
            )
        )
        if (slot == 0) h.radius = smallRadius else h.radius2 = smallRadius
    }
    val rb = node.beginRecording()
    try {
        rb.drawColor(bg.toArgb())
        rb.scale(1f / BLUR_DOWN, 1f / BLUR_DOWN)
        rb.translate(0f, -oy)
        rb.drawRenderNode(h.src)
    } finally {
        node.endRecording()
    }
    val bounds = Rect(0f, 0f, size.width, hh)
    h.layerPaint.alpha = a
    drawIntoCanvas { c ->
        c.save()
        c.translate(0f, oy)
        c.saveLayer(bounds, h.layerPaint)
        c.save()
        c.scale(BLUR_DOWN, BLUR_DOWN)
        c.nativeCanvas.drawRenderNode(node)
        c.restore()
        drawRect(tint, size = Size(size.width, hh))
        if (fade) {
            drawRect(
                brush = if (bottom) {
                    Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black, 1f to Color.Black, startY = 0f, endY = hh)
                } else {
                    Brush.verticalGradient(0f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent, startY = 0f, endY = hh)
                },
                size = Size(size.width, hh),
                blendMode = BlendMode.DstIn
            )
        }
        c.restore()
        c.restore()
    }
}


/**
 * A snapshot taken while a finger is still held on the screen (before the long-press fires), so the blur behind a
 * context menu is ready the moment the menu opens instead of arriving a few frames later.
 */
object BackdropCache {
    private var snap: Backdrop? = null
    private var at = 0L

    @Synchronized fun put(b: Backdrop?) {
        snap = b
        at = System.currentTimeMillis()
    }

    @Synchronized fun clear() {
        snap = null
    }

    /** Returns the prefetched snapshot once (and only if it is recent and the window size is unchanged). */
    @Synchronized fun take(view: View): Backdrop? {
        val b = snap ?: return null
        snap = null
        val decor = view.context.findActivity()?.window?.decorView ?: return null
        val fresh = System.currentTimeMillis() - at < 2500L
        return if (fresh && b.width == decor.width && b.height == decor.height) b else null
    }
}

/**
 * Runs the expensive first-time work of the blur path once, right after start-up (shader compile, PixelCopy,
 * RenderEffect), invisibly, so the first menu / long press is as smooth as the hundredth.
 */
@Composable
fun BlurWarmUp() {
    val view = LocalView.current
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600)
        runCatching { captureBackdrop(view) }
        kotlinx.coroutines.delay(900)
        on = false
    }
    if (on) {
        val cf = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.8f) }) }
        Spacer(
            Modifier
                .padding(top = 1.dp)
                .size(48.dp)
                .graphicsLayer { alpha = 0.02f; renderEffect = BlurEffect(6f, 6f, TileMode.Clamp) }
                .drawBehind {
                    drawRect(Color(0xFF808080), colorFilter = cf)
                    drawRect(Brush.verticalGradient(listOf(Color.White, Color.Black)))
                }
        )
    }
}
