package app.ios.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.R
import app.ios.messages.data.BubbleConfig

@Immutable
class BubbleStyle(
    val top: Color,
    val bottom: Color,
    val received: Int,
    val tails: Boolean,
    val chatBg: Int,
    val gradient: Boolean
) {
    fun sentBrush(): Brush = if (gradient) Brush.verticalGradient(listOf(top, bottom)) else SolidColor(bottom)

    /** Flat colour that matches the chat background (used by the cheap bars that fade the list edges). */
    fun chatBase(bg: Color, dark: Boolean): Color = when (chatBg) {
        1 -> bottom.copy(alpha = if (dark) 0.16f else 0.07f).compositeOver(bg)
        2 -> lerp(top, bottom, 0.5f).copy(alpha = if (dark) 0.12f else 0.06f).compositeOver(bg)
        else -> bg
    }

    fun chatBrush(bg: Color, dark: Boolean): Brush? =
        if (chatBg != 2) null
        else Brush.verticalGradient(
            listOf(
                top.copy(alpha = if (dark) 0.22f else 0.12f).compositeOver(bg),
                bottom.copy(alpha = if (dark) 0.06f else 0.03f).compositeOver(bg)
            )
        )

    companion object {
        val Default = BubbleStyle(Color(0xFF4ADE6B), Color(0xFF34C759), 0, true, 0, true)
    }
}

class BubblePreset(val id: String, val name: Int, val top: Color, val bottom: Color)

object BubbleThemes {
    val presets = listOf(
        BubblePreset("green", R.string.theme_green, Color(0xFF4ADE6B), Color(0xFF34C759)),
        BubblePreset("blue", R.string.theme_blue, Color(0xFF2E9BFF), Color(0xFF007AFF)),
        BubblePreset("sunset", R.string.theme_sunset, Color(0xFFFF9F0A), Color(0xFFFF375F)),
        BubblePreset("ocean", R.string.theme_ocean, Color(0xFF5AC8FA), Color(0xFF5856D6)),
        BubblePreset("grape", R.string.theme_grape, Color(0xFFBF5AF2), Color(0xFF5E5CE6)),
        BubblePreset("mint", R.string.theme_mint, Color(0xFF63E6BE), Color(0xFF30B0C7)),
        BubblePreset("rose", R.string.theme_rose, Color(0xFFFF6482), Color(0xFFFF2D55)),
        BubblePreset("graphite", R.string.theme_graphite, Color(0xFF8E8E93), Color(0xFF48484A))
    )

    fun colors(id: String, hue: Float): Pair<Color, Color> {
        if (id == "custom") return Color.hsv(hue, 0.70f, 1f) to Color.hsv(hue, 0.88f, 0.86f)
        val p = presets.firstOrNull { it.id == id } ?: presets[0]
        return p.top to p.bottom
    }
}

fun BubbleConfig.style(override: String? = null): BubbleStyle {
    val id = override?.takeIf { it.isNotEmpty() } ?: theme
    val (top, bottom) = BubbleThemes.colors(id, hue)
    return BubbleStyle(top, bottom, received, tails, chatBg, gradient)
}

val LocalBubbleStyle = staticCompositionLocalOf { BubbleStyle.Default }
val LocalBubbleConfig = staticCompositionLocalOf { BubbleConfig() }

@Composable
fun Modifier.bubbleFill(style: BubbleStyle, mine: Boolean, shape: Shape): Modifier {
    val p = LocalPalette.current
    val dark = LocalDark.current
    return if (mine) {
        val brush = remember(style) { style.sentBrush() }
        background(brush, shape)
    } else when (style.received) {
        1 -> background(style.bottom.copy(alpha = if (dark) 0.30f else 0.16f).compositeOver(p.bg), shape)
        2 -> border(1.2.dp, style.bottom.copy(alpha = 0.7f), shape)
        else -> background(p.received, shape)
    }
}

@Composable
fun BubblePreview(style: BubbleStyle, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val dark = LocalDark.current
    val base = remember(style, p, dark) { style.chatBase(p.bg, dark) }
    val brush = remember(style, p, dark) { style.chatBrush(p.bg, dark) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(base))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        PreviewBubble(style, false, stringResource(R.string.theme_preview_in), true)
        Box(Modifier.height(6.dp))
        PreviewBubble(style, true, stringResource(R.string.theme_preview_out), false)
        PreviewBubble(style, true, stringResource(R.string.theme_preview_out2), true)
    }
}

@Composable
private fun PreviewBubble(style: BubbleStyle, mine: Boolean, text: String, tail: Boolean) {
    val p = LocalPalette.current
    val shape = remember(mine, tail, style.tails) { BubbleShape(mine, tail && style.tails) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            color = if (mine) Color.White else p.receivedText,
            fontSize = 16.sp,
            modifier = Modifier
                .bubbleFill(style, mine, shape)
                .padding(
                    start = if (mine) 12.dp else 18.dp,
                    end = if (mine) 18.dp else 12.dp,
                    top = 7.dp,
                    bottom = 7.dp
                )
        )
    }
}

@Composable
fun ThemeSwatches(selected: String, hue: Float, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val rainbow = remember { Brush.sweepGradient((0..6).map { Color.hsv(it * 60f, 0.8f, 1f) }) }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BubbleThemes.presets.forEach { t ->
            Swatch(Brush.verticalGradient(listOf(t.top, t.bottom)), selected == t.id) { onSelect(t.id) }
        }
        val (ct, cb) = BubbleThemes.colors("custom", hue)
        Swatch(rainbow, selected == "custom", inner = Brush.verticalGradient(listOf(ct, cb))) { onSelect("custom") }
    }
}

@Composable
private fun Swatch(fill: Brush, on: Boolean, inner: Brush? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (on) Modifier.border(2.5.dp, p.label.copy(alpha = 0.85f), CircleShape) else Modifier)
            .plainClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        if (inner != null) Box(Modifier.size(22.dp).clip(CircleShape).background(inner))
        if (on) CheckMark(Color.White)
    }
}

@Composable
fun HueSlider(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val rainbow = remember { Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 0.85f, 1f) }) }
    var w by remember { mutableFloatStateOf(1f) }
    val change by rememberUpdatedState(onChange)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .height(34.dp)
                .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { change((it.x / w * 360f).coerceIn(0f, 359f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { c, _ ->
                        c.consume()
                        change((c.position.x / w * 360f).coerceIn(0f, 359f))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(rainbow))
            val x = with(LocalDensity.current) { (hue / 360f * w).toDp() }
            Box(
                Modifier
                    .absoluteOffset(x = x - 15.dp)
                    .size(30.dp)
                    .shadow(3.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .padding(4.dp)
                    .background(Color.hsv(hue, 0.85f, 0.95f), CircleShape)
            )
        }
    }
}
