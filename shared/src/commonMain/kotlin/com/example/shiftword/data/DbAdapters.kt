package com.example.shiftword.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.example.shiftword.db.Level
import com.example.shiftword.db.WordShiftDatabase

private const val WORD_LIST_DELIMITER = ","
private const val CELL_GRID_SIZE_DELIMITER = "|"

object StringListAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String): List<String> =
        if (databaseValue.isEmpty()) emptyList() else databaseValue.split(WORD_LIST_DELIMITER)

    override fun encode(value: List<String>): String = value.joinToString(WORD_LIST_DELIMITER)
}

// Grid size is embedded in the encoded value itself, since a ColumnAdapter only ever sees
// its own column's raw string and can't read the sibling gridSize column to reshape by.
object CellGridAdapter : ColumnAdapter<List<List<Char>>, String> {
    override fun decode(databaseValue: String): List<List<Char>> {
        val (sizeText, flat) = databaseValue.split(CELL_GRID_SIZE_DELIMITER, limit = 2)
        val size = sizeText.toInt()
        return (0 until size).map { r -> (0 until size).map { c -> flat[r * size + c] } }
    }

    override fun encode(value: List<List<Char>>): String {
        val flat = value.joinToString("") { row -> row.joinToString("") { it.toString() } }
        return "${value.size}$CELL_GRID_SIZE_DELIMITER$flat"
    }
}

fun createDatabase(driver: SqlDriver): WordShiftDatabase =
    WordShiftDatabase(
        driver,
        Level.Adapter(
            initialCellsAdapter = CellGridAdapter,
            targetWordsAdapter = StringListAdapter,
        ),
    )
