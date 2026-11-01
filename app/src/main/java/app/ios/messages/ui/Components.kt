package app.ios.messages.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
        if (pressed) snap<Color>() else Motion.fade(300),
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
        if (pressed) snap<Float>() else Motion.fade(300),
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
        Motion.press(),
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
            .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
            .clip(CircleShape)
            .background(p.glass)
            .border(Dp.Hairline, p.separator, CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

private class MenuPosition(private val padPx: Int, startOriginX: Float) : PopupPositionProvider {
    var originX by mutableFloatStateOf(startOriginX)
    var originY by mutableFloatStateOf(0f)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(8, maxOf(8, windowSize.width - popupContentSize.width - 8))
        val below = anchorBounds.bottom - 14
        val fits = below + popupContentSize.height <= windowSize.height - 8
        val y = if (fits) below else maxOf(8, anchorBounds.top - popupContentSize.height + 14)
        val cardW = popupContentSize.width - 2 * padPx
        if (cardW > 0) {
            originX = ((anchorBounds.center.x - (x + padPx)).toFloat() / cardW).coerceIn(0.05f, 0.95f)
        }
        originY = if (fits) 0f else 1f
        return IntOffset(x, y)
    }
}

@Composable
private fun MenuCard(
    modifier: Modifier,
    snap: Backdrop?,
    origin: IntOffset,
    contentAlpha: () -> Float = { 1f },
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = LocalDark.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .shadow(
                24.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.28f)
            )
            .clip(shape)
            .border(0.5.dp, if (dark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.06f), shape)
    ) {
        GlassFill(snap, origin, dark, Modifier.matchParentSize())
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 210.dp, max = 300.dp)
                .graphicsLayer { alpha = contentAlpha() },
            content = content
        )
    }
}

@Composable
fun IosMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    originX: Float = 0.5f,
    content: @Composable ColumnScope.() -> Unit
) {
    val a = remember { Animatable(0f) }
    var alive by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf<Backdrop?>(null) }
    val host = LocalView.current
    LaunchedEffect(expanded) {
        if (expanded) {
            alive = true
            launch { snap = captureBackdrop(host) }
            a.animateTo(1f, Motion.menuIn())
        } else if (alive) {
            a.animateTo(0f, Motion.menuOut())
            alive = false
            snap = null
        }
    }
    if (!alive) return
    val padPx = with(LocalDensity.current) { 20.dp.roundToPx() }
    val provider = remember(padPx) { MenuPosition(padPx, originX) }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        val sp = rememberScreenPos()
        Box(Modifier.padding(20.dp).screenPos(sp)) {
            MenuCard(
                Modifier.graphicsLayer {
                    val s = 0.5f + 0.5f * a.value
                    scaleX = s
                    scaleY = s
                    alpha = (a.value * 2.4f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(provider.originX, provider.originY)
                },
                snap,
                sp.pos,
                contentAlpha = { ((a.value - 0.3f) / 0.5f).coerceIn(0f, 1f) },
                content = content
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
    var snap by remember { mutableStateOf<Backdrop?>(null) }
    val host = LocalView.current
    LaunchedEffect(Unit) {
        launch { snap = captureBackdrop(host) }
        a.animateTo(1f, Motion.menuIn())
    }
    val ready by animateFloatAsState(if (snap != null) 1f else 0f, Motion.fade(220), label = "overlayBlur")
    val dark = LocalDark.current
    Popup(
        popupPositionProvider = ZeroPosition,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        val screen = rememberScreenPos()
        val menuPos = rememberScreenPos()
        Box(Modifier.fillMaxSize().screenPos(screen).plainClickable(onDismiss)) {
            snap?.let { s ->
                GlassBackdrop(
                    s,
                    screen.pos,
                    Modifier.matchParentSize().graphicsLayer { alpha = (a.value * ready).coerceIn(0f, 1f) },
                    blurDp = 22f,
                    saturation = 1.5f
                )
            }
            Box(
                Modifier.matchParentSize().drawBehind {
                    drawRect(
                        (if (dark) Color.Black else Color(0xFF1C1C1E))
                            .copy(alpha = (if (dark) 0.34f else 0.16f) * a.value)
                    )
                }
            )
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
                    Box(Modifier.screenPos(menuPos)) {
                        MenuCard(
                            Modifier.graphicsLayer {
                                val s = 0.6f + 0.4f * a.value
                                scaleX = s
                                scaleY = s
                                alpha = (a.value * 1.6f).coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(if (menuAtEnd) 1f else 0f, 0f)
                            },
                            snap,
                            menuPos.pos,
                            contentAlpha = { ((a.value - 0.2f) / 0.6f).coerceIn(0f, 1f) },
                            content = menu
                        )
                    }
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

class AlertAction(
    val label: String,
    val bold: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun IosConfirm(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    IosAlert(
        title = title,
        actions = listOf(
            AlertAction(stringResource(R.string.cancel), onClick = onDismiss),
            AlertAction(confirmLabel, bold = true, destructive = true, onClick = onConfirm)
        ),
        onDismiss = onDismiss
    )
}

/** UIAlertController look: frosted card, centered title/message, optional content, hairline-separated actions. */
@Composable
fun IosAlert(
    title: String,
    message: String? = null,
    actions: List<AlertAction>,
    onDismiss: () -> Unit,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val p = LocalPalette.current
    val dark = LocalDark.current
    var snap by remember { mutableStateOf<Backdrop?>(null) }
    val host = LocalView.current
    LaunchedEffect(Unit) { snap = captureBackdrop(host) }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val sp = rememberScreenPos()
        val a = remember { Animatable(0f) }
        LaunchedEffect(Unit) { a.animateTo(1f, Motion.menuIn()) }
        val shape = RoundedCornerShape(14.dp)
        Box(Modifier.screenPos(sp).width(270.dp)) {
            Box(
                Modifier
                    .graphicsLayer {
                        val s = 1.08f - 0.08f * a.value
                        scaleX = s
                        scaleY = s
                        alpha = (a.value * 2f).coerceIn(0f, 1f)
                    }
                    .clip(shape)
            ) {
                GlassFill(snap, sp.pos, dark, Modifier.matchParentSize(), blurDp = 30f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (header != null) header()
                        Text(
                            title,
                            color = p.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        if (message != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(message, color = p.label, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                        if (content != null) {
                            Spacer(Modifier.height(14.dp))
                            content()
                        }
                    }
                    Hairline()
                    if (actions.size == 2) {
                        Row(Modifier.fillMaxWidth().height(44.dp)) {
                            actions.forEachIndexed { i, act ->
                                if (i > 0) Box(Modifier.width(Dp.Hairline).fillMaxHeight().background(p.separator))
                                AlertButton(act, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    } else {
                        actions.forEachIndexed { i, act ->
                            if (i > 0) Hairline()
                            AlertButton(act, Modifier.fillMaxWidth().height(44.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertButton(act: AlertAction, modifier: Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.then(if (act.enabled) Modifier.iosClickable(act.onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            act.label,
            color = when {
                !act.enabled -> p.tertiary
                act.destructive -> Red
                else -> p.blue
            },
            fontSize = 17.sp,
            fontWeight = if (act.bold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/** UITextField inside an alert. */
@Composable
fun IosField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(p.fieldFill)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = p.label),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.blue),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) Text(placeholder, color = p.secondary, fontSize = 15.sp)
    }
}

/** Row highlight inside glass menus: translucent so the blur stays visible. */
fun Modifier.menuClickable(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val dark = LocalDark.current
    val base = if (dark) Color(0x30FFFFFF) else Color(0x1C000000)
    val color by animateColorAsState(
        if (pressed) base else base.copy(alpha = 0f),
        if (pressed) snap<Color>() else Motion.fade(280),
        label = "menuRow"
    )
    this
        .drawBehind { drawRect(color) }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun IosMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().menuClickable(onClick).heightIn(min = 46.dp).padding(horizontal = 16.dp),
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
}

@Composable
fun rtlSign(): Float = if (androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

/** iPhone-style menu row: checkmark column, optional icon, label. No hairlines between rows. */
@Composable
fun IosMenuRow(
    checked: Boolean = false,
    checkColumn: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    label: @Composable () -> Unit
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().menuClickable(onClick).heightIn(min = 46.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checkColumn) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.CenterStart) {
                if (checked) CheckMark(p.label, Modifier.size(15.dp))
            }
        }
        if (icon != null) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) { icon() }
        }
        CompositionLocalProvider(LocalContentColor provides p.label) {
            ProvideTextStyle(TextStyle(fontSize = 17.sp)) {
                Box(Modifier.weight(1f)) { label() }
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun IosMenuHeader(text: String) {
    val p = LocalPalette.current
    Text(
        text,
        color = p.secondary,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun IosMenuDivider() {
    val dark = LocalDark.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .height(1.dp)
            .background(if (dark) Color(0x33FFFFFF) else Color(0x1F000000))
    )
}
