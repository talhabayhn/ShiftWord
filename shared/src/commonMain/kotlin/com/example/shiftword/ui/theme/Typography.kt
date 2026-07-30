package com.example.shiftword.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import shiftword.shared.generated.resources.Res
import shiftword.shared.generated.resources.comfortaa_regular
import shiftword.shared.generated.resources.poppins_bold
import shiftword.shared.generated.resources.poppins_medium
import shiftword.shared.generated.resources.poppins_regular
import shiftword.shared.generated.resources.poppins_semibold

/**
 * Two typefaces, matching the mockups: Comfortaa (rounded, friendly) for headings/UI labels —
 * the app title, "Seviye Tamamlandı!", button labels, the move-counter chip — and Poppins
 * (clean geometric sans) for body/caption text and, at its boldest weight, the letter-tile
 * text, where legibility of Turkish Ç/Ğ/İ/Ö/Ş/Ü at small tile size matters most.
 *
 * Comfortaa ships upstream only as a variable font (single file, weight axis) — bundled as one
 * FontResource at its default (regular-ish) instance; the mockups don't show it needing a
 * separate bold cut (headings/buttons all read as one consistent weight).
 */
@Composable
fun comfortaaFontFamily(): FontFamily = FontFamily(Font(Res.font.comfortaa_regular))

@Composable
fun poppinsFontFamily(): FontFamily = FontFamily(
    Font(Res.font.poppins_regular, weight = FontWeight.Normal),
    Font(Res.font.poppins_medium, weight = FontWeight.Medium),
    Font(Res.font.poppins_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.poppins_bold, weight = FontWeight.Bold),
)

/** Large, bold, high-legibility — used only for the letters inside grid tiles. */
@Composable
fun tileLetterStyle(): TextStyle = TextStyle(
    fontFamily = poppinsFontFamily(),
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
)

@Composable
fun appTypography(): Typography {
    val heading = comfortaaFontFamily()
    val body = poppinsFontFamily()
    return Typography(
        headlineSmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 32.sp),
        titleLarge = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 24.sp),
        titleMedium = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 18.sp),
        labelLarge = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 18.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    )
}
