package com.example.shiftword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shiftword.ui.theme.DustyLavender
import com.example.shiftword.ui.theme.SageGreen
import com.example.shiftword.ui.theme.SoftCoral
import com.example.shiftword.ui.theme.WarmSand

/** The 2x2 lavender/sage/sand/coral mark used in app_icon.png / splash_screen.png / main_menu.png. */
@Composable
fun LogoMark(squareSize: Dp = 32.dp, gap: Dp = 4.dp) {
    val shape = RoundedCornerShape(squareSize * 0.18f)
    Column {
        Row {
            Box(DustyLavender, squareSize, shape)
            Spacer(gap)
            Box(SageGreen, squareSize, shape)
        }
        Spacer(gap)
        Row {
            Box(WarmSand, squareSize, shape)
            Spacer(gap)
            Box(SoftCoral, squareSize, shape)
        }
    }
}

@Composable
private fun Box(color: androidx.compose.ui.graphics.Color, size: Dp, shape: RoundedCornerShape) {
    androidx.compose.foundation.layout.Box(Modifier.size(size).background(color, shape))
}

@Composable
private fun Spacer(size: Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.size(size))
}
