package com.example.shiftword.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.up
import com.example.shiftword.model.Axis
import com.example.shiftword.model.Cell
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression test for a concurrent-input class of bug this project has hit repeatedly before
 * (stale hint results racing manual play, cascade calls overlapping) -- here, a real finger drag
 * starting WHILE the hint-nudge animation (GridBoard's hintMove feature) is still playing. Confirms
 * the real drag cleanly takes over: the hint animation is cancelled outright (not just out-rendered
 * -- see GridBoard's hintJob doc comment), and the eventual committed Move matches the real drag
 * exactly, never a blend of the hint's suggested row/direction and the real one.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class GridBoardHintDragInterruptionTest {

    private fun testGrid(size: Int = 4): Grid {
        var id = 0L
        val cells = (0 until size).map { row ->
            (0 until size).map { col -> Cell(('A' + (row * size + col) % 26), id++) }
        }
        return Grid(size, cells)
    }

    @Test
    fun realDragStartingMidHintAnimation_takesOverCleanly() = runComposeUiTest {
        mainClock.autoAdvance = false
        val moves = mutableListOf<Move>()
        val grid = testGrid()

        setContent {
            GridBoard(
                grid = grid,
                explodingCellIds = emptySet(),
                onMove = { moves.add(it) },
                // Suggests row 0, forward -- deliberately a different row AND a different
                // resulting direction than the real drag below, so the two are unambiguous.
                hintMove = Move(Axis.Row, 0, forward = true),
            )
        }

        // Let the hint LaunchedEffect launch and get partway through its first nudge leg
        // (HINT_NUDGE_DURATION_MS = 260ms) without letting it finish.
        mainClock.advanceTimeBy(80)

        // Real drag on ROW 2, released backward -- opposite axis-index AND direction from the
        // hint's row-0-forward suggestion, so a blend or a leftover-hint artifact would be
        // immediately visible as a wrong axis/index/direction in the committed move.
        onRoot().performTouchInput {
            down(Offset(120f, 2 * 56f + 10f))
            moveTo(Offset(10f, 2 * 56f + 10f))
            up()
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(1, moves.size, "exactly one move must commit -- the real drag, not a blend with the hint")
        val committed = moves.single()
        assertEquals(Axis.Row, committed.axis)
        assertEquals(2, committed.index, "must reflect the row the real drag touched, not the hinted row 0")
        assertEquals(false, committed.forward, "must reflect the real drag's direction, not the hint's suggested forward")

        // Let the (cancelled) hint coroutine's animation window fully elapse, then confirm nothing
        // further ever fires from it once it's cancelled.
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(1, moves.size, "the cancelled hint animation must never itself commit a move")

        // Sanity: a further, independent drag still works afterward -- proves no leftover
        // dragAxis/hintAnimAxis state is left stuck blocking future input.
        onRoot().performTouchInput {
            down(Offset(10f, 10f))
            moveTo(Offset(120f, 10f))
            up()
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(2, moves.size, "a subsequent independent drag must still be able to commit a move")
        val second = moves[1]
        assertEquals(Axis.Row, second.axis)
        assertEquals(0, second.index)
        assertEquals(true, second.forward)
        assertNull(
            moves.getOrNull(2),
            "sanity: no extra/phantom move should ever appear",
        )
    }
}
