package app.ios.messages.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing

/**
 * One place for every animation curve in the app, tuned to UIKit's feel:
 * navigation = critically damped (no overshoot), small controls = quick spring with a hint of bounce,
 * fades = ease-in-out. Change a value here and the whole app follows.
 */
object Motion {
    const val PushMs = 420
    const val MenuMs = 240
    const val Parallax = 0.3333f
    const val PushDim = 0.2f
    const val MenuBlur = 40f
    const val MenuDim = 0.3f
    val PushEase = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val MenuEase = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)

    fun pushF(distance: Float = 1f): FiniteAnimationSpec<Float> =
        tween((PushMs * distance.coerceIn(0f, 1f)).toInt().coerceAtLeast(200), easing = PushEase)

    fun <T> push(): FiniteAnimationSpec<T> = tween(PushMs, easing = PushEase)

    /** Button press scale: fast down, soft settle on release. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 900f)

    /** Popup menus: pop from the anchor with a barely visible overshoot. */
    fun <T> menuIn(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 700f)
    fun <T> menuOut(): FiniteAnimationSpec<T> = tween(170, easing = EaseOutIos)

    /** Elements entering/leaving (send button, emoji button, SIM chip). */
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 820f)

    /** Layout changes (text field growing, list reorder, keyboard-like panels). */
    fun <T> layout(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)

    /** New message bubble flying up from the input bar. */
    fun <T> bubble(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 620f)

    /** UISwitch knob. */
    fun <T> knob(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 700f)

    /** Colour/opacity fades. */
    fun <T> fade(ms: Int = 250): FiniteAnimationSpec<T> = tween(ms, easing = EaseInOutIos)
}

val EaseOutIos = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
val EaseInOutIos = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
