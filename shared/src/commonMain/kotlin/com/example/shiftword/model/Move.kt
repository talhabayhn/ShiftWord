package com.example.shiftword.model

sealed interface Axis {
    data object Row : Axis
    data object Col : Axis
}

data class Move(val axis: Axis, val index: Int, val forward: Boolean) {
    fun inverse(): Move = Move(axis, index, !forward)
}

fun allMoves(size: Int): List<Move> = buildList {
    for (i in 0 until size) {
        add(Move(Axis.Row, i, true))
        add(Move(Axis.Row, i, false))
        add(Move(Axis.Col, i, true))
        add(Move(Axis.Col, i, false))
    }
}
