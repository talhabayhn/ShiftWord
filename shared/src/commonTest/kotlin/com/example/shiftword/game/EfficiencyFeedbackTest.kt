package com.example.shiftword.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EfficiencyFeedbackTest {

    @Test
    fun exactMinMovesShowsAnOptimalClaim() {
        val message = efficiencyMessage(movesUsed = 4, minMovesToSolve = 3, minMovesIsExact = true)
        assertEquals("Optimal: 3 moves — you used 4", message)
    }

    @Test
    fun nonExactMinMovesNeverClaimsOptimal() {
        // Risk R4 fallback: minMovesToSolve here is just the scramble-length upper bound, not a
        // proven-optimal figure, so the message must not call it "optimal".
        val message = efficiencyMessage(movesUsed = 4, minMovesToSolve = 5, minMovesIsExact = false)
        assertFalse(message.contains("Optimal", ignoreCase = true))
        assertTrue(message.contains("4"))
    }
}
