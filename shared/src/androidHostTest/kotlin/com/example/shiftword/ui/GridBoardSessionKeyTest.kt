package com.example.shiftword.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.up
import androidx.compose.ui.geometry.Offset
import com.example.shiftword.model.Cell
import com.example.shiftword.model.Grid
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Regression test for the bug where advancing to the next level, or replaying the current one,
 * left the grid unresponsive to drag gestures (GridBoard.kt's pointerInput doc comment has the
 * full root-cause writeup). Simulates what AppNavHost actually does across a level transition --
 * swap in a new callback (standing in for a fresh GameViewModel's onMove) while `size` stays the
 * same (every generated level is size=4) -- and asserts the drag lands on whichever callback is
 * current, not whichever was current when the gesture detector first started.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class GridBoardSessionKeyTest {

    private fun testGrid(size: Int = 4): Grid {
        var id = 0L
        val cells = (0 until size).map { row ->
            (0 until size).map { col -> Cell(('A' + (row * size + col) % 26), id++) }
        }
        return Grid(size, cells)
    }

    @Test
    fun dragAfterSessionKeyChange_callsCurrentOnMove_notStaleOne() = runComposeUiTest {
        var sessionGeneration by mutableStateOf(0)
        var firstSessionMoveCount = 0
        var secondSessionMoveCount = 0
        val grid = testGrid()

        setContent {
            GridBoard(
                grid = grid,
                explodingCellIds = emptySet(),
                onMove = { if (sessionGeneration == 0) firstSessionMoveCount++ else secondSessionMoveCount++ },
                sessionKey = sessionGeneration,
            )
        }

        fun dragOneCellRight() {
            onRoot().performTouchInput {
                down(Offset(10f, 10f))
                moveTo(Offset(120f, 10f))
                up()
            }
        }

        dragOneCellRight()
        waitForIdle()
        assertEquals(1, firstSessionMoveCount, "first session's onMove should fire for the first drag")
        assertEquals(0, secondSessionMoveCount)

        // Simulate AppNavHost swapping in a new GameViewModel (next level / replay): a new
        // sessionKey, grid size unchanged (every level is size=4).
        sessionGeneration = 1
        waitForIdle()

        dragOneCellRight()
        waitForIdle()

        assertEquals(
            1,
            firstSessionMoveCount,
            "stale first-session onMove must NOT be called again after sessionKey changed",
        )
        assertEquals(1, secondSessionMoveCount, "current session's onMove should fire for the post-swap drag")
    }
}
