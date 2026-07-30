package com.example.shiftword.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A second, continuous score signal alongside (not replacing) the existing discrete star rating
 * (StarRating.kt, deliberately untouched by this). Where stars are based on total moves used vs.
 * the level's move limit, points-per-word are based on the specific move count that word
 * individually completed at -- see GAME_DESIGN.md §9b.
 *
 * Order-independent by construction: this only depends on [moveAtCompletion], never on which
 * target it was Nth to complete, matching this project's existing order-invariance guarantees
 * (ALGORITHM_VALIDATION.md R4 addendum).
 */
fun pointsForWord(moveAtCompletion: Int, moveLimit: Int): Int {
    if (moveLimit <= 0) return 0
    val ratio = (moveAtCompletion.toFloat() / moveLimit.toFloat()).coerceIn(0f, 1f)
    return (100 * (1f - ratio).pow(2)).roundToInt()
}

/** Sum of [pointsForWord] across every entry -- see [pointsForWord]'s doc comment for why this
 * is order-independent regardless of the map's iteration order. */
fun scoreForLevel(foundAtMoveCount: Map<String, Int>, moveLimit: Int): Int =
    foundAtMoveCount.values.sumOf { pointsForWord(it, moveLimit) }
