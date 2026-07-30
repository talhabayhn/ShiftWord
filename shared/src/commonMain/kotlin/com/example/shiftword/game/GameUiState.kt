package com.example.shiftword.game

import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move

data class GameUiState(
    val grid: Grid,
    val targetWords: List<String>,
    val foundWords: Set<String>,
    val moveCount: Int,
    val moveLimit: Int,
    val minMovesToSolve: Int,
    val minMovesIsExact: Boolean,
    val isWon: Boolean,
    val isLost: Boolean,
    // Cell ids currently forming a completed word, shown just before they clear — lets the UI
    // play an explosion (scale/fade) animation on exactly these cells before removal, rather
    // than having them vanish the instant the domain layer clears them.
    val explodingCellIds: Set<Long> = emptySet(),
    // Populated on request by the hint system (bfsMinMovesToAnyTarget's first path step);
    // cleared automatically once a move is committed, since it applies to the grid at request
    // time only.
    val hintMove: Move? = null,
    // Feature 2 (GAME_DESIGN.md §9b): the move count each found word individually completed at,
    // populated by GameViewModel.commit -- independent of StarRating, which is untouched by this.
    val foundAtMoveCount: Map<String, Int> = emptyMap(),
) {
    val remainingTargets: Set<String> get() = targetWords.toSet() - foundWords
    val totalScore: Int get() = scoreForLevel(foundAtMoveCount, moveLimit)
}
