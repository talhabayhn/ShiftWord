package com.example.shiftword.tools

import com.example.shiftword.model.Level
import kotlin.test.Test
import kotlin.test.assertEquals

class LevelPackGeneratorReportTest {

    private fun levelOf(id: Int, minMovesIsExact: Boolean) = Level(
        id = id,
        gridSize = 4,
        initialCells = List(4) { List(4) { 'P' } },
        targetWords = listOf("KALE"),
        moveLimit = 5,
        minMovesToSolve = 2,
        minMovesIsExact = minMovesIsExact,
    )

    @Test
    fun nonExactEntriesReportsOnlyTheFlaggedLevelsRegardlessOfHowRareTheyAreInPractice() {
        // Decoupled from whether real generation ever actually produces a non-exact result
        // (empirically it's rare — see LevelPackGeneratorReportTool) — this proves the
        // reporting/filtering logic itself is correct.
        val report = LevelPackReport(
            requested = 3,
            entries = listOf(
                LevelPackEntry(1, levelOf(1, minMovesIsExact = true), listOf("KALE")),
                LevelPackEntry(2, levelOf(2, minMovesIsExact = false), listOf("KALE")),
                LevelPackEntry(3, levelOf(3, minMovesIsExact = true), listOf("KALE")),
            ),
            failedAttempts = 0,
        )

        assertEquals(1, report.nonExactEntries.size)
        assertEquals(2, report.nonExactEntries.single().index)
        assertEquals(3, report.successCount)
    }
}
