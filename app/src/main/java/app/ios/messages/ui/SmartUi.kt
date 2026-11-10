package app.ios.messages.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ios.messages.data.Intel
import kotlinx.coroutines.delay

@Composable
fun SmartReplyBar(replies: List<String>, visible: Boolean, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    AnimatedVisibility(
        visible = visible && replies.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(Motion.fade(180)) + slideInVertically(Motion.pop()) { it / 2 },
        exit = fadeOut(Motion.fade(120)) + slideOutVertically(Motion.pop()) { it / 2 },
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shape = RoundedCornerShape(18.dp)
            replies.forEach { r ->
                Text(
                    r,
                    color = p.blue,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(shape)
                        .background(p.bar)
                        .border(0.5.dp, p.tertiary.copy(alpha = 0.4f), shape)
                        .iosClickable({ onPick(r) })
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Phone number currently on the clipboard (re-read shortly after every resume), or null. */
@Composable
fun rememberClipboardNumber(): String? {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    var value by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tick) {
        delay(350)
        value = Intel.clipboardNumber(context)
    }
    return value
}
