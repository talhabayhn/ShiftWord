package com.example.shiftword.domain

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move

fun Grid.apply(move: Move): Grid {
    val newCells = cells.map { it.toMutableList() }.toMutableList()
    when (move.axis) {
        Axis.Row -> {
            val row = newCells[move.index]
            newCells[move.index] = if (move.forward) {
                (listOf(row.last()) + row.dropLast(1)).toMutableList()
            } else {
                (row.drop(1) + row.first()).toMutableList()
            }
        }
        Axis.Col -> {
            val col = (0 until size).map { r -> newCells[r][move.index] }
            val shifted = if (move.forward) listOf(col.last()) + col.dropLast(1) else col.drop(1) + col.first()
            shifted.forEachIndexed { r, cell -> newCells[r][move.index] = cell }
        }
    }
    return Grid(size, newCells)
}

fun findMatchedWords(grid: Grid, targets: Set<String>): List<String> =
    grid.allCandidateStrings().filter { it in targets }
