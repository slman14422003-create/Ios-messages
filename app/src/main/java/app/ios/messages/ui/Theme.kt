package app.ios.messages.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
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
    val glass: Color,
    val groupBg: Color,
    val cell: Color
)

/** iOS shows SMS bubbles green. Change to `Blue` below if you prefer iMessage-blue. */
val SentBubble = Color(0xFF34C759)
val Red = Color(0xFFFF3B30)
val IosBlue = Color(0xFF007AFF)

val LocalSentColor = staticCompositionLocalOf { SentBubble }

val LightPalette = Palette(
    bg = Color.White,
    label = Color.Black,
    secondary = Color(0xFF8A8A8E),
    tertiary = Color(0xFFC4C4C7),
    separator = Color(0x4A3C3C43),
    fieldFill = Color(0x1F787880),
    received = Color(0xFFE9E9EB),
    receivedText = Color.Black,
    bar = Color(0xD9F9F9F9),
    blue = Color(0xFF007AFF),
    pressed = Color(0xFFD1D1D6),
    outline = Color(0xFFC6C6C8),
    avatarTop = Color(0xFFB7BFDB),
    avatarBottom = Color(0xFF8E98BD),
    glass = Color(0xA8FFFFFF),
    groupBg = Color(0xFFF2F2F7),
    cell = Color.White
)

val DarkPalette = Palette(
    bg = Color.Black,
    label = Color.White,
    secondary = Color(0xFF8D8D93),
    tertiary = Color(0xFF58585C),
    separator = Color(0x99545458),
    fieldFill = Color(0x5C787880),
    received = Color(0xFF262629),
    receivedText = Color.White,
    bar = Color(0xD9161618),
    blue = Color(0xFF0A84FF),
    pressed = Color(0xFF2C2C2E),
    outline = Color(0xFF3A3A3C),
    avatarTop = Color(0xFF7B84A6),
    avatarBottom = Color(0xFF555D80),
    glass = Color(0x99303033),
    groupBg = Color.Black,
    cell = Color(0xFF1C1C1E)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalDark = staticCompositionLocalOf { false }

@Composable
fun IMessageTheme(
    bubble: BubbleStyle = BubbleStyle.Default,
    config: app.ios.messages.data.BubbleConfig = app.ios.messages.data.BubbleConfig(),
    appearance: Int = 0,
    content: @Composable () -> Unit
) {
    val dark = when (appearance) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(
        LocalPalette provides if (dark) DarkPalette else LightPalette,
        LocalDark provides dark,
        LocalSentColor provides bubble.bottom,
        LocalBubbleStyle provides bubble,
        LocalBubbleConfig provides config
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme()
        ) {
            ProvideTextStyle(TextStyle(letterSpacing = (-0.2).sp)) { content() }
        }
    }
}

/** Same type scale as the phone app, so titles, rows and footnotes line up between the two apps. */
object IosType {
    private val base = androidx.compose.ui.text.font.FontFamily.SansSerif
    private val semi = androidx.compose.ui.text.font.FontWeight.SemiBold
    private val normal = androidx.compose.ui.text.font.FontWeight.Normal
    val largeTitle = TextStyle(fontFamily = base, fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.37.sp)
    val title3 = TextStyle(fontFamily = base, fontSize = 20.sp, fontWeight = normal, letterSpacing = 0.38.sp)
    val headline = TextStyle(fontFamily = base, fontSize = 17.sp, fontWeight = semi, letterSpacing = (-0.43).sp)
    val body = TextStyle(fontFamily = base, fontSize = 17.sp, fontWeight = normal, letterSpacing = (-0.43).sp)
    val callout = TextStyle(fontFamily = base, fontSize = 15.sp, fontWeight = normal, letterSpacing = (-0.2).sp)
    val footnote = TextStyle(fontFamily = base, fontSize = 13.sp, fontWeight = normal, letterSpacing = (-0.08).sp)
}
