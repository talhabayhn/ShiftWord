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
    // Feature 3 (GAME_DESIGN.md §9c): a GLOBAL, not per-level, credit pool -- the caller supplies
    // the current persisted value at construction and GameViewModel decrements it locally as
    // hints are used; it does NOT reset itself on level transitions. Defaults to effectively
    // unlimited so existing call sites/tests that don't care about the hint economy are unaffected.
    val hintCreditsRemaining: Int = Int.MAX_VALUE,
) {
    val remainingTargets: Set<String> get() = targetWords.toSet() - foundWords
}
