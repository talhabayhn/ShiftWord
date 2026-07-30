package com.example.shiftword.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Corner radii measured directly from the mockup PNGs by detecting each shape's opaque-pixel
 * boundary (not eyeballed):
 *
 * - Grid tile (gameplay_mockup.png): ~120px tile, corner rounds out by ~9px inset from the
 *   edge → radius/tile-size ≈ 7.5%, a moderate rounding, clearly not a pill.
 * - Level-complete card (level_complete.png): ~679px-wide card, corner rounds out by ~64px →
 *   radius/width ≈ 9.4%, but ~7x the tile's radius in absolute pixels — cards read as
 *   noticeably more rounded than tiles, not just proportionally similar.
 * - Buttons (main_menu.png "Oyna", ~419x70px): corner rounds out at ~33px, essentially half
 *   the button height — confirms buttons are fully rounded stadium/pill shapes, not a fixed
 *   radius, so these use percent-based rounding rather than a dp value (stays a true pill at
 *   any height, including the existing GridBoard cell size which differs from the mockup's).
 *
 * The mockups' absolute pixel tile size (~120px) doesn't match this codebase's existing
 * GridBoard tile size (56.dp, set in Phase 4) — this pass restyles color/shape/typography only
 * per the phase's scope boundary, not layout dimensions, so radii below are chosen to preserve
 * the *measured proportion* (moderate tile rounding, notably more generous card rounding)
 * against this project's existing sizes rather than a literal unit conversion of mockup pixels.
 */
val TileCornerRadius = 8.dp
val CardCornerRadius = 24.dp
val PillShape = RoundedCornerShape(percent = 50)
