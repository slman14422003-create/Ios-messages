package com.cloud.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CloudColors = darkColorScheme(
    primary = CloudAccent,
    onPrimary = CloudBlack,
    secondary = CloudGray,
    onSecondary = CloudBlack,
    background = CloudBlack,
    onBackground = CloudWhite,
    surface = CloudSurface,
    onSurface = CloudWhite,
    surfaceVariant = CloudSurfaceHigh,
    onSurfaceVariant = CloudGray,
    error = CloudDanger,
    outline = CloudOutline
)

private val CloudShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun CloudLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CloudColors,
        typography = CloudTypography,
        shapes = CloudShapes,
        content = content
    )
}
