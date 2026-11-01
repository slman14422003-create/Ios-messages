package app.ios.messages.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.AppState
import app.ios.messages.BannerEvent
import app.ios.messages.data.Prefs
import kotlinx.coroutines.delay

/** Everything the user can tweak about the banner. */
data class BannerLook(val color: Color, val alpha: Float, val anim: Int, val seconds: Int) {
    companion object {
        fun load(prefs: Prefs) = BannerLook(
            Color(prefs.notifColorArgb()), prefs.bannerAlpha, prefs.bannerAnim, prefs.bannerSeconds
        )
    }

    fun enter(): EnterTransition = when (anim) {
        1 -> slideInVertically(spring(dampingRatio = 1f, stiffness = 420f)) { -it } + fadeIn(tween(180))
        2 -> scaleIn(spring(dampingRatio = 0.62f, stiffness = 520f), initialScale = 0.55f, transformOrigin = TransformOrigin(0.5f, 0f)) + fadeIn(tween(160))
        3 -> fadeIn(tween(380))
        4 -> slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) { -it * 2 }
        else -> EnterTransition.None
    }

    fun exit(): ExitTransition = when (anim) {
        1 -> slideOutVertically(tween(240)) { -it } + fadeOut(tween(200))
        2 -> scaleOut(tween(200), targetScale = 0.7f, transformOrigin = TransformOrigin(0.5f, 0f)) + fadeOut(tween(200))
        3 -> fadeOut(tween(260))
        4 -> slideOutVertically(tween(220)) { -it }
        else -> ExitTransition.None
    }
}

/** Draws incoming-message banners at the top of the screen while the app is open. */
@Composable
fun InAppBannerHost(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var event by remember { mutableStateOf<BannerEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    var look by remember { mutableStateOf(BannerLook.load(Prefs(ctx))) }

    LaunchedEffect(Unit) {
        AppState.banners.collect { e ->
            look = BannerLook.load(Prefs(ctx))
            event = e
            visible = true
        }
    }
    LaunchedEffect(event) {
        if (event != null) {
            delay(look.seconds * 1000L)
            visible = false
        }
    }
    Box(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = visible && event != null,
            enter = look.enter(),
            exit = look.exit(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            event?.let { e ->
                BannerCard(
                    title = e.title,
                    body = e.body,
                    sub = e.simLabel,
                    look = look,
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                    onClick = { visible = false; onOpen(e.address) },
                    onDismiss = { visible = false }
                )
            }
        }
    }
}

@Composable
fun BannerCard(
    title: String,
    body: String,
    sub: String?,
    look: BannerLook,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(22.dp)
    val content = TextStyle(textDirection = TextDirection.Content)
    var drag = 0f
    Row(
        modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(look.color.copy(alpha = look.alpha))
            .pointerInput(onDismiss) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        drag += dy
                        if (drag < -24f) { onDismiss?.invoke(); drag = 0f }
                    }
                )
            }
            .then(if (onClick != null) Modifier.plainClickable(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = content
            )
            Text(
                body, color = Color.White.copy(alpha = 0.95f), fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = content
            )
        }
        if (sub != null) {
            Spacer(Modifier.width(10.dp))
            Text(sub, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
        }
    }
}
