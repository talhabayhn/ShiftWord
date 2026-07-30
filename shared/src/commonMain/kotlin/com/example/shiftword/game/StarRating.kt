package com.example.shiftword.game

import com.example.shiftword.model.Level
import kotlin.math.ceil

/**
 * GAME_DESIGN.md §6 left the exact thresholds as an implementation-time decision. Levels are
 * built as `moveLimit = minMovesToSolve + buffer` (default buffer 3, see the generator), so the
 * buffer itself is the only room a player has to be "less than perfect" and still win:
 *
 * - 3 stars: `movesUsed <= minMovesToSolve` — played the mathematically optimal solution.
 * - 2 stars: used at most half the buffer beyond optimal (rounded up, so a buffer of 1 still
 *   gives a real 2-star band instead of collapsing into 3).
 * - 1 star: anything else that still won (up to the full moveLimit, since exceeding it is a
 *   loss, not a low star rating).
 *
 * When buffer is 0 (moveLimit == minMovesToSolve, e.g. an ungenerous/edge-case level), the
 * 2-star band collapses and only 3 or 1 stars are reachable — an accepted degenerate case
 * rather than something worth special-casing.
 */
fun starsFor(movesUsed: Int, minMovesToSolve: Int, moveLimit: Int): Int {
    val buffer = moveLimit - minMovesToSolve
    val twoStarThreshold = minMovesToSolve + ceil(buffer / 2.0).toInt()
    return when {
        movesUsed <= minMovesToSolve -> 3
        movesUsed <= twoStarThreshold -> 2
        else -> 1
    }
}

fun starsFor(movesUsed: Int, level: Level): Int =
    starsFor(movesUsed, level.minMovesToSolve, level.moveLimit)
