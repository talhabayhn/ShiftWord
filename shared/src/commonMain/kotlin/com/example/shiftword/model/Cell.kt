package com.example.shiftword.model

import androidx.compose.runtime.Immutable

// id is stable across shifts/cascades so Compose can diff "moved" vs "destroyed+new" tiles.
@Immutable
data class Cell(val letter: Char, val id: Long)
