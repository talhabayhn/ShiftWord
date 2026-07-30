package com.example.shiftword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shiftword.game.GameViewModel
import com.example.shiftword.game.efficiencyMessage
import com.example.shiftword.game.starsFor
import com.example.shiftword.ui.theme.DustyLavender
import com.example.shiftword.ui.theme.PillShape
import com.example.shiftword.ui.theme.SageGreen
import com.example.shiftword.ui.theme.SoftCoral
import com.example.shiftword.ui.theme.StarEmpty
import com.example.shiftword.ui.theme.SurfaceWhite
import com.example.shiftword.ui.theme.TextPrimary
import com.example.shiftword.ui.theme.WarmSand

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    // Debug/playtest surface only — the caller (app shell) decides whether this is shown,
    // per the debug-build-only gating requirement; GameViewModel itself has no notion of it.
    showDevTools: Boolean = false,
    onBackToMenu: () -> Unit = {},
    onReplaySameLevel: () -> Unit = {},
    onNextLevel: () -> Unit = {},
    strings: UiStrings = TurkishStrings,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBackToMenu) {
            Text(strings.backToMenu, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }

        Pill(
            text = strings.moves(state.moveCount, state.moveLimit),
            fill = SurfaceWhite,
            textColor = TextPrimary,
            border = WarmSand,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.targetWords.forEach { word ->
                val label = if (word in state.foundWords) "✓$word" else word
                Pill(text = label, fill = SageGreen, textColor = SurfaceWhite)
            }
        }

        GridBoard(
            grid = state.grid,
            explodingCellIds = state.explodingCellIds,
            onMove = viewModel::onMove,
        )

        if (!state.isWon && !state.isLost) {
            Button(
                onClick = viewModel::requestHint,
                // Disabled mid-cascade: a hint computed against the pre-cascade grid wouldn't
                // describe a move valid for whatever's about to be on screen once the explosion/
                // refill resolves (audit finding 4a).
                enabled = state.explodingCellIds.isEmpty(),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = SoftCoral, contentColor = SurfaceWhite),
            ) { Text(strings.hint) }
            state.hintMove?.let { move ->
                Text(
                    strings.tryHint(move.axis, move.index, move.forward),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                )
            }
        }

        when {
            state.isWon -> {
                val stars = starsFor(state.moveCount, state.minMovesToSolve, state.moveLimit)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { i ->
                        Text(
                            if (i < stars) "★" else "☆",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (i < stars) SoftCoral else StarEmpty,
                        )
                    }
                }
                Text(strings.levelComplete, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    efficiencyMessage(
                        state.moveCount,
                        state.minMovesToSolve,
                        state.minMovesIsExact,
                        optimalMessage = strings.optimalMoves,
                        usedMessage = strings.usedMoves,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Text(
                    strings.scoreLabel(state.totalScore),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Button(
                    onClick = onNextLevel,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = DustyLavender, contentColor = SurfaceWhite),
                ) { Text(strings.nextLevel) }
                Button(
                    onClick = onReplaySameLevel,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
                ) { Text(strings.playAgain) }
            }
            state.isLost -> {
                Text(strings.outOfMoves, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Button(
                    onClick = onReplaySameLevel,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
                ) { Text(strings.playAgain) }
            }
        }

        if (showDevTools) {
            DevMenu(onForceCompleteWord = viewModel::debugForceCompleteWord, strings = strings)
        }
    }
}

/** Pill-shaped chip/button base — matches the rounded stadium tags used throughout the mockups
 * (move-counter chip, target-word chips, found-word chips). */
@Composable
private fun Pill(text: String, fill: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color, border: androidx.compose.ui.graphics.Color? = null) {
    Row(
        modifier = Modifier
            .wrapContentSize()
            .background(fill, PillShape)
            .let { if (border != null) it.border(1.dp, border, PillShape) else it }
            .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DevMenu(onForceCompleteWord: () -> Unit, strings: UiStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.debugTools, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Button(
            onClick = onForceCompleteWord,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = WarmSand, contentColor = TextPrimary),
        ) { Text(strings.forceCompleteWord) }
    }
}
