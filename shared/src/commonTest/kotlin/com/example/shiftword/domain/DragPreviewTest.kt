package com.example.shiftword.domain

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Grid
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for GridBoard's Feature 1B win-highlight, reported broken on a physical
 * device. Investigation traced the report to a reproduction that dragged a column whose OWN
 * resulting word was never a target, but which incidentally completed an intersecting ROW as a
 * side effect (this game's R2 crossword-style placement makes target words share letters/cells).
 * [wouldCompleteTarget] is documented and, per these tests, correctly scoped to the dragged axis
 * only -- the underlying computation was never actually broken; see its own doc comment for the
 * full root-cause writeup, including why a Compose UI test (`captureToImage`) was tried first and
 * abandoned (hangs under Robolectric, an environment limitation).
 */
class DragPreviewTest {

    // "BCDA" shifted right (forward=true) wraps A to the front -> "ABCD".
    private val grid = Grid.fromRows(listOf("BCDA", "EFGH", "IJKL", "MNOP"))

    @Test
    fun draggedRowThatWouldSpellATargetWord_returnsTrue() {
        // cellSizePx=100, offsetPx=100 -> steps=1 (forward) -> row 0 becomes "ABCD".
        val result = wouldCompleteTarget(
            grid = grid,
            axis = Axis.Row,
            index = 0,
            offsetPx = 100f,
            cellSizePx = 100f,
            targetWords = setOf("ABCD"),
        )
        assertTrue(result, "row 0 shifted right spells 'ABCD', a target word")
    }

    @Test
    fun draggedRowInTheOppositeDirection_returnsFalse() {
        // offsetPx negative -> steps=-1 (backward) -> row 0 becomes "CDAB", not a target.
        val result = wouldCompleteTarget(
            grid = grid,
            axis = Axis.Row,
            index = 0,
            offsetPx = -100f,
            cellSizePx = 100f,
            targetWords = setOf("ABCD"),
        )
        assertFalse(result, "row 0 shifted left spells 'CDAB', not the target 'ABCD'")
    }

    @Test
    fun offsetBelowHalfACell_roundsToZeroSteps_returnsFalseEvenIfDirectionIsCorrect() {
        // 40% of a cell rounds to 0 steps -- releasing now would commit no move at all, so the
        // highlight must not claim a win that release wouldn't actually produce.
        val result = wouldCompleteTarget(
            grid = grid,
            axis = Axis.Row,
            index = 0,
            offsetPx = 40f,
            cellSizePx = 100f,
            targetWords = setOf("ABCD"),
        )
        assertFalse(result)
    }

    @Test
    fun nullAxis_returnsFalse() {
        val result = wouldCompleteTarget(
            grid = grid,
            axis = null,
            index = 0,
            offsetPx = 100f,
            cellSizePx = 100f,
            targetWords = setOf("ABCD"),
        )
        assertFalse(result, "no axis means no drag in progress -- never highlighted")
    }

    @Test
    fun draggedColumn_checksTheColumnWordNotAnyRow() {
        // Column 0 top-to-bottom is "BEIM"; shifting it down (forward=true) wraps M to the top:
        // "MBEI" -- not a target, even though this is the same kind of shift that, on the real
        // production grid that surfaced this report, incidentally completed an intersecting row.
        val result = wouldCompleteTarget(
            grid = grid,
            axis = Axis.Col,
            index = 0,
            offsetPx = 100f,
            cellSizePx = 100f,
            targetWords = setOf("ABCD"), // column 0 can never spell this regardless of shift direction
        )
        assertFalse(
            result,
            "the highlight is scoped to the dragged axis's own word -- a perpendicular " +
                "row/column completing as a side effect must not count",
        )
    }

    @Test
    fun draggedColumnThatWouldSpellATargetWord_returnsTrue() {
        val colGrid = Grid.fromRows(listOf("Bxxx", "Cxxx", "Dxxx", "Axxx"))
        // Column 0 top-to-bottom is "BCDA"; shifting down (forward=true) wraps A to the top -> "ABCD".
        val result = wouldCompleteTarget(
            grid = colGrid,
            axis = Axis.Col,
            index = 0,
            offsetPx = 100f,
            cellSizePx = 100f,
            targetWords = setOf("ABCD"),
        )
        assertTrue(result)
    }
}
