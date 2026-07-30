package com.example.shiftword.game

import com.example.shiftword.model.Level
import kotlin.test.Test
import kotlin.test.assertEquals

class StarRatingTest {

    private fun levelWith(minMovesToSolve: Int, moveLimit: Int) = Level(
        id = 1,
        gridSize = 4,
        initialCells = List(4) { List(4) { 'P' } },
        targetWords = listOf("KALE"),
        moveLimit = moveLimit,
        minMovesToSolve = minMovesToSolve,
        minMovesIsExact = true,
    )

    @Test
    fun playingAtOrUnderTheOptimalMoveCountIsThreeStars() {
        val level = levelWith(minMovesToSolve = 2, moveLimit = 5)
        assertEquals(3, starsFor(movesUsed = 1, level))
        assertEquals(3, starsFor(movesUsed = 2, level))
    }

    @Test
    fun usingUpToHalfTheBufferIsTwoStars() {
        // buffer = 5 - 2 = 3, half rounded up = 2, so 2-star band is movesUsed in (2, 4].
        val level = levelWith(minMovesToSolve = 2, moveLimit = 5)
        assertEquals(2, starsFor(movesUsed = 3, level))
        assertEquals(2, starsFor(movesUsed = 4, level))
    }

    @Test
    fun usingTheRestOfTheLimitIsOneStar() {
        val level = levelWith(minMovesToSolve = 2, moveLimit = 5)
        assertEquals(1, starsFor(movesUsed = 5, level))
    }

    @Test
    fun zeroBufferCollapsesTheTwoStarBandButStillRatesCorrectly() {
        val level = levelWith(minMovesToSolve = 3, moveLimit = 3)
        assertEquals(3, starsFor(movesUsed = 3, level))
        // No moves beyond minMovesToSolve are possible in this level anyway (moveLimit == min),
        // but the function itself must still degrade sensibly if ever called with more.
        assertEquals(1, starsFor(movesUsed = 4, level))
    }

    /**
     * Cross-subsystem audit finding (R4's minMovesIsExact=false fallback x star rating): starsFor
     * takes only minMovesToSolve/moveLimit as plain Ints -- it has no idea whether minMovesToSolve
     * came from an exact BFS result or R4's structural-upper-bound fallback. When
     * minMovesIsExact=false, minMovesToSolve is NOT the true optimal move count, just a safe upper
     * bound -- so a player who merely matches that inflated number (not the real, unknown, possibly
     * much lower optimum) still gets a 3-star "you played optimally" rating. GameScreen's
     * efficiencyMessage correctly avoids the word "Optimal" in this case (see
     * EfficiencyFeedbackTest), but the star rating shown right next to it does not carry the same
     * caveat -- the two pieces of the win screen can send a mixed signal (neutral text, maximum
     * stars) precisely in the fallback case R4 exists to describe. This test documents the gap
     * rather than papering over it; deciding whether to change starsFor's contract (e.g. taking
     * minMovesIsExact and capping the rating) is a product/design call, not made here.
     */
    @Test
    fun threeStarsCanBeAwardedForNonOptimalPlayWhenMinMovesIsExactIsFalse() {
        // Simulates R4's fallback: the real optimal solution might be 1 move, but BFS didn't
        // confirm it within the depth cap, so minMovesToSolve was set to the structural upper
        // bound (5, the scramble length) instead -- an inflated, non-optimal number.
        val nonExactLevel = Level(
            id = 1,
            gridSize = 4,
            initialCells = List(4) { List(4) { 'P' } },
            targetWords = listOf("KALE"),
            moveLimit = 8,
            minMovesToSolve = 5, // NOT the true optimum -- see comment above.
            minMovesIsExact = false,
        )
        // Player used all 5 of the (inflated) "optimal" moves -- genuinely not optimal play if
        // the true minimum was actually lower, yet still rated the maximum 3 stars.
        assertEquals(3, starsFor(movesUsed = 5, nonExactLevel))
    }
}
