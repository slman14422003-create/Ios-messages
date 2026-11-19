package app.ios.messages.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ios.messages.R
import java.io.File

private class InkStroke(val color: Color, val width: Float) {
    val points = mutableStateListOf<Offset>()
}

private val inkColors = listOf(
    Color.Black, Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00),
    Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFAF52DE)
)

private fun strokePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
        return path
    }
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        path.quadraticBezierTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun save(ctx: Context, strokes: List<InkStroke>, size: IntSize): Uri? = runCatching {
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (s in strokes) {
        paint.color = android.graphics.Color.argb(
            (s.color.alpha * 255).toInt(), (s.color.red * 255).toInt(), (s.color.green * 255).toInt(), (s.color.blue * 255).toInt()
        )
        paint.strokeWidth = s.width
        canvas.drawPath(strokePath(s.points).asAndroidPath(), paint)
    }
    val dir = File(ctx.cacheDir, "mms").apply { mkdirs() }
    val f = File(dir, "draw_${System.currentTimeMillis()}.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
}.getOrNull()

@Composable
fun DrawingSheet(onDone: (Uri) -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var colorIdx by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = ctx.resources.displayMetrics.density
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(p.bg)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.cancel), color = p.blue, fontSize = 17.sp, modifier = Modifier.clickable { onCancel() })
                Text(
                    stringResource(R.string.draw_clear),
                    color = if (strokes.isEmpty()) p.tertiary else Red,
                    fontSize = 17.sp,
                    modifier = Modifier.clickable(enabled = strokes.isNotEmpty()) { strokes.clear() }
                )
                Text(
                    stringResource(R.string.draw_done),
                    color = if (strokes.isEmpty()) p.tertiary else p.blue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = strokes.isNotEmpty()) {
                        save(ctx, strokes, canvasSize)?.let(onDone)
                    }
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(colorIdx) {
                        detectDragGestures(
                            onDragStart = { o ->
                                val s = InkStroke(inkColors[colorIdx], 4f * density)
                                s.points.add(o)
                                strokes.add(s)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                strokes.lastOrNull()?.points?.add(change.position)
                            }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    for (s in strokes) {
                        drawPath(
                            strokePath(s.points),
                            s.color,
                            style = Stroke(s.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                inkColors.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(if (i == colorIdx) 34.dp else 28.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (i == colorIdx) 3.dp else 1.dp, if (i == colorIdx) p.blue else p.outline, CircleShape)
                            .clickable { colorIdx = i }
                    )
                }
            }
        }
    }
}
