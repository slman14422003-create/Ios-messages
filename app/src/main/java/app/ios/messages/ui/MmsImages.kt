package app.ios.messages.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MmsImages {
    private val cache = LruCache<String, ImageBitmap>(24)

    fun cached(key: String): ImageBitmap? = cache.get(key)

    fun load(ctx: Context, uri: String, maxSide: Int = 900): ImageBitmap? {
        val key = "$uri@$maxSide"
        cache.get(key)?.let { return it }
        return try {
            val cr = ctx.contentResolver
            val u = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
            bmp?.asImageBitmap()?.also { cache.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun rememberMmsImage(uri: String, maxSide: Int = 900): ImageBitmap? {
    val ctx = LocalContext.current
    val state by produceState<ImageBitmap?>(MmsImages.cached("$uri@$maxSide"), uri, maxSide) {
        if (value == null) value = withContext(Dispatchers.IO) { MmsImages.load(ctx, uri, maxSide) }
    }
    return state
}

@Composable
fun MmsImage(uri: String, maxW: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val bmp = rememberMmsImage(uri)
    val ratio = if (bmp != null) bmp.width.toFloat() / bmp.height else 1.33f
    Box(
        modifier
            .width(maxW * 0.85f)
            .aspectRatio(ratio.coerceIn(0.5f, 2.2f))
            .clip(RoundedCornerShape(18.dp))
            .background(p.received)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
