package app.ios.messages.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors taken from Apple's system palette (UIKit semantic colors). */
@Immutable
class Palette(
    val bg: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val fieldFill: Color,
    val received: Color,
    val receivedText: Color,
    val bar: Color,
    val blue: Color,
    val pressed: Color,
    val outline: Color,
    val avatarTop: Color,
    val avatarBottom: Color,
    val glass: Color
)

/** iOS shows SMS bubbles green. Change to `Blue` below if you prefer iMessage-blue. */
val SentBubble = Color(0xFF34C759)
val Red = Color(0xFFFF3B30)

val LightPalette = Palette(
    bg = Color.White,
    label = Color.Black,
    secondary = Color(0xFF8A8A8E),
    tertiary = Color(0xFFC4C4C7),
    separator = Color(0x4A3C3C43),
    fieldFill = Color(0x1F767680),
    received = Color(0xFFE9E9EB),
    receivedText = Color.Black,
    bar = Color(0xD9F9F9F9),
    blue = Color(0xFF007AFF),
    pressed = Color(0xFFD1D1D6),
    outline = Color(0xFFC6C6C8),
    avatarTop = Color(0xFFA6AAB4),
    avatarBottom = Color(0xFF828690),
    glass = Color(0xF0F2F2F7)
)

val DarkPalette = Palette(
    bg = Color.Black,
    label = Color.White,
    secondary = Color(0xFF8D8D93),
    tertiary = Color(0xFF58585C),
    separator = Color(0xFF38383A),
    fieldFill = Color(0x3D767680),
    received = Color(0xFF262628),
    receivedText = Color.White,
    bar = Color(0xD9161618),
    blue = Color(0xFF0A84FF),
    pressed = Color(0xFF2C2C2E),
    outline = Color(0xFF3A3A3C),
    avatarTop = Color(0xFF6D717A),
    avatarBottom = Color(0xFF4B4E55),
    glass = Color(0xF01C1C1E)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun IMessageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}
