package com.example.shiftword.model

data class Level(
    val id: Int,
    val gridSize: Int,
    val initialCells: List<List<Char>>,
    val targetWords: List<String>,
    val moveLimit: Int,
    val minMovesToSolve: Int,
    // false = structural upper bound only, BFS hit its hard depth cap — see Risk R4.
    val minMovesIsExact: Boolean,
)
