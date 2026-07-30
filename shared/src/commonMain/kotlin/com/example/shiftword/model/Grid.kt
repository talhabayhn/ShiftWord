package com.example.shiftword.model

import androidx.compose.runtime.Immutable

// Grid instances are never mutated in place (GridEngine/Cascade always build a new Grid via
// Grid.fromRows/the constructor) -- @Immutable tells the Compose compiler it can trust that
// contract even though `cells: List<List<Cell>>` is a plain (compiler-unstable-by-default)
// interface type. Without this, any composable taking a Grid parameter (GridBoard) could never
// be skipped -- every unrelated GameUiState field change (moveCount, hintMove, isWon, ...) read
// via a single collectAsState() in GameScreen would force the whole grid to fully recompose,
// not just redraw, on every state update.
@Immutable
class Grid(val size: Int, val cells: List<List<Cell>>) {

    fun rowsAsStrings(): List<String> =
        cells.map { row -> row.joinToString("") { it.letter.toString() } }

    fun colsAsStrings(): List<String> =
        (0 until size).map { c -> (0 until size).joinToString("") { r -> cells[r][c].letter.toString() } }

    fun allCandidateStrings(): List<String> = rowsAsStrings() + colsAsStrings()

    // Letters only, ignoring Cell.id — this is the BFS/generator state-space key, matching
    // the prototype's plain-letter Grid equality exactly (see ALGORITHM_VALIDATION.md R4).
    fun letterKey(): List<List<Char>> = cells.map { row -> row.map { it.letter } }

    override fun equals(other: Any?): Boolean =
        other is Grid && size == other.size && letterKey() == other.letterKey()

    override fun hashCode(): Int = letterKey().hashCode()

    fun pretty(): String = cells.joinToString("\n") { row -> row.joinToString(" ") { "[${it.letter}]" } }

    companion object {
        fun fromRows(rows: List<String>, idStart: Long = 0L): Grid {
            var id = idStart
            val cells = rows.map { row -> row.map { ch -> Cell(ch, id++) } }
            return Grid(rows.size, cells)
        }
    }
}
