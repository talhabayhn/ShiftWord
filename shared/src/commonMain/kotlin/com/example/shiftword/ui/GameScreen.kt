package com.example.shiftword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    // Level Select feature: despite the name (kept for git-blame continuity), this now pops back
    // to Level Select, not necessarily the Main Menu -- see backToLevelSelect's doc comment.
    onBackToMenu: () -> Unit = {},
    onReplaySameLevel: () -> Unit = {},
    onNextLevel: () -> Unit = {},
    strings: UiStrings = TurkishStrings,
    // Feature 1B (GAME_DESIGN.md): opt-in, off-by-default drag-time win highlight.
    winHighlightEnabled: Boolean = false,
    // Feature 3 (GAME_DESIGN.md §9c): TODO(IAP) -- structural hook for a future "buy more hints"
    // flow. Not invoked by anything in this pass: the Hint button below is fully disabled while
    // hintCreditsRemaining is 0, so there's currently no tap for this to fire from. Kept as an
    // explicit parameter so a later phase can wire a real onClick (e.g. on the exhausted message)
    // without touching every other call site of GameScreen.
    onHintExhausted: () -> Unit = {},
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

        // Task 1 (enlarge/center the board): the board is now this screen's visual focal point --
        // it claims all the vertical space left over between the compact top info (move
        // counter/target chips) and the bottom controls, and is centered within it, rather than
        // sitting at a fixed 56.dp tile size immediately below the top info. See GridBoard's
        // DEFAULT_CELL_SIZE doc comment for why 56.dp was never the intended final size (Shape.kt
        // already documented the mockup's tiles as proportionally much larger).
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val gridSize = state.grid.size
            val cellSize = (minOf(maxWidth, maxHeight) / gridSize).coerceIn(56.dp, 100.dp)
            GridBoard(
                grid = state.grid,
                explodingCellIds = state.explodingCellIds,
                onMove = viewModel::onMove,
                winHighlightEnabled = winHighlightEnabled,
                targetWords = state.remainingTargets,
                // See GridBoard's sessionKey doc comment -- must change whenever the viewModel
                // this GameScreen was given changes (level advance/replay), so the drag-gesture
                // coroutine restarts and stops calling into the previous, disposed viewModel's
                // onMove.
                sessionKey = viewModel,
                hintMove = state.hintMove,
                cellSize = cellSize,
            )
        }

        if (!state.isWon && !state.isLost && state.hintCreditsRemaining <= 0) {
            Text(strings.hintExhausted, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
        state.hintMove?.let { move ->
            if (!state.isWon && !state.isLost) {
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

        // Task 2: "← Menü" moved from the top of the screen down here, next to the Hint control --
        // with the board now centered and enlarged, a top-anchored back button read as disconnected
        // from the rest of the controls. Uses the same pill-button tokens as every other action
        // here (ARCHITECTURE.md §7a), not a plain TextButton, so it reads as a peer control rather
        // than a stray top-of-screen link.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Button(
                onClick = onBackToMenu,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
            ) { Text(strings.backToLevelSelect) }

            if (!state.isWon && !state.isLost) {
                Button(
                    onClick = viewModel::requestHint,
                    // Disabled mid-cascade: a hint computed against the pre-cascade grid wouldn't
                    // describe a move valid for whatever's about to be on screen once the
                    // explosion/refill resolves (audit finding 4a). Feature 3 (GAME_DESIGN.md
                    // §9c): also disabled once the global hint-credit pool is exhausted.
                    enabled = state.explodingCellIds.isEmpty() && state.hintCreditsRemaining > 0,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = SoftCoral, contentColor = SurfaceWhite),
                ) { Text(strings.hintWithCredits(state.hintCreditsRemaining)) }
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
