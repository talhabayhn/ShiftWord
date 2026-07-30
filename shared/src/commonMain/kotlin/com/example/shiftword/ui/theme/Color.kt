package com.example.shiftword.ui.theme

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
 */
val CreamBackground = Color(0xFFFAF7F2)
val DustyLavender = Color(0xFFC4B8D9)
val LavenderTileTint = Color(0xFFE8E2F0)
val SageGreen = Color(0xFFA8C4A0)
val WarmSand = Color(0xFFE7DCC8)
val SoftCoral = Color(0xFFE8A598)
val TextPrimary = Color(0xFF4A4453)
val StarEmpty = Color(0xFFD4D0CC)
val SurfaceWhite = Color(0xFFFFFFFF)
