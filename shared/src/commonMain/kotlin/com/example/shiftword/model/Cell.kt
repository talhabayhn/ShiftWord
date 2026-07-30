package com.example.shiftword.model

// id is stable across shifts/cascades so Compose can diff "moved" vs "destroyed+new" tiles.
data class Cell(val letter: Char, val id: Long)
