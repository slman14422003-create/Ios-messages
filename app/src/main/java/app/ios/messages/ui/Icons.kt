package app.ios.messages.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun ChevronLeft(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 13.dp, height = 22.dp)) {
        val sw = 2.8.dp.toPx()
        val path = Path().apply {
            moveTo(size.width - sw / 2, sw / 2)
            lineTo(sw / 2, size.height / 2)
            lineTo(size.width - sw / 2, size.height - sw / 2)
        }
        drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ChevronRight(color: Color, modifier: Modifier = Modifier, width: Int = 8, height: Int = 14, stroke: Float = 2f) {
    Canvas(modifier.size(width.dp, height.dp)) {
        val sw = stroke.dp.toPx()
        val path = Path().apply {
            moveTo(sw / 2, sw / 2)
            lineTo(size.width - sw / 2, size.height / 2)
            lineTo(sw / 2, size.height - sw / 2)
        }
        drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val sw = 2.2.dp.toPx()
        val m = size.width * 0.12f
        drawLine(color, Offset(m, size.height / 2), Offset(size.width - m, size.height / 2), sw, StrokeCap.Round)
        drawLine(color, Offset(size.width / 2, m), Offset(size.width / 2, size.height - m), sw, StrokeCap.Round)
    }
}

@Composable
fun SendArrow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.4.dp.toPx()
        drawLine(color, Offset(w / 2, h * 0.76f), Offset(w / 2, h * 0.27f), sw, StrokeCap.Round)
        val head = Path().apply {
            moveTo(w * 0.31f, h * 0.45f)
            lineTo(w / 2, h * 0.26f)
            lineTo(w * 0.69f, h * 0.45f)
        }
        drawPath(head, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun SearchIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(15.dp)) {
        val sw = 1.8.dp.toPx()
        val w = size.width
        drawCircle(color, radius = w * 0.31f, center = Offset(w * 0.42f, w * 0.42f), style = Stroke(sw))
        drawLine(color, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.92f, w * 0.92f), sw, StrokeCap.Round)
    }
}

@Composable
fun ComposeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.dp.toPx()
        val box = Path().apply {
            moveTo(w * 0.58f, h * 0.12f)
            lineTo(w * 0.30f, h * 0.12f)
            quadraticBezierTo(w * 0.12f, h * 0.12f, w * 0.12f, h * 0.30f)
            lineTo(w * 0.12f, h * 0.70f)
            quadraticBezierTo(w * 0.12f, h * 0.88f, w * 0.30f, h * 0.88f)
            lineTo(w * 0.70f, h * 0.88f)
            quadraticBezierTo(w * 0.88f, h * 0.88f, w * 0.88f, h * 0.70f)
            lineTo(w * 0.88f, h * 0.42f)
        }
        drawPath(box, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(w * 0.45f, h * 0.56f), Offset(w * 0.86f, h * 0.15f), sw * 1.3f, StrokeCap.Round)
    }
}

@Composable
fun CheckMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        val sw = size.width * 0.14f
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.80f)
            lineTo(size.width * 0.88f, size.height * 0.20f)
        }
        drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val c = center
        val w = size.width
        val sw = 1.9.dp.toPx()
        drawCircle(color, radius = w * 0.26f, center = c, style = Stroke(sw))
        drawCircle(color, radius = w * 0.09f, center = c, style = Stroke(sw))
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                drawLine(color, Offset(c.x, c.y - w * 0.33f), Offset(c.x, c.y - w * 0.46f), sw * 1.7f, StrokeCap.Round)
            }
        }
    }
}
