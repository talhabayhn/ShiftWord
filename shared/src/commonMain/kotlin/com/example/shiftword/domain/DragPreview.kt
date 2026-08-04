package com.example.shiftword.domain

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move
import kotlin.math.roundToInt

/**
 * Feature 1B (GAME_DESIGN.md): whether releasing a drag RIGHT NOW, at [offsetPx] along [axis]'s
 * [index], would complete one of [targetWords] -- GridBoard's live win-highlight. Extracted out of
 * GridBoard.kt (a `@Composable`) into a pure function so it can be unit-tested directly on the
 * JVM without Robolectric/Compose UI test infrastructure, per ARCHITECTURE.md §10's "shared
 * module: JVM unit tests" preference -- this project's Compose UI test suite is otherwise minimal
 * (TESTING_GAPS.md item 1) and, concretely, `captureToImage()`-based pixel assertions were tried
 * first here and hang indefinitely under Robolectric (`ComposeTimeoutException` waiting on
 * `forceRedraw`) -- a known environment limitation, not something a test tweak fixes.
 *
 * Scoped to the DRAGGED axis's own resulting word only, matching the feature's original design
 * (GridBoard's `winHighlightEnabled` doc comment: "the axis currently being dragged would spell
 * one of targetWords") -- it deliberately does NOT check whether some OTHER, perpendicular
 * row/column would complete as a side effect of the shift, even though the real post-move match
 * detection (`findMatchedWords`) checks the whole grid. A real-device bug report ("the highlight
 * never appears") turned out, on investigation, to be exactly this: a reproduction dragging a
 * column whose own word wasn't a target, but which incidentally completed an intersecting ROW
 * (this game's R2 crossword-style word placement makes that common) -- the highlight correctly
 * stayed off there; the underlying computation itself was never broken. Kept scoped to the dragged
 * axis rather than "fixed" to check the whole grid: widening it would mean highlighting a line the
 * player isn't even touching, a different (and more confusing) feature than what was asked for.
 */
fun wouldCompleteTarget(
    grid: Grid,
    axis: Axis?,
    index: Int,
    offsetPx: Float,
    cellSizePx: Float,
    targetWords: Set<String>,
): Boolean {
    if (axis == null || cellSizePx == 0f) return false
    val steps = (offsetPx / cellSizePx).roundToInt()
    if (steps == 0) return false
    val shifted = grid.apply(Move(axis, index, forward = steps > 0))
    val resultWord = when (axis) {
        Axis.Row -> shifted.cells[index].joinToString("") { it.letter.toString() }
        Axis.Col -> (0 until grid.size).joinToString("") { r -> shifted.cells[r][index].letter.toString() }
    }
    return resultWord in targetWords
}
