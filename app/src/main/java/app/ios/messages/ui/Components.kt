package app.ios.messages.ui

import androidx.compose.foundation.Canvas
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
    this
        .background(if (pressed) LocalPalette.current.pressed else Color.Transparent)
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
        fun fx(x: Float) = if (mine) x else w - x
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
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(p.glass)
            .border(Dp.Hairline, p.separator, CircleShape)
            .plainClickable(onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
