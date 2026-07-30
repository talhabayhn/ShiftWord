package com.example.shiftword.game

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreTest {

    @Test
    fun completingRightAwayScoresTheFullHundredPoints() {
        assertEquals(100, pointsForWord(moveAtCompletion = 0, moveLimit = 10))
    }

    @Test
    fun completingExactlyAtTheMoveLimitScoresZero() {
        assertEquals(0, pointsForWord(moveAtCompletion = 10, moveLimit = 10))
    }

    @Test
    fun completingHalfwayThroughTheLimitScoresAQuarterOfMax() {
        // ratio = 0.5 -> 100 * (1 - 0.5)^2 = 25 -- the quadratic term, not a linear one.
        assertEquals(25, pointsForWord(moveAtCompletion = 5, moveLimit = 10))
    }

    @Test
    fun completingPastTheMoveLimitStillClampsToZeroNotNegative() {
        // Not reachable in real play (a level can't still be won after moveLimit is exceeded --
        // see GameViewModel.commit's isLost check) but the function itself must degrade sensibly.
        assertEquals(0, pointsForWord(moveAtCompletion = 15, moveLimit = 10))
    }

    @Test
    fun aNonPositiveMoveLimitScoresZeroRatherThanDividingByZero() {
        assertEquals(0, pointsForWord(moveAtCompletion = 3, moveLimit = 0))
    }

    /**
     * Order-independence: pointsForWord only ever depends on the move count a word completed at,
     * never on which target it was Nth to complete -- so scoreForLevel must be identical
     * regardless of the map's construction/iteration order. Mirrors the order-invariance style
     * already established for cascade/reachability guarantees (see
     * CascadeIntersectionGuaranteeTest, ALGORITHM_VALIDATION.md R4 addendum).
     */
    @Test
    fun totalScoreIsIndependentOfCompletionOrder() {
        val moveLimit = 12
        val completionsInOneOrder = linkedMapOf("KALE" to 3, "MASA" to 7, "TUZ" to 12)
        val completionsInAnotherOrder = linkedMapOf("TUZ" to 12, "KALE" to 3, "MASA" to 7)

        val scoreA = scoreForLevel(completionsInOneOrder, moveLimit)
        val scoreB = scoreForLevel(completionsInAnotherOrder, moveLimit)

        assertEquals(scoreA, scoreB)
        val expected = pointsForWord(3, moveLimit) + pointsForWord(7, moveLimit) + pointsForWord(12, moveLimit)
        assertEquals(expected, scoreA)
    }
}
