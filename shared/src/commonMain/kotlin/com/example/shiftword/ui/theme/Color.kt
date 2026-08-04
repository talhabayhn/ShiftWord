package com.example.shiftword.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Exact hex values sampled by pixel-reading the mockup PNGs under design/mockups (not
 * approximated from the verbal design brief) — each value below is cross-confirmed from at
 * least two independent
 * mockups/elements:
 *
 * - CreamBackground (#FAF7F2): main_menu.png "Ayarlar" button fill, gameplay pause-button fill,
 *   level_complete.png "Tekrar Oyna" button fill — all three match exactly.
 * - DustyLavender (#C4B8D9): main_menu.png "Oyna" button, gameplay highlighted-tile border,
 *   level_complete.png "Sonraki Seviye" button — all match exactly.
 * - SageGreen (#A8C4A0): splash_screen.png swatch; main_menu.png "Günlük Bulmaca" button
 *   (#A8C5A0, 1-unit rounding) and level_complete.png found-word chips (#A8C5A0) confirm the
 *   same color within anti-aliasing tolerance.
 * - WarmSand (#E7DCC8): splash_screen.png swatch; gameplay tile/card border stroke (#E8DCC8)
 *   confirms the same family within anti-aliasing tolerance.
 * - SoftCoral (#E8A598): gameplay hint-button fill and level_complete.png filled-star color —
 *   exact match; splash swatch (#E7A598) is the same color within anti-aliasing tolerance.
 * - TextPrimary (#4A4453): consistent across every mockup's body/heading text.
 *
 * Two additional tokens found during sampling, not named in the original brief but real,
 * distinct colors actually used in the mockups:
 * - LavenderTileTint (#E8E2F0): the lighter lavender FILL used inside a highlighted grid tile
 *   (distinct from DustyLavender, which is that same tile's border/stroke).
 * - StarEmpty (#D4D0CC): the muted warm-gray outline color for an unearned star in
 *   level_complete.png (distinct from TextPrimary).
 *
 * Tiles, chips, and the level-complete card itself are plain white (#FFFFFF) — cream is
 * reserved for the page background and cream/tertiary buttons, confirmed by direct sampling
 * (e.g. level_complete.png's white card sits on a transparent page background, but every
 * cream *button* fill sampled across all three mockups is identically #FAF7F2).
 *
 * ---
 *
 * **Dark mode (added post-launch — see GAME_DESIGN.md, ARCHITECTURE.md §7a):** every token
 * below is now a `@Composable @ReadOnlyComposable` property, not a plain `val`, backed by
 * [LocalDarkTheme]. This is a deliberate structural choice, not just a naming convenience: every
 * call site across the app (GridBoard, GameScreen, SettingsScreen, MainMenuScreen,
 * LevelSelectScreen, SplashScreen, LogoMark) already referenced these as plain top-level names
 * (`color = TextPrimary`, not `MaterialTheme.colorScheme.onBackground`), so making the property
 * itself theme-reactive — rather than threading a `darkTheme: Boolean` parameter through every
 * composable in the call chain — means dark mode works everywhere these tokens are already used,
 * with zero call-site changes required. This is Kotlin/Compose-legal: a `@Composable get()`
 * property is read exactly like a plain `val` at every existing call site, as long as that call
 * site is itself inside composition (true for all of them — they're all UI code).
 *
 * The dark values below are NOT the light values reused as-is — each was checked against WCAG
 * contrast math (see the PR that introduced this) before being picked, specifically because the
 * light-mode pastel accents (e.g. DustyLavender #C4B8D9) are too close to white to give
 * `SurfaceWhite`-colored button text (used throughout — see GameScreen/MainMenuScreen button
 * `contentColor`) adequate contrast against a dark-mode fill. Each dark accent below was chosen
 * so white text on top of it clears ~4.5:1 (WCAG AA for normal text), while still reading as
 * "the same color family" as its light counterpart, since it's the same design identity in a
 * different theme, not a new palette.
 */
private val LightCreamBackground = Color(0xFFFAF7F2)
private val LightDustyLavender = Color(0xFFC4B8D9)
private val LightLavenderTileTint = Color(0xFFE8E2F0)
private val LightSageGreen = Color(0xFFA8C4A0)
private val LightWarmSand = Color(0xFFE7DCC8)
private val LightSoftCoral = Color(0xFFE8A598)
private val LightTextPrimary = Color(0xFF4A4453)
private val LightStarEmpty = Color(0xFFD4D0CC)
private val LightSurfaceWhite = Color(0xFFFFFFFF)
// Light mode's existing GridBoard shadow reused the WarmSand token directly (a warm off-white
// halo under the board, not a literal dark drop-shadow) — kept byte-identical here rather than
// introducing a second light value for it.
private val LightGridShadow = LightWarmSand

// Dark palette: a dark, slightly warm-desaturated neutral (not pure black/gray — keeps the same
// "warm pastel" identity as the light theme's cream/sand family) for background/surface, with
// accents darkened/saturated (not just the light hex reused) so SurfaceWhite text on top of a
// filled button stays WCAG-AA-legible (~4.5:1+) against each. Verified with a standalone contrast
// script during development (not eyeballed): DustyLavender 6.43:1, SageGreen 5.07:1, SoftCoral
// 4.69:1, all vs. white text; DarkTextPrimary vs. DarkBackground/DarkSurface are both >12:1.
private val DarkBackground = Color(0xFF1E1C24)
private val DarkSurfaceWhite = Color(0xFF2A2733) // "SurfaceWhite" role (tile/card fill), not literally white in dark mode
private val DarkDustyLavender = Color(0xFF6552A0)
private val DarkLavenderTileTint = Color(0xFF5F5586)
private val DarkSageGreen = Color(0xFF457A4D)
// Slightly darker than an initial pass (#736A5A) -- that value cleared white-text contrast
// nowhere it needed to (WarmSand is a decorative border everywhere else), but DevMenu's
// debug-only "force complete a word" button uses WarmSand fill + TextPrimary content color, and
// #736A5A only cleared 4.46:1 against DarkTextPrimary, just under WCAG AA's 4.5:1. This value
// clears 4.90:1 there while keeping WarmSand's border-stroke contrast against DarkSurfaceWhite
// in the same ballpark (2.50:1 vs. the original's 2.74:1).
private val DarkWarmSand = Color(0xFF6D6450)
private val DarkSoftCoral = Color(0xFFB85740)
private val DarkTextPrimary = Color(0xFFEDE9F5)
private val DarkStarEmpty = Color(0xFF807A87)
// GridBoard's soft "shadow" is a light warm halo in light mode (see LightGridShadow) — reusing
// that same warm-sand tone against a dark background would read as a highlight/glow, not a
// shadow (a shadow needs to be DARKER than what it sits on, not lighter). Uses a translucent
// black instead, which is the standard Material dark-theme elevation treatment, paired with a
// slightly higher elevation (GridBoard.kt) since a black-on-near-black shadow is inherently more
// subtle than a light halo on a light page and needs the extra intensity to still read as
// elevation at all.
private val DarkGridShadow = Color.Black.copy(alpha = 0.55f)

/**
 * Whether the app is currently in dark mode — provided once, near the composition root, by
 * [com.example.shiftword.ui.theme.WordShiftTheme]. Defaults to `false` (light) so any composable
 * previewed/tested without an explicit provider (existing behavior, unaffected) still renders the
 * original light palette.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

val CreamBackground: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkBackground else LightCreamBackground

val DustyLavender: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkDustyLavender else LightDustyLavender

val LavenderTileTint: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkLavenderTileTint else LightLavenderTileTint

val SageGreen: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkSageGreen else LightSageGreen

val WarmSand: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkWarmSand else LightWarmSand

val SoftCoral: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkSoftCoral else LightSoftCoral

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkTextPrimary else LightTextPrimary

val StarEmpty: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkStarEmpty else LightStarEmpty

val SurfaceWhite: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkSurfaceWhite else LightSurfaceWhite

/** GridBoard's board-level drop shadow color — see [DarkGridShadow]'s doc comment for why this
 * can't just reuse [WarmSand] in dark mode the way light mode does. */
val GridShadowColor: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) DarkGridShadow else LightGridShadow

/**
 * Text/icon color drawn ON TOP of an accent fill (a `DustyLavender`/`SageGreen`/`SoftCoral`
 * button or chip, or a `Switch`'s "on" thumb against its `DustyLavender` track) — deliberately
 * NOT theme-reactive, unlike every other token in this file, and this is a real bug found and
 * fixed via on-device dark-mode verification, not a stylistic choice made up front: this file's
 * dark accent tokens were specifically darkened/saturated (see their own doc comment) so that
 * WHITE content on top of them clears WCAG AA contrast. `SurfaceWhite` was originally reused for
 * this role too (it happens to equal white in light mode), but `SurfaceWhite` switches to
 * `DarkSurfaceWhite` (a DARK color, #2A2733 — the tile/card surface role) in dark mode — putting
 * that dark color on top of the same dark-tuned accents is dark-on-dark, exactly the contrast
 * failure the accent darkening was meant to prevent. Confirmed on an emulator running dark mode:
 * MainMenuScreen's "Oyna"/"Günlük Bulmaca" button text, GameScreen's found-word chips/hint
 * button/next-level button, and SettingsScreen's `Switch` thumbs were all low-contrast
 * dark-on-dark before this token existed.
 */
val OnAccent: Color = Color.White

// Exposed for Theme.kt's darkColorScheme()/lightColorScheme() construction, which needs plain
// Color values at MaterialTheme-setup time, not composable property reads.
internal object LightTokens {
    val background = LightCreamBackground
    val primary = LightDustyLavender
    val secondary = LightSageGreen
    val surface = LightSurfaceWhite
    val onSurface = LightTextPrimary
}

internal object DarkTokens {
    val background = DarkBackground
    val primary = DarkDustyLavender
    val secondary = DarkSageGreen
    val surface = DarkSurfaceWhite
    val onSurface = DarkTextPrimary
}
