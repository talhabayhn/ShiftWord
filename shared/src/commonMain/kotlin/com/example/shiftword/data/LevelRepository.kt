package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.Level

class LevelRepository(private val database: WordShiftDatabase) {

    fun insert(level: Level) {
        database.levelQueries.insertLevel(
            id = level.id.toLong(),
            gridSize = level.gridSize.toLong(),
            initialCells = level.initialCells,
            targetWords = level.targetWords,
            moveLimit = level.moveLimit.toLong(),
            minMovesToSolve = level.minMovesToSolve.toLong(),
            minMovesIsExact = level.minMovesIsExact,
        )
    }

    fun findById(id: Int): Level? =
        database.levelQueries.selectById(id.toLong()).executeAsOneOrNull()?.let {
            Level(
                id = it.id.toInt(),
                gridSize = it.gridSize.toInt(),
                initialCells = it.initialCells,
                targetWords = it.targetWords,
                moveLimit = it.moveLimit.toInt(),
                minMovesToSolve = it.minMovesToSolve.toInt(),
                minMovesIsExact = it.minMovesIsExact,
            )
        }

    fun all(): List<Level> =
        database.levelQueries.selectAll().executeAsList().map {
            Level(
                id = it.id.toInt(),
                gridSize = it.gridSize.toInt(),
                initialCells = it.initialCells,
                targetWords = it.targetWords,
                moveLimit = it.moveLimit.toInt(),
                minMovesToSolve = it.minMovesToSolve.toInt(),
                minMovesIsExact = it.minMovesIsExact,
            )
        }

    fun clearAll() = database.levelQueries.clearAll()
}
