package com.example.shiftword.model

import androidx.compose.runtime.Immutable

// id is stable across shifts/cascades so Compose can diff "moved" vs "destroyed+new" tiles.
//
// INVARIANT: Grid/Cell must never be mutated in place -- always construct new instances.
// Marking this @Immutable tells Compose to trust that without checking; violating it causes
// silent stale UI, not a crash or test failure.
@Immutable
data class Cell(val letter: Char, val id: Long)
