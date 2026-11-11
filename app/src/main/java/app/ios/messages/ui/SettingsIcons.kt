package app.ios.messages.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Rounded coloured square behind a white glyph, like the icons in the iOS Settings list. */
@Composable
fun SettingsTile(color: Color, size: Dp = 30.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(color),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun BellGlyph() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            cubicTo(w * 0.78f, h * 0.08f, w * 0.8f, h * 0.35f, w * 0.8f, h * 0.55f)
            lineTo(w * 0.92f, h * 0.72f)
            lineTo(w * 0.08f, h * 0.72f)
            lineTo(w * 0.2f, h * 0.55f)
            cubicTo(w * 0.2f, h * 0.35f, w * 0.22f, h * 0.08f, w * 0.5f, h * 0.08f)
            close()
        }
        drawPath(path, Color.White)
        drawCircle(Color.White, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.88f))
    }
}

@Composable
fun TextGlyph() {
    Text("Aa", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun GlobeGlyph() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val sw = w * 0.09f
        drawCircle(Color.White, radius = w * 0.44f, style = Stroke(sw))
        drawOval(Color.White, topLeft = Offset(w * 0.3f, w * 0.06f), size = Size(w * 0.4f, w * 0.88f), style = Stroke(sw))
        drawLine(Color.White, Offset(w * 0.06f, w * 0.5f), Offset(w * 0.94f, w * 0.5f), sw, StrokeCap.Round)
    }
}

@Composable
fun SimGlyph(hole: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.04f)
            lineTo(w * 0.62f, h * 0.04f)
            lineTo(w * 0.84f, h * 0.26f)
            lineTo(w * 0.84f, h * 0.96f)
            lineTo(w * 0.22f, h * 0.96f)
            close()
        }
        drawPath(path, Color.White)
        drawRoundRect(hole, Offset(w * 0.36f, h * 0.5f), Size(w * 0.34f, h * 0.3f), CornerRadius(w * 0.05f))
    }
}

@Composable
fun ArchiveGlyph(hole: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color.White, Offset(w * 0.06f, h * 0.14f), Size(w * 0.88f, h * 0.24f), CornerRadius(w * 0.06f))
        drawRoundRect(Color.White, Offset(w * 0.12f, h * 0.42f), Size(w * 0.76f, h * 0.46f), CornerRadius(w * 0.06f))
        drawRoundRect(hole, Offset(w * 0.36f, h * 0.52f), Size(w * 0.28f, h * 0.09f), CornerRadius(w * 0.045f))
    }
}

@Composable
fun LockGlyph() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        drawArc(
            Color.White, 180f, 180f, false,
            topLeft = Offset(w * 0.28f, h * 0.08f), size = Size(w * 0.44f, h * 0.6f),
            style = Stroke(w * 0.1f)
        )
        drawRoundRect(Color.White, Offset(w * 0.16f, h * 0.44f), Size(w * 0.68f, h * 0.5f), CornerRadius(w * 0.1f))
    }
}
