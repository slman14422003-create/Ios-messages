package app.ios.messages.ui

import androidx.compose.ui.res.stringResource
import app.ios.messages.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Row highlight on press (gray, no ripple) like UITableView cells. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.iosClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val base = LocalPalette.current.pressed
    val color by animateColorAsState(
        if (pressed) base else base.copy(alpha = 0f),
        if (pressed) snap<Color>() else tween<Color>(260),
        label = "row"
    )
    this
        .drawBehind { drawRect(color) }
        .combinedClickable(
            interactionSource = src,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
}

/** Tap with no visual feedback (nav bar buttons). */
fun Modifier.plainClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

fun Modifier.dimClickable(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(
        if (pressed) 0.35f else 1f,
        if (pressed) snap<Float>() else tween<Float>(260),
        label = "dim"
    )
    this
        .graphicsLayer { alpha = a }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

fun Modifier.pressScale(src: InteractionSource, pressedScale: Float = 0.9f): Modifier = composed {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
fun rememberTick(): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

@Composable
fun rememberThud(): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { h.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Dp.Hairline).background(LocalPalette.current.separator))
}

/** Message bubble with the iOS tail. Body is inset by 6dp on the tail side so grouped bubbles align. */
class BubbleShape(private val mine: Boolean, private val tail: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tw = with(density) { 6.dp.toPx() }
        val r = with(density) { 18.dp.toPx() }.coerceAtMost(size.height / 2f)
        val w = size.width
        val h = size.height
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun fx(x: Float) = if (mine != rtl) x else w - x
        val right = w - tw // body right edge in "mine" coordinates

        val body = Path().apply {
            val l = fx(0f)
            val rr = fx(right)
            addRoundRect(
                RoundRect(minOf(l, rr), 0f, maxOf(l, rr), h, CornerRadius(r, r))
            )
        }
        if (!tail) return Outline.Generic(body)

        val t = Path().apply {
            moveTo(fx(right - r), h - r)
            lineTo(fx(right), h - r)
            cubicTo(fx(right), h - r * 0.30f, fx(right + tw * 0.40f), h - tw * 0.45f, fx(w), h)
            cubicTo(fx(w - tw * 0.55f), h, fx(right - tw * 0.55f), h, fx(right - r), h)
            close()
        }
        val out = Path()
        out.op(body, t, PathOperation.Union)
        return Outline.Generic(out)
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(" ")
        .filter { it.isNotEmpty() && it.first().isLetter() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

/** iOS-style contact avatar: gray gradient + initials, or silhouette for unknown numbers. */
@Composable
fun Avatar(name: String, size: Dp) {
    val p = LocalPalette.current
    val initials = remember(name) { initialsOf(name) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(p.avatarTop, p.avatarBottom))),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNotEmpty()) {
            Text(
                initials,
                color = Color.White,
                fontSize = (size.value * 0.40f).sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val c = Color.White.copy(alpha = 0.92f)
                drawCircle(c, radius = this.size.width * 0.17f, center = Offset(this.size.width / 2f, this.size.height * 0.38f))
                drawCircle(c, radius = this.size.width * 0.31f, center = Offset(this.size.width / 2f, this.size.height * 0.98f))
            }
        }
    }
}

/** iOS 26 style floating "glass" circular button. */
@Composable
fun GlassCircle(size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(src, 0.88f)
            .clip(CircleShape)
            .background(p.glass)
            .border(Dp.Hairline, p.separator, CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

private class MenuPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(8, maxOf(8, windowSize.width - popupContentSize.width - 8))
        val below = anchorBounds.bottom - 14
        val y = if (below + popupContentSize.height <= windowSize.height - 8) below
        else maxOf(8, anchorBounds.top - popupContentSize.height + 14)
        return IntOffset(x, y)
    }
}

@Composable
private fun MenuCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .shadow(
                22.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(if (dark) Color(0xFF2C2C2E) else Color(0xFFF8F8F8))
            .width(IntrinsicSize.Max)
            .widthIn(min = 210.dp, max = 300.dp),
        content = content
    )
}

@Composable
fun IosMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 520f)) }
    Popup(
        popupPositionProvider = remember { MenuPosition() },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Box(Modifier.padding(20.dp)) {
            MenuCard(
                Modifier.graphicsLayer {
                    val s = 0.78f + 0.22f * a.value
                    scaleX = s
                    scaleY = s
                    alpha = a.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
                content
            )
        }
    }
}

private object ZeroPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}

@Composable
fun ContextOverlay(
    bounds: Rect,
    menuAtEnd: Boolean,
    previewWidth: Int?,
    onDismiss: () -> Unit,
    preview: @Composable () -> Unit,
    menu: @Composable ColumnScope.() -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f)) }
    Popup(
        popupPositionProvider = ZeroPosition,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.42f * a.value)) }
                .plainClickable(onDismiss)
        ) {
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        Modifier.graphicsLayer {
                            val s = 1f + 0.035f * a.value
                            scaleX = s
                            scaleY = s
                        }
                    ) { preview() }
                    MenuCard(
                        Modifier.graphicsLayer {
                            val s = 0.7f + 0.3f * a.value
                            scaleX = s
                            scaleY = s
                            alpha = a.value.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(if (menuAtEnd) 1f else 0f, 0f)
                        },
                        menu
                    )
                }
            ) { ms, c ->
                val loose = c.copy(minWidth = 0, minHeight = 0)
                val pc = if (previewWidth != null) loose.copy(minWidth = previewWidth, maxWidth = previewWidth) else loose
                val pv = ms[0].measure(pc)
                val mn = ms[1].measure(loose)
                val gap = 10.dp.roundToPx()
                val margin = 12.dp.roundToPx()
                val maxTop = c.maxHeight - margin - pv.height - gap - mn.height
                val top = minOf(bounds.top.roundToInt(), maxTop).coerceAtLeast(margin * 2)
                val left = bounds.left.roundToInt().coerceIn(0, maxOf(0, c.maxWidth - pv.width))
                val rawX = if (menuAtEnd) left + pv.width - mn.width else left
                val mx = rawX.coerceIn(margin, maxOf(margin, c.maxWidth - mn.width - margin))
                layout(c.maxWidth, c.maxHeight) {
                    pv.place(left, top)
                    mn.place(mx, top + pv.height + gap)
                }
            }
        }
    }
}

@Composable
fun IosConfirm(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val dark = isSystemInDarkTheme()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F2)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = p.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
            Hairline()
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().iosClickable(onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.cancel), color = p.blue, fontSize = 17.sp) }
                Box(Modifier.width(Dp.Hairline).fillMaxHeight().background(p.separator))
                Box(
                    Modifier.weight(1f).fillMaxHeight().iosClickable(onConfirm),
                    contentAlignment = Alignment.Center
                ) { Text(confirmLabel, color = Red, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun IosMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().iosClickable(onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides p.label) {
                ProvideTextStyle(TextStyle(fontSize = 17.sp)) {
                    Box(Modifier.weight(1f)) { text() }
                }
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(12.dp))
                trailingIcon()
            }
        }
        Hairline(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
fun rtlSign(): Float = if (androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
