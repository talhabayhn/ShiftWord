package com.example.shiftword.domain

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move
import kotlin.test.Test
import kotlin.test.assertEquals

class GridEngineTest {

    // Direct port of the KALE/RİMA/UMUT/SIRA sanity grid from the Python prototype.
    private val grid = Grid.fromRows(listOf("KALE", "RİMA", "UMUT", "SIRA"))

    @Test
    fun rowsAndColsAsStringsMatchInitialLayout() {
        assertEquals(listOf("KALE", "RİMA", "UMUT", "SIRA"), grid.rowsAsStrings())
        assertEquals(listOf("KRUS", "AİMI", "LMUR", "EATA"), grid.colsAsStrings())
    }

    @Test
    fun rowForwardShiftWrapsLastCellToFront() {
        val shifted = grid.apply(Move(Axis.Row, 0, forward = true))
        assertEquals("EKAL", shifted.rowsAsStrings()[0])
        // Other rows untouched.
        assertEquals(listOf("RİMA", "UMUT", "SIRA"), shifted.rowsAsStrings().drop(1))
    }

    @Test
    fun rowBackwardShiftWrapsFirstCellToBack() {
        val shifted = grid.apply(Move(Axis.Row, 0, forward = false))
        assertEquals("ALEK", shifted.rowsAsStrings()[0])
    }

    @Test
    fun colForwardShiftWrapsLastCellToTop() {
        val shifted = grid.apply(Move(Axis.Col, 0, forward = true))
        assertEquals("SKRU", shifted.colsAsStrings()[0])
    }

    @Test
    fun colBackwardShiftWrapsFirstCellToBottom() {
        val shifted = grid.apply(Move(Axis.Col, 0, forward = false))
        assertEquals("RUSK", shifted.colsAsStrings()[0])
    }

    @Test
    fun moveFollowedByItsInverseReturnsOriginalGrid() {
        val moves = listOf(
            Move(Axis.Row, 1, forward = true),
            Move(Axis.Col, 2, forward = false),
            Move(Axis.Row, 3, forward = false),
        )
        var current = grid
        for (m in moves) current = current.apply(m)
        for (m in moves.reversed()) current = current.apply(m.inverse())
        assertEquals(grid.rowsAsStrings(), current.rowsAsStrings())
    }

    @Test
    fun cellIdsInAnUntouchedRowAreUnchangedAfterARowShift() {
        // Cell.id must survive shifts unchanged so Compose can diff "moved" vs "new" tiles
        // (ARCHITECTURE.md §2). A row shift must not reconstruct cells in other rows.
        val idsBefore = grid.cells[1].map { it.id }
        val shifted = grid.apply(Move(Axis.Row, 0, forward = true))
        val idsAfter = shifted.cells[1].map { it.id }
        assertEquals(idsBefore, idsAfter)
    }

    @Test
    fun cellIdsTravelWithTheirLetterWithinTheShiftedRow() {
        // The cell that wraps from the end to the front keeps its own id, not a fresh one.
        val wrappedCell = grid.cells[0].last()
        val shifted = grid.apply(Move(Axis.Row, 0, forward = true))
        assertEquals(wrappedCell.id, shifted.cells[0].first().id)
        assertEquals(wrappedCell.letter, shifted.cells[0].first().letter)
    }

    @Test
    fun findMatchedWordsIsScopedToTargetsOnly() {
        val g = Grid.fromRows(listOf("KALE", "PPPP", "PPPP", "PPPP"))
        // "KALE" is a real dictionary-shaped word, but if it's not in the target set for
        // this level, it must not be reported as a match.
        assertEquals(emptyList(), findMatchedWords(g, setOf("UMUT")))
        assertEquals(listOf("KALE"), findMatchedWords(g, setOf("KALE")))
    }
}
