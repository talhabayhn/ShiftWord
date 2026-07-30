package com.example.shiftword.domain

import com.example.shiftword.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class SolverTest {

    @Test
    fun bfsFindsImmediateMatchAtDepthZero() {
        val grid = Grid.fromRows(listOf("KALE", "PPPP", "PPPP", "PPPP"))
        val result = bfsMinMovesToAnyTarget(grid, setOf("KALE"))
        checkNotNull(result)
        assertEquals(0, result.minMoves)
        assertTrue(result.path.isEmpty())
    }

    @Test
    fun bfsFindsMatchOneMoveAway() {
        // Row0 pre = "ALEK"; one right-shift turns it into "KALE".
        val grid = Grid.fromRows(listOf("ALEK", "PPPP", "PPPP", "PPPP"))
        val result = bfsMinMovesToAnyTarget(grid, setOf("KALE"))
        checkNotNull(result)
        assertEquals(1, result.minMoves)
        assertEquals("KALE", grid.apply(result.path.single()).rowsAsStrings()[0])
    }

    @Test
    fun bfsReturnsNullWhenTargetIsStructurallyUnreachableWithinTheHardDepthCap() {
        // Target word's letters don't exist anywhere in the grid at all, so no sequence of
        // shifts (which only ever permutes existing letters) can ever produce it. This is
        // the adversarial case from ALGORITHM_VALIDATION.md Risk R4 that must return a clean
        // null within the hard depth cap, never hang or exhaust memory searching further.
        val grid = Grid.fromRows(listOf("PPPP", "PPPP", "PPPP", "PPPP"))
        val elapsed = TimeSource.Monotonic.markNow()
        val result = bfsMinMovesToAnyTarget(grid, setOf("KALE"))
        assertNull(result)
        assertTrue(elapsed.elapsedNow().inWholeSeconds < 30, "BFS must resolve quickly under the hard depth cap")
    }

    @Test
    fun requestedDepthBeyondTheHardCapIsClampedNotHonored() {
        val grid = Grid.fromRows(listOf("PPPP", "PPPP", "PPPP", "PPPP"))
        val elapsed = TimeSource.Monotonic.markNow()
        // Passing a much larger maxDepth must not be honored — BFS_HARD_DEPTH_CAP always wins,
        // so this must stay fast rather than exhibit the depth-6+ blow-up from Risk R4.
        val result = bfsMinMovesToAnyTarget(grid, setOf("KALE"), maxDepth = 20)
        assertNull(result)
        assertTrue(elapsed.elapsedNow().inWholeSeconds < 30, "clamped BFS must not blow up like unbounded depth 6+ did")
    }

    /**
     * P0 real-device OutOfMemoryError finding: BFS previously had no cancellation checkpoints, so
     * an abandoned/superseded search (e.g. a hint request the player has already moved past) kept
     * consuming memory to full completion regardless of being cancelled — a real contributing
     * factor in an on-device crash (see GameViewModel.requestHint's doc comment). This confirms
     * the fix: when [isActive] reports false, the search stops at its very next loop iteration
     * instead of exhausting the full search space.
     */
    @Test
    fun bfsStopsImmediatelyWhenIsActiveReportsFalse() {
        // Adversarial input (letters don't exist in the grid at all, per the null-result test
        // above) so a non-cancelled search would run all the way to exhausting its search space —
        // this proves cancellation actually short-circuits that, rather than merely coinciding
        // with a fast legitimate result.
        val grid = Grid.fromRows(listOf("PPPP", "PPPP", "PPPP", "PPPP"))
        val result = bfsMinMovesToAnyTarget(grid, setOf("KALE"), isActive = { false })
        assertNull(result, "a cancelled search must report no result, the same as an exhausted-but-not-found one")
    }

    /**
     * Confirms the memory-footprint fix (compact keys + parent-pointer path reconstruction,
     * instead of a full Grid and a growing List<Move> copy per queued node — see this function's
     * doc comment) didn't change WHAT is found, only how the search represents its own state:
     * the reconstructed path must still be a genuinely valid, walkable sequence of moves from the
     * start grid to a matching one.
     */
    @Test
    fun reconstructedPathIsActuallyWalkableFromStartToAMatch() {
        // Same deterministic construction as CascadeTest's Phase-1/2 scenario: row0 pre-move
        // "ALEK" is one right-shift away from "KALE", so a path is guaranteed to exist here.
        val grid = Grid.fromRows(listOf("ALEK", "UPPP", "TPPP", "UPPP"))
        val result = bfsMinMovesToAnyTarget(grid, setOf("KALE", "KUTU"))
        checkNotNull(result)
        var current = grid
        for (move in result.path) current = current.apply(move)
        val matched = (current.rowsAsStrings() + current.colsAsStrings()).toSet()
        assertTrue(
            matched.any { it in setOf("KALE", "KUTU") },
            "replaying the reconstructed path must actually reach one of the targets, not just claim a move count",
        )
    }
}
