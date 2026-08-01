package com.example.shiftword.domain

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Cell
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Level
import com.example.shiftword.model.Move
import com.example.shiftword.model.Turkish
import com.example.shiftword.model.allMoves
import kotlin.random.Random

// Default stays Turkish for backward compatibility with existing call sites/tests; callers
// generating levels in another language (see LanguageProfile) pass their own fillerPool.
val DEFAULT_FILLER_POOL: String = Turkish.fillerPool

data class Placement(val word: String, val axis: Axis, val index: Int, val intersections: Int)

private data class Candidate(val axis: Axis, val index: Int, val intersections: Int)

/**
 * Greedily places each word as a full row or column, preferring the placement that
 * intersects already-placed words at matching letters (falls back to a non-intersecting
 * slot when no intersection is possible). Returns null if any word can't be placed at all
 * (wrong length for the grid, or every row/col is either used or in conflict) — see Risk R5.
 */
fun buildCrosswordLayout(size: Int, words: List<String>, rng: Random): Pair<List<List<Char?>>, List<Placement>>? {
    val partial: Array<Array<Char?>> = Array(size) { arrayOfNulls(size) }
    val usedRows = mutableSetOf<Int>()
    val usedCols = mutableSetOf<Int>()
    val placements = mutableListOf<Placement>()

    for (word in words) {
        if (word.length != size) return null
        val candidates = mutableListOf<Candidate>()

        for (r in 0 until size) {
            if (r in usedRows) continue
            var conflict = false
            var intersections = 0
            for (c in 0 until size) {
                val existing = partial[r][c]
                if (existing != null) {
                    if (existing != word[c]) {
                        conflict = true
                        break
                    }
                    intersections++
                }
            }
            if (!conflict) candidates.add(Candidate(Axis.Row, r, intersections))
        }
        for (c in 0 until size) {
            if (c in usedCols) continue
            var conflict = false
            var intersections = 0
            for (r in 0 until size) {
                val existing = partial[r][c]
                if (existing != null) {
                    if (existing != word[r]) {
                        conflict = true
                        break
                    }
                    intersections++
                }
            }
            if (!conflict) candidates.add(Candidate(Axis.Col, c, intersections))
        }

        if (candidates.isEmpty()) return null

        val maxScore = candidates.maxOf { it.intersections }
        val best = candidates.filter { it.intersections == maxScore }
        val chosen = best[rng.nextInt(best.size)]

        when (chosen.axis) {
            Axis.Row -> {
                for (c in 0 until size) partial[chosen.index][c] = word[c]
                usedRows.add(chosen.index)
            }
            Axis.Col -> {
                for (r in 0 until size) partial[r][chosen.index] = word[r]
                usedCols.add(chosen.index)
            }
        }
        placements.add(Placement(word, chosen.axis, chosen.index, chosen.intersections))
    }

    return partial.map { it.toList() } to placements
}

/**
 * Greedy placement is order-dependent: whichever word is placed first claims the
 * highest-intersection slot, leaving later words to fit around it. Trying several random
 * word orderings (Risk R2's flagged future enhancement) and keeping the best-scoring valid
 * layout raises the intersection rate over always taking the first successful attempt,
 * without weakening the "no accidental extra word" guarantee.
 */
fun generateSolvedGrid(
    size: Int,
    targetWords: List<String>,
    fillerLetters: String,
    targetsSet: Set<String>,
    rng: Random,
    maxAttempts: Int = 50,
): Pair<Grid, List<Placement>>? {
    var best: Pair<Grid, List<Placement>>? = null
    var bestScore = -1
    repeat(maxAttempts) {
        val (partial, placements) = buildCrosswordLayout(size, targetWords.shuffled(rng), rng) ?: return@repeat
        var id = 0L
        val cells = partial.map { row ->
            row.map { ch -> Cell(ch ?: fillerLetters[rng.nextInt(fillerLetters.length)], id++) }
        }
        val grid = Grid(size, cells)
        // Filler letters must not have accidentally spelled an extra target word.
        val matched = findMatchedWords(grid, targetsSet)
        if (matched.toSet() != targetWords.toSet()) return@repeat

        val score = placements.sumOf { it.intersections }
        if (score > bestScore) {
            best = grid to placements
            bestScore = score
        }
        // Every word beyond the first intersecting something is as good as this greedy
        // approach gets — stop searching once that's achieved.
        if (placements.drop(1).all { it.intersections > 0 }) return best
    }
    return best
}

fun scramble(grid: Grid, nMoves: Int, rng: Random): Pair<Grid, List<Move>> {
    val moves = allMoves(grid.size)
    var current = grid
    val applied = mutableListOf<Move>()
    var lastInverse: Move? = null
    repeat(nMoves) {
        val choices = moves.filter { lastInverse == null || it != lastInverse }
        val m = choices[rng.nextInt(choices.size)]
        current = current.apply(m)
        applied.add(m)
        lastInverse = m.inverse()
    }
    return current to applied
}

data class GeneratedLevel(
    val size: Int,
    val targetWords: List<String>,
    val solvedGrid: Grid,
    val placements: List<Placement>,
    val levelGrid: Grid,
    val scrambleMovesApplied: List<Move>,
    val minMovesToCompleteAll: Int,
    val moveLimit: Int,
    val minMovesIsExact: Boolean,
    val generationAttempts: Int,
) {
    fun toLevel(id: Int, language: String = "tr"): Level = Level(
        id = id,
        gridSize = size,
        initialCells = levelGrid.letterKey(),
        targetWords = targetWords,
        moveLimit = moveLimit,
        minMovesToSolve = minMovesToCompleteAll,
        minMovesIsExact = minMovesIsExact,
        language = language,
    )
}

/**
 * Extra moves credited per target BEYOND the nearest one, when estimating the total needed to
 * complete an entire level (see [generateLevel]'s moveLimit calculation) — a fixed, documented
 * constant rather than a second BFS search, because that search turned out to be unsafe at
 * generation time (see the doc comment below for why).
 *
 * Derived from `MoveLimitCalibrationTest`, which measures the actual number of moves a real,
 * immediately-replanned optimal playthrough needs to complete all 3 targets of real generated
 * levels. Grid-size-dependent, NOT a single shared constant: a first version used a flat `3` for
 * every size, tuned only against 4x4 data (500 trials/language: ~6.7-6.8 actual average moves
 * against a ~2.0-2.1 average nearest-target distance, i.e. ~2.3-2.4 extra/target, rounded up to 3
 * for headroom) — when the same flat constant was checked against 5x5 (25-trial batches, kept
 * small because this measurement's own resolveCascade-based simulation is itself expensive at
 * 5x5's larger branching factor, same root cause as the OOM note below), 1/25 levels in BOTH
 * languages still exceeded their move limit under optimal play, because 5x5's larger branching
 * factor (20 vs 16) and longer default `scrambleMoves` (6 vs 5) make each additional target
 * genuinely cost more real moves on average than at 4x4. Bumped to 4/target at size>=5 in
 * response, restoring the zero-overage guarantee at that sample size — see
 * `MoveLimitCalibrationTest`'s 5x5 variants for the regression guard.
 */
private fun additionalTargetMovesEstimate(size: Int): Int = if (size >= 5) 4 else 3

/**
 * Solvability is structural (scramble uses invertible moves, so the level is solvable in at
 * most [scrambleMoves] moves by construction) — BFS is only an optional refinement for a
 * tighter move limit, bounded by [BFS_HARD_DEPTH_CAP] regardless of what's passed here. If BFS
 * doesn't resolve within the cap, generation does not fail: it falls back to [scrambleMoves]
 * as the upper bound and reports minMovesIsExact = false. See Risk R4.
 *
 * **Move-limit calibration (priority-2 real-device playtesting finding):** GAME_DESIGN.md defines
 * the move limit as a buffer over "the minimum number of shifts required to solve THE LEVEL" (all
 * targets) — but [bfsMinMovesToAnyTarget] only ever reports the distance to whichever target is
 * closest to the scrambled starting grid, not the cost of completing every target. Left
 * unadjusted, that meant `moveLimit` was calibrated against reaching ONE word, not winning the
 * level: `MoveLimitCalibrationTest` measured this directly (a simulated optimal, immediately
 * re-planned playthrough against 500 real generated levels per language) and found the move limit
 * averaged ~5.0-5.1 moves while actually completing all 3 targets averaged ~6.7-6.8, so ~76-78%
 * of levels needed MORE real moves than the limit allowed even under perfect play.
 *
 * The fix credits [ADDITIONAL_TARGET_MOVES_ESTIMATE] extra moves per target beyond the first
 * (see its doc comment for the measured basis) rather than actually simulating completion of all
 * targets at generation time: an exact chained-BFS-plus-cascade simulation was tried first and
 * crashed with OutOfMemoryError at 5x5 scale (branching factor 20, depth cap 5) over a 300-trial
 * batch, and a cheaper per-target-independent-BFS-sum variant hit the same wall for the same
 * underlying reason -- a single-target BFS query is far more likely than the original
 * nearest-of-N query to have to exhaust its full depth-5 search space before concluding a target
 * is unreachable, and that cost multiplies badly at 5x5's larger branching factor. The fixed
 * constant keeps generateLevel's BFS cost identical to before (exactly one call, same as the
 * original single-target refinement) while still meaningfully recalibrating the result.
 *
 * A level with more than one target can therefore never be reported `minMovesIsExact = true`
 * anymore: the reported total is a calibrated estimate, not a BFS-proven optimum for completing
 * the whole level, so claiming exactness (and showing an "Optimal: N moves" message off the back
 * of it) would assert a precision this doesn't have — exactly the dishonesty
 * `EfficiencyFeedback.kt`'s doc comment already warns against, just previously happening
 * silently for a different reason (the old value being exact-but-for-the-wrong-target).
 */
fun generateLevel(
    size: Int,
    targetWords: List<String>,
    scrambleMoves: Int,
    rng: Random,
    buffer: Int = 3,
    maxAttempts: Int = 200,
    fillerPool: String = DEFAULT_FILLER_POOL,
): GeneratedLevel? {
    val targetsSet = targetWords.toSet()

    for (attempt in 1..maxAttempts) {
        val (solved, placements) = generateSolvedGrid(size, targetWords, fillerPool, targetsSet, rng) ?: continue

        val (scrambled, appliedMoves) = scramble(solved, scrambleMoves, rng)
        if (findMatchedWords(scrambled, targetsSet).isNotEmpty()) continue

        val bfsResult = bfsMinMovesToAnyTarget(scrambled, targetsSet)
        val (minMoves, exact) = if (bfsResult != null) {
            val additionalTargets = targetWords.size - 1
            val estimatedTotal = bfsResult.minMoves + additionalTargets * additionalTargetMovesEstimate(size)
            estimatedTotal to (additionalTargets == 0)
        } else {
            scrambleMoves to false
        }

        return GeneratedLevel(
            size = size,
            targetWords = targetWords,
            solvedGrid = solved,
            placements = placements,
            levelGrid = scrambled,
            scrambleMovesApplied = appliedMoves,
            minMovesToCompleteAll = minMoves,
            moveLimit = minMoves + buffer,
            minMovesIsExact = exact,
            generationAttempts = attempt,
        )
    }
    return null
}
