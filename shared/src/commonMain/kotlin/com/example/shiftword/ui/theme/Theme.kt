package com.example.shiftword.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightAppColorScheme = lightColorScheme(
    primary = LightTokens.primary,
    onPrimary = LightTokens.surface,
    secondary = LightTokens.secondary,
    onSecondary = LightTokens.surface,
    tertiary = LightTokens.background,
    onTertiary = LightTokens.onSurface,
    background = LightTokens.background,
    onBackground = LightTokens.onSurface,
    surface = LightTokens.surface,
    onSurface = LightTokens.onSurface,
    secondaryContainer = LightTokens.surface,
    onSecondaryContainer = LightTokens.onSurface,
)

// Dark mode (added post-launch, GAME_DESIGN.md/ARCHITECTURE.md §7a): mirrors LightAppColorScheme
// role-for-role, sourced from DarkTokens (Color.kt) rather than reusing the light Color objects,
// per that file's doc comment on why the light hex values would fail contrast if reused as-is.
private val DarkAppColorScheme = darkColorScheme(
    primary = DarkTokens.primary,
    onPrimary = DarkTokens.surface,
    secondary = DarkTokens.secondary,
    onSecondary = DarkTokens.surface,
    tertiary = DarkTokens.background,
    onTertiary = DarkTokens.onSurface,
    background = DarkTokens.background,
    onBackground = DarkTokens.onSurface,
    surface = DarkTokens.surface,
    onSurface = DarkTokens.onSurface,
    secondaryContainer = DarkTokens.surface,
    onSecondaryContainer = DarkTokens.onSurface,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(TileCornerRadius),
    medium = RoundedCornerShape(TileCornerRadius),
    large = RoundedCornerShape(CardCornerRadius),
)

/**
 * [darkTheme] drives two independent things, both required for dark mode to actually work
 * everywhere: (1) [MaterialTheme]'s own `colorScheme`, for the handful of call sites that read
 * `MaterialTheme.colorScheme.*` directly (App.kt's root background, GameScreen/MainMenuScreen/
 * LevelSelectScreen/SettingsScreen's screen backgrounds); and (2) [LocalDarkTheme], provided here
 * so every screen's plain-named token reads (`TextPrimary`, `SurfaceWhite`, etc. — see Color.kt's
 * doc comment) also switch, without those call sites needing a `darkTheme` parameter threaded
 * through them individually. Defaults to `false` (light) so every existing call site/preview that
 * doesn't pass this explicitly is pixel-identical to before dark mode existed.
 */
@Composable
fun WordShiftTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkAppColorScheme else LightAppColorScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
