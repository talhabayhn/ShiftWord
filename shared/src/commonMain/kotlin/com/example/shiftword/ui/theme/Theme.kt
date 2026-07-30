package com.example.shiftword.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = DustyLavender,
    onPrimary = SurfaceWhite,
    secondary = SageGreen,
    onSecondary = SurfaceWhite,
    tertiary = CreamBackground,
    onTertiary = TextPrimary,
    background = CreamBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    secondaryContainer = SurfaceWhite,
    onSecondaryContainer = TextPrimary,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(TileCornerRadius),
    medium = RoundedCornerShape(TileCornerRadius),
    large = RoundedCornerShape(CardCornerRadius),
)

@Composable
fun WordShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = appTypography(),
        shapes = AppShapes,
        content = content,
    )
}
