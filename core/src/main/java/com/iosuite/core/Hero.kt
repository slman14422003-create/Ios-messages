package com.iosuite.core

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A rectangle in screen pixels. */
class HeroRect(val x: Int, val y: Int, val w: Int, val h: Int)

/** What travels with the intent: who is being shown and where it sits on the screen that is leaving. */
class Hero(
    val name: String,
    val photo: String?,
    val avatar: HeroRect,
    val nameRect: HeroRect?,
)

object HeroIntent {
    private const val KEY = "com.iosuite.hero"

    fun put(intent: Intent, hero: Hero) {
        val b = Bundle()
        b.putString("name", hero.name)
        b.putString("photo", hero.photo)
        b.putIntArray("avatar", intArrayOf(hero.avatar.x, hero.avatar.y, hero.avatar.w, hero.avatar.h))
        hero.nameRect?.let { b.putIntArray("nameRect", intArrayOf(it.x, it.y, it.w, it.h)) }
        intent.putExtra(KEY, b)
    }

    fun read(intent: Intent?): Hero? {
        val b = intent?.getBundleExtra(KEY) ?: return null
        val a = b.getIntArray("avatar")?.takeIf { it.size == 4 } ?: return null
        val n = b.getIntArray("nameRect")?.takeIf { it.size == 4 }
        return Hero(
            b.getString("name").orEmpty(),
            b.getString("photo"),
            HeroRect(a[0], a[1], a[2], a[3]),
            n?.let { HeroRect(it[0], it[1], it[2], it[3]) },
        )
    }
}

/** Remembers where an avatar / name sits so a tap can capture it without any per-frame work. */
class HeroAnchor {
    internal var coords: LayoutCoordinates? = null

    fun rect(): HeroRect? {
        val c = coords?.takeIf { it.isAttached } ?: return null
        val p = c.positionOnScreen()
        if (c.size.width <= 0 || c.size.height <= 0) return null
        return HeroRect(Math.round(p.x), Math.round(p.y), c.size.width, c.size.height)
    }
}

@Composable
fun rememberHeroAnchor(): HeroAnchor = remember { HeroAnchor() }

fun Modifier.heroAnchor(anchor: HeroAnchor): Modifier = onGloballyPositioned { anchor.coords = it }

fun heroOf(name: String, photo: String?, avatar: HeroAnchor, nameAnchor: HeroAnchor?): Hero? {
    val a = avatar.rect() ?: return null
    return Hero(name, photo, a, nameAnchor?.rect())
}

/**
 * Landing side of a shared-element hop. The activity that was opened with a [Hero] publishes it here;
 * the screen that shows the contact marks its avatar / name with [heroTarget], and [HeroHost.Overlay]
 * flies the visuals from the old position to the real one, then hands over.
 */
object HeroHost {
    private const val WAIT_MS = 700L

    class Landing(val hero: Hero) {
        val started = SystemClock.uptimeMillis()
        var avatarTarget by mutableStateOf<HeroRect?>(null)
        var nameTarget by mutableStateOf<HeroRect?>(null)
        var active by mutableStateOf(true)
        fun expired() = SystemClock.uptimeMillis() - started > WAIT_MS * 3
    }

    var landing by mutableStateOf<Landing?>(null)
        private set

    fun accept(hero: Hero?) {
        landing = hero?.let { Landing(it) }
    }

    fun accept(intent: Intent?) = accept(HeroIntent.read(intent))

    /**
     * Put this last in the activity's root. [avatar] draws the contact's avatar at the given size,
     * [name] draws the name in the destination's own text style (both are laid out once and then only scaled).
     */
    @Composable
    fun Overlay(
        avatar: @Composable (Dp) -> Unit,
        name: (@Composable (String) -> Unit)? = null,
    ) {
        val l = landing ?: return
        if (!l.active) return
        val progress = remember(l) { Animatable(0f) }
        val target = l.avatarTarget
        LaunchedEffect(l, target != null) {
            if (target == null) {
                kotlinx.coroutines.delay(WAIT_MS)
                if (l.avatarTarget == null) l.active = false
                return@LaunchedEffect
            }
            progress.animateTo(1f, tween(Motion.PushMs, easing = Motion.PushEase))
            l.active = false
        }
        if (target == null) return
        val density = LocalDensity.current
        var origin by remember { mutableStateOf(Offset.Zero) }
        val src = l.hero.avatar
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionOnScreen() }
        ) {
            val tgtDp = with(density) { target.w.toDp() }
            Box(
                Modifier
                    .size(tgtDp, with(density) { target.h.toDp() })
                    .graphicsLayer {
                        val p = progress.value
                        val sx = lerp(src.w.toFloat() / target.w, 1f, p)
                        val sy = lerp(src.h.toFloat() / target.h, 1f, p)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = sx
                        scaleY = sy
                        translationX = lerp(src.x.toFloat(), target.x.toFloat(), p) - origin.x
                        translationY = lerp(src.y.toFloat(), target.y.toFloat(), p) - origin.y
                    }
            ) { avatar(tgtDp) }
            val nameSrc = l.hero.nameRect
            val nameTgt = l.nameTarget
            if (name != null && nameSrc != null && nameTgt != null) {
                Box(
                    Modifier
                        .size(with(density) { nameTgt.w.toDp() }, with(density) { nameTgt.h.toDp() })
                        .graphicsLayer {
                            val p = progress.value
                            val s = lerp(nameSrc.h.toFloat() / nameTgt.h, 1f, p)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = s
                            scaleY = s
                            translationX = lerp(nameSrc.x.toFloat(), nameTgt.x.toFloat(), p) - origin.x
                            translationY = lerp(nameSrc.y.toFloat(), nameTgt.y.toFloat(), p) - origin.y
                        }
                ) { name(l.hero.name) }
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/** Mark the avatar / name of the destination screen. Hidden while the flying copy is on its way. */
fun Modifier.heroTarget(isName: Boolean): Modifier {
    val l = HeroHost.landing ?: return this
    return this
        .onGloballyPositioned {
            if (!l.active) return@onGloballyPositioned
            val p = it.positionOnScreen()
            val r = HeroRect(Math.round(p.x), Math.round(p.y), it.size.width, it.size.height)
            if (r.w <= 0 || r.h <= 0) return@onGloballyPositioned
            if (isName) l.nameTarget = r else l.avatarTarget = r
        }
        .graphicsLayer { alpha = if (l.active) 0f else 1f }
}
