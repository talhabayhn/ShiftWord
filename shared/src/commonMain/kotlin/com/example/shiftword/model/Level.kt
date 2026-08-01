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
    // Level Select feature (GAME_DESIGN.md): levels are now a persisted, per-language pack
    // (numbered 1..50) -- `id` is that level's stable number, scoped by [language] since a
    // Turkish level 7 and an English level 7 are different puzzles sharing the same number.
    // Defaults to Turkish's code so existing call sites/tests are unaffected.
    val language: String = "tr",
)
