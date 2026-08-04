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
    // Feature 3 (GAME_DESIGN.md §9c): a GLOBAL, not per-level, credit pool -- the caller supplies
    // the current persisted value at construction and GameViewModel decrements it locally as
    // hints are used; it does NOT reset itself on level transitions. Defaults to effectively
    // unlimited so existing call sites/tests that don't care about the hint economy are unaffected.
    val hintCreditsRemaining: Int = Int.MAX_VALUE,
    // Onboarding (GAME_DESIGN.md §9h): true only for the hintMove GameViewModel auto-played
    // unprompted (see autoPlayOnboardingHint) -- distinguishes it from a normal player-requested
    // hint so GameScreen knows to show the onboarding swipe bubble instead of the regular tryHint
    // text. Cleared alongside hintMove everywhere hintMove itself is cleared.
    val isOnboardingHint: Boolean = false,
    // Onboarding (GAME_DESIGN.md §9h): consecutive moves committed since the last one that found
    // any word, reset to 0 the moment a match is found. Drives the hint-button callout's trigger
    // condition (movesSinceLastMatch >= threshold) -- unrelated to moveCount/moveLimit, which
    // track the whole level, not a "haven't found anything in a while" streak.
    val movesSinceLastMatch: Int = 0,
    // Onboarding (GAME_DESIGN.md §9h): true once the hint-button callout has been triggered for
    // this GameViewModel instance -- shown until dismissed by the next move or a real hint
    // request. GameViewModel guarantees this is set true at most once per instance regardless of
    // how many further no-match moves follow (see hintCalloutShownOnce).
    val hintButtonCalloutVisible: Boolean = false,
) {
    val remainingTargets: Set<String> get() = targetWords.toSet() - foundWords
    val totalScore: Int get() = scoreForLevel(foundAtMoveCount, moveLimit)
}
