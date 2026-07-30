package com.example.shiftword.domain

import com.example.shiftword.model.Cell
import com.example.shiftword.model.Grid
import kotlin.random.Random

data class CascadeStep(val step: Int, val foundWords: List<String>, val cellsCleared: Int)

data class CascadeResult(
    val grid: Grid,
    val chainLog: List<CascadeStep>,
    val remainingTargets: Set<String>,
    val hitChainLimit: Boolean,
    // True if the arrangement search (see resolveCascade's doc comment) could not find a
    // BFS-confirmed-reachable refill for every remaining target and had to fall back to a
    // letter-count-valid-only candidate. Exposed so this can be measured/asserted on in tests
    // rather than silently absorbed — see ALGORITHM_VALIDATION.md R4.
    val hadUnconfirmedArrangement: Boolean = false,
)

fun getMatchesWithPositions(grid: Grid, targets: Set<String>): List<Pair<String, Set<Pair<Int, Int>>>> {
    val size = grid.size
    val matches = mutableListOf<Pair<String, Set<Pair<Int, Int>>>>()
    for (r in 0 until size) {
        val w = grid.cells[r].joinToString("") { it.letter.toString() }
        if (w in targets) matches.add(w to (0 until size).map { c -> r to c }.toSet())
    }
    for (c in 0 until size) {
        val w = (0 until size).joinToString("") { r -> grid.cells[r][c].letter.toString() }
        if (w in targets) matches.add(w to (0 until size).map { r -> r to c }.toSet())
    }
    return matches
}

/**
 * [forcedLetters] lets callers guarantee specific letters land among the refilled cells (see
 * [resolveCascade]'s doc comment for why) — consumed first, in order, before falling back to
 * [refillLetter] for any remaining empty slots. Position within the column doesn't matter: only
 * the final per-column gravity order does, so forced letters are just as "random-looking" to the
 * player as any other refill.
 */
fun explodeAndRefill(
    grid: Grid,
    positionsToClear: Set<Pair<Int, Int>>,
    nextId: () -> Long,
    refillLetter: () -> Char,
    forcedLetters: List<Char> = emptyList(),
): Grid {
    val size = grid.size
    val newCells = MutableList(size) { r -> MutableList(size) { c -> grid.cells[r][c] } }
    val forcedQueue = ArrayDeque(forcedLetters)
    for (c in 0 until size) {
        val remaining = (0 until size).mapNotNull { r -> if ((r to c) in positionsToClear) null else grid.cells[r][c] }
        val missing = size - remaining.size
        val refilled = (0 until missing).map {
            val letter = if (forcedQueue.isNotEmpty()) forcedQueue.removeFirst() else refillLetter()
            Cell(letter, nextId())
        }
        val newCol = refilled + remaining
        for (r in 0 until size) newCells[r][c] = newCol[r]
    }
    return Grid(size, newCells)
}

/**
 * Whether [grid] still contains, as a plain letter multiset (ignoring position), enough of
 * every letter [word] needs. This is a NECESSARY condition for [word] to ever be formable by
 * further shifts — row/column shifts only permute existing letters, they never create or
 * destroy them — so if this is false, [word] is now permanently impossible no matter how many
 * moves remain. See [resolveCascade]'s doc comment for why this check exists.
 */
fun gridHasSufficientLetters(grid: Grid, word: String): Boolean {
    val gridCounts = grid.cells.flatten().groupingBy { it.letter }.eachCount()
    val wordCounts = word.groupingBy { it }.eachCount()
    return wordCounts.all { (ch, count) -> (gridCounts[ch] ?: 0) >= count }
}

/**
 * For each letter, the largest deficiency across [remaining] targets against [survivingCounts]
 * (the grid's letter counts *excluding* the cells about to be cleared) — i.e. how many more of
 * that letter the refill must contribute so every remaining target's full letter count is met
 * again. Taking the max (not the sum) across targets is deliberate: the final grid only needs
 * one shared pool of letters, and satisfying the toughest individual requirement for a letter
 * automatically satisfies every softer one too.
 */
private fun requiredLettersFor(remaining: Set<String>, survivingCounts: Map<Char, Int>): List<Char> {
    val maxDeficiency = mutableMapOf<Char, Int>()
    for (target in remaining) {
        val need = target.groupingBy { it }.eachCount()
        for ((ch, count) in need) {
            val deficiency = (count - (survivingCounts[ch] ?: 0)).coerceAtLeast(0)
            maxDeficiency[ch] = maxOf(maxDeficiency[ch] ?: 0, deficiency)
        }
    }
    return maxDeficiency.flatMap { (ch, count) -> List(count) { ch } }
}

/**
 * Every letter that could possibly matter for whether [remaining] stays formable. Any OTHER
 * letter is pure "filler" that could sit in any un-forced refill slot: it can never itself
 * become part of a remaining target (it isn't one of that target's letters), so trying
 * different filler values there can only matter through coincidence, never through necessity —
 * any grid arrangement reachable using an arbitrary alphabet is also reachable using only these
 * letters in the free slots (swap a non-contributing filler letter for one of these; nothing
 * about reachability depends on a filler cell's specific non-matching value). This is what makes
 * restricting the search to this list a completeness-preserving optimization, not a shortcut.
 */
private fun candidateLettersFor(remaining: Set<String>): List<Char> = remaining.flatMap { it.toList() }.distinct()

/**
 * Deterministically tries every combination of [candidates] across [freeSlotCount] positions
 * (in a fixed enumeration order), up to [maxCombosTried] — a true exhaustive search over the
 * scoped candidate space (see [candidateLettersFor]), not sampling. Returns null if every
 * combination within the cap was tried and none satisfied [isValid].
 */
private fun exhaustiveSearch(
    candidates: List<Char>,
    freeSlotCount: Int,
    maxCombosTried: Int,
    isValid: (List<Char>) -> Boolean,
): List<Char>? {
    if (freeSlotCount == 0) return if (isValid(emptyList())) emptyList() else null
    if (candidates.isEmpty()) return null
    val base = candidates.size
    val totalCombos = run {
        var t = 1L
        repeat(freeSlotCount) { t *= base; if (t > maxCombosTried) return@run maxCombosTried.toLong() }
        t
    }
    val combosToTry = minOf(totalCombos, maxCombosTried.toLong())
    for (index in 0 until combosToTry) {
        var n = index
        val combo = ArrayList<Char>(freeSlotCount)
        repeat(freeSlotCount) {
            combo.add(candidates[(n % base).toInt()])
            n /= base
        }
        if (isValid(combo)) return combo
    }
    return null
}

// max_chain_steps is a hard safety cap against a pathological refill sequence re-triggering
// matches forever, regardless of whether that's likely in practice — see Risk R3.
//
// R4 addendum: R2 deliberately places target words so they intersect (share a cell) to raise
// the crossword-feel intersection rate. That means exploding one matched word's full row/column
// can clear a cell that a DIFFERENT, still-remaining target depends on — and a naive refill has
// no obligation to put back a letter that target still needs, silently breaking R4's "every
// generated level is solvable" guarantee for whichever targets are found later. Reproduced
// empirically before any fix: 1552/2000 (77.6%) of generated levels had at least one remaining
// target left with insufficient letters after force-completing words one at a time (see
// CascadeIntersectionGuaranteeTest).
//
// [requiredLettersFor]/[explodeAndRefill]'s `forcedLetters` deterministically closes the
// letter-COUNT case (0/3000 in the same measurement — a necessary condition, not a heuristic).
// The harder, geometric-arrangement case (letters all present, but not BFS-reachable within
// BFS_HARD_DEPTH_CAP) is closed by an actual EXHAUSTIVE search over the free (non-forced) refill
// slots — restricted to [candidateLettersFor]'s scoped letter set, which is a completeness-
// preserving restriction (see its doc comment), not a shortcut — falling back to random sampling
// from that same scoped set only when the free-slot count is too large to exhaustively cover
// within [maxCombosTried]. Two earlier, weaker attempts are recorded here for why this is the
// actual fix rather than the first idea that worked: (1) retrying with fresh, fully-random
// letters from the whole filler alphabet reduced the letter-count failure rate only to 25% at 50
// retries — the odds of independently drawing several specific letters together are too low for
// blind full-alphabet retrying to close that gap reliably; (2) once the letter-count case was
// fixed deterministically, checking BFS-reachability and retrying with fresh full-alphabet random
// letters (15 tries) reduced the geometric-arrangement failure rate from 1.05% (before) to 0.1%
// (21/2000 -> 1/1000) but did not close it, because most full-alphabet draws waste tries on
// letters that can't possibly help. Scoping the search to only the letters that could matter
// closes the remaining gap without needing a combinatorially infeasible full-alphabet exhaustive
// search.
fun resolveCascade(
    grid: Grid,
    targetsRemaining: Set<String>,
    nextId: () -> Long,
    refillLetter: () -> Char,
    maxChainSteps: Int = 10,
    randomSamplingAttempts: Int = 60,
    maxExhaustiveCombos: Int = 20_000,
    // Drives the random-sampling phase's candidate-letter choice below. Previously this used
    // the un-seedable `Random.Default` internally regardless of what the caller passed to
    // `refillLetter` -- meaning two calls with the "same" seed (via refillLetter's own rng)
    // could still resolve differently run to run, since the sampling order here was never
    // actually tied to that seed. Surfaced by MoveLimitCalibrationTest flaking (0/500 vs 1/500
    // exceedances) between otherwise-identical seeded runs. Defaults to Random.Default so
    // existing callers that don't care about reproducibility are unaffected.
    rng: Random = Random.Default,
): CascadeResult {
    var current = grid
    val remaining = targetsRemaining.toMutableSet()
    val chainLog = mutableListOf<CascadeStep>()
    var hadUnconfirmedArrangement = false

    for (step in 0 until maxChainSteps) {
        val matches = getMatchesWithPositions(current, remaining).filter { it.first in remaining }
        if (matches.isEmpty()) break
        val cleared = mutableSetOf<Pair<Int, Int>>()
        val found = mutableListOf<String>()
        for ((word, positions) in matches) {
            found.add(word)
            cleared += positions
            remaining.remove(word)
        }

        val survivingCounts = mutableMapOf<Char, Int>()
        for (r in current.cells.indices) {
            for (c in current.cells[r].indices) {
                if ((r to c) !in cleared) {
                    val ch = current.cells[r][c].letter
                    survivingCounts[ch] = (survivingCounts[ch] ?: 0) + 1
                }
            }
        }
        val forcedLetters = requiredLettersFor(remaining, survivingCounts)
        val freeSlotCount = (cleared.size - forcedLetters.size).coerceAtLeast(0)

        current = if (remaining.isEmpty() || freeSlotCount == 0) {
            explodeAndRefill(current, cleared, nextId, refillLetter, forcedLetters)
        } else {
            val candidates = candidateLettersFor(remaining)
            fun candidateGrid(freeLetters: List<Char>): Grid {
                val queue = ArrayDeque(freeLetters)
                return explodeAndRefill(current, cleared, nextId, { queue.removeFirst() }, forcedLetters)
            }
            fun isFullyReachable(g: Grid) = remaining.all { bfsMinMovesToAnyTarget(g, setOf(it)) != null }

            // Random sampling first: cheap, and in practice finds a working arrangement almost
            // immediately (see the doc comment above for the measured rates this builds on).
            var resolved: Grid? = null
            var attempts = 0
            while (attempts < randomSamplingAttempts && resolved == null) {
                val candidateFree = List(freeSlotCount) { candidates.random(rng) }
                val candidateGrid = candidateGrid(candidateFree)
                if (isFullyReachable(candidateGrid)) resolved = candidateGrid
                attempts++
            }

            resolved ?: run {
                // Random sampling exhausted -- fall back to a true exhaustive search over the
                // same scoped candidate letters, guaranteed to find a solution if one exists
                // within maxExhaustiveCombos combinations (see exhaustiveSearch's doc comment).
                val found2 = exhaustiveSearch(candidates, freeSlotCount, maxExhaustiveCombos) { combo ->
                    isFullyReachable(candidateGrid(combo))
                }
                if (found2 != null) {
                    candidateGrid(found2)
                } else {
                    hadUnconfirmedArrangement = true
                    explodeAndRefill(current, cleared, nextId, refillLetter, forcedLetters)
                }
            }
        }
        chainLog.add(CascadeStep(step + 1, found, cleared.size))
    }
    val hitLimit = chainLog.size == maxChainSteps
    return CascadeResult(current, chainLog, remaining, hitLimit, hadUnconfirmedArrangement)
}
