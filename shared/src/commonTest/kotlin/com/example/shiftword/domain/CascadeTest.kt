package com.example.shiftword.domain

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CascadeTest {

    @Test
    fun singleMoveTriggeringTwoSimultaneousMatchesClearsBothInOneStep() {
        // Deliberately constructed (not RNG-discovered) so row 0 and col 0 both complete
        // at once: row0 pre-move = "ALEK" (KALE rotated), everything else filler ('P').
        // Shifting row0 right turns row0 into "KALE" AND simultaneously turns col0 into
        // "KUTU" (K,U,T,U), since col0's other 3 cells were already U/T/U. This mirrors
        // ALGORITHM_VALIDATION.md Risk R3's confirmed simultaneous row+col match case.
        val pre = Grid.fromRows(listOf("ALEK", "UPPP", "TPPP", "UPPP"))
        val targets = setOf("KALE", "KUTU")

        // Sanity: no match exists yet before the move.
        assertTrue(findMatchedWords(pre, targets).isEmpty())

        val afterMove = pre.apply(Move(Axis.Row, 0, forward = true))
        assertEquals("KALE", afterMove.rowsAsStrings()[0])
        assertEquals("KUTU", afterMove.colsAsStrings()[0])

        val rng = Random(1)
        val result = resolveCascade(
            grid = afterMove,
            targetsRemaining = targets,
            nextId = { -1L },
            refillLetter = { "ABCDEFGHIJKLMNOPRSTUVYZÇĞİÖŞÜ".random(rng) },
            rng = rng,
        )

        assertEquals(1, result.chainLog.size)
        assertEquals(setOf("KALE", "KUTU"), result.chainLog[0].foundWords.toSet())
        // 4 (row) + 4 (col) - 1 (shared cell at 0,0) = 7 unique cleared cells.
        assertEquals(7, result.chainLog[0].cellsCleared)
        assertTrue(result.remainingTargets.isEmpty())
        assertFalse(result.hitChainLimit)
    }

    @Test
    fun cascadeStopsWhenNoMoreMatchesRegardlessOfRefill() {
        val grid = Grid.fromRows(listOf("PPPP", "PPPP", "PPPP", "PPPP"))
        val rng = Random(2)
        val result = resolveCascade(
            grid = grid,
            targetsRemaining = setOf("KALE"),
            nextId = { -1L },
            refillLetter = { "P".single() },
        )
        assertTrue(result.chainLog.isEmpty())
        assertEquals(setOf("KALE"), result.remainingTargets)
        assertFalse(result.hitChainLimit)
    }

    @Test
    fun bfsSolutionPathAppliedThroughRealCascadeNeverHitsChainLimit() {
        // End-to-end check mirroring the prototype's R3 test: generate a level, walk its
        // BFS solution path (which only guarantees reaching the *first* match), and resolve
        // cascades after each move without ever tripping the max-chain-steps safety cap.
        val targets = listOf("ANLA", "UMUT", "KALE")
        val rng = Random(5)
        val level = generateLevel(size = 4, targetWords = targets, scrambleMoves = 4, rng = rng)
        checkNotNull(level)

        var current = level.levelGrid
        var remaining = targets.toSet()
        val cascadeRng = Random(7)
        var idCounter = 1_000_000L

        val solution = bfsMinMovesToAnyTarget(current, remaining)
        checkNotNull(solution)

        for (move in solution.path) {
            current = current.apply(move)
            val result = resolveCascade(
                grid = current,
                targetsRemaining = remaining,
                nextId = { idCounter++ },
                refillLetter = { DEFAULT_FILLER_POOL.random(cascadeRng) },
                rng = cascadeRng,
            )
            assertFalse(result.hitChainLimit)
            current = result.grid
            remaining = result.remainingTargets
            if (remaining.isEmpty()) break
        }
        assertTrue(remaining.size <= targets.size)
    }
}
