package com.example.shiftword.domain

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move
import kotlin.test.Test
import kotlin.test.assertEquals

class GridEngine5x5Test {

    // Five real 5-letter words from the sample dictionary's five-letter pool.
    private val grid = Grid.fromRows(listOf("KİTAP", "DUMAN", "ÇİÇEK", "BALIK", "TAVAN"))

    @Test
    fun rowsAndColsAsStringsMatchInitialLayoutAt5x5() {
        assertEquals(listOf("KİTAP", "DUMAN", "ÇİÇEK", "BALIK", "TAVAN"), grid.rowsAsStrings())
        assertEquals(listOf("KDÇBT", "İUİAA", "TMÇLV", "AAEIA", "PNKKN"), grid.colsAsStrings())
    }

    @Test
    fun rowForwardShiftWrapsLastCellToFrontAt5x5() {
        val shifted = grid.apply(Move(Axis.Row, 0, forward = true))
        assertEquals("PKİTA", shifted.rowsAsStrings()[0])
    }

    @Test
    fun rowBackwardShiftWrapsFirstCellToBackAt5x5() {
        val shifted = grid.apply(Move(Axis.Row, 0, forward = false))
        assertEquals("İTAPK", shifted.rowsAsStrings()[0])
    }

    @Test
    fun colForwardShiftWrapsLastCellToTopAt5x5() {
        val shifted = grid.apply(Move(Axis.Col, 0, forward = true))
        assertEquals("TKDÇB", shifted.colsAsStrings()[0])
    }

    @Test
    fun colBackwardShiftWrapsFirstCellToBottomAt5x5() {
        val shifted = grid.apply(Move(Axis.Col, 0, forward = false))
        assertEquals("DÇBTK", shifted.colsAsStrings()[0])
    }

    @Test
    fun moveFollowedByItsInverseReturnsOriginalGridAt5x5() {
        val moves = listOf(
            Move(Axis.Row, 2, forward = true),
            Move(Axis.Col, 4, forward = false),
            Move(Axis.Row, 0, forward = false),
        )
        var current = grid
        for (m in moves) current = current.apply(m)
        for (m in moves.reversed()) current = current.apply(m.inverse())
        assertEquals(grid.rowsAsStrings(), current.rowsAsStrings())
    }

    @Test
    fun findMatchedWordsIsScopedToTargetsOnlyAt5x5() {
        assertEquals(listOf("DUMAN"), findMatchedWords(grid, setOf("DUMAN")))
        // "DUMAN" is a real row present in the grid, but if only "TAVAN" is the target for
        // this level, "DUMAN" must not be reported as a match.
        assertEquals(listOf("TAVAN"), findMatchedWords(grid, setOf("TAVAN")))
        assertEquals(emptyList(), findMatchedWords(grid, setOf("KALEM")))
    }
}
