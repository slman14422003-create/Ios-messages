package com.cloud.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** تأثير زجاجي بأسلوب iOS 26 (Liquid Glass): تدرج شفاف + حد لامع رفيع. */
fun Modifier.liquidGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF))
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color(0x80FFFFFF), Color(0x1FFFFFFF), Color(0x55FFFFFF))
        ),
        shape = shape
    )
