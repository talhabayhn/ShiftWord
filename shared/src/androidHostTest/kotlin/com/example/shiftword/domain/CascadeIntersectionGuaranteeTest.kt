package com.example.shiftword.domain

import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS_EN
import com.example.shiftword.model.Cell
import com.example.shiftword.model.English
import com.example.shiftword.model.Grid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression coverage for the R2/R3/R4 interaction bug found in real-device playtesting
 * (priority-1 report): R2 deliberately places target words so they intersect (share a cell) to
 * raise the crossword-feel intersection rate. R3's cascade explosion clears a matched word's
 * entire row/column and refills it with brand-new random letters — including, potentially, a
 * cell that a DIFFERENT, still-remaining target word depends on. A naive refill has no
 * obligation to put back a letter that target still needs, silently voiding R4's "every
 * generated level is solvable" guarantee for whichever targets are found later (or, from the
 * player's perspective, making it *look* like target words have to be found in a specific
 * order — whichever order happens to avoid destroying an intersecting word's needed letter).
 *
 * Reproduced empirically before the fix (`resolveCascade` in Cascade.kt), simulating repeated
 * debugForceCompleteWord-style force-completions (the exact production cascade path) across
 * 2,000 generated levels: **1552/2000 (77.6%)** had at least one remaining target left with
 * insufficient letters at some point. [requiredLettersFor]/[explodeAndRefill]'s `forcedLetters`
 * now deterministically guarantee the letter-count necessary condition instead of leaving it to
 * chance (an interim retry-based attempt only got the failure rate down to 25% at 50 retries —
 * the odds of independently drawing several specific letters together are too low for blind
 * retrying alone).
 *
 * The harder geometric-arrangement case (letters present, but not BFS-reachable) went through
 * two more iterations before being fully closed: a bounded random-retry (15 tries, full filler
 * alphabet) got the failure rate from 1.05% (21/2000) down to 0.1% (1/1000), but R4's promise is
 * "structural, by construction," not "very likely" — a nonzero rate, however small, is a real
 * regression from that, not a rounding error. The actual closure restricts the search to
 * [candidateLettersFor]'s scoped letter set (a completeness-preserving restriction, not a
 * shortcut — see its doc comment) and falls back to a genuine EXHAUSTIVE search over that scoped
 * space (see [exhaustiveSearch]) when random sampling doesn't find a hit quickly, guaranteed to
 * find a solution if one exists within the space searched. [CascadeResult.hadUnconfirmedArrangement]
 * reports whenever even that fails, so this is measured and asserted on directly, not inferred.
 *
 * Language-parity audit note: originally this whole suite ran against the Turkish dictionary
 * only, on the assumption that the fix (operating on generic Char/String, no alphabet-specific
 * logic) would obviously work the same for English. That's exactly the kind of assumption this
 * project's own history says not to trust silently (see the R2/R3 bug this file exists to catch,
 * and the Phase 9 language/filler-pool bug — both were "the architecture should handle this"
 * gaps). Every test below now runs against both CURATED_DICTIONARY_SEED_WORDS (Turkish) and
 * CURATED_DICTIONARY_SEED_WORDS_EN (English), at identical trial counts for both languages —
 * an earlier version of this suite ran English at half Turkish's trial count purely to keep
 * total runtime down, but that tradeoff was never written down as a deliberate decision
 * anywhere, and the whole point of this suite existing bilingually is to actually measure
 * English at the same rigor as Turkish, not a "probably fine, checked at reduced confidence"
 * version of it.
 */
class CascadeIntersectionGuaranteeTest {

    @Test
    fun cascadesNeverLeaveARemainingTargetWithInsufficientLettersTurkish() {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val (trials, breaks) = measureLetterSufficiency(pool4, DEFAULT_FILLER_POOL, trialCount = 3000, seedOffset = 0)
        println("[cascade-guarantee][TR] trials=$trials totalLetterBreaks=$breaks (was 2923 breaks / 77.6% of levels before the fix)")
        // Provably guaranteed by requiredLettersFor's deterministic forcing, not a probabilistic
        // improvement — this must be exactly zero, always.
        assertTrue(breaks == 0, "TR: a remaining target lost letters it can never get back: $breaks occurrences")
    }

    @Test
    fun cascadesNeverLeaveARemainingTargetWithInsufficientLettersEnglish() {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val (trials, breaks) = measureLetterSufficiency(pool4, English.fillerPool, trialCount = 3000, seedOffset = 1_000_000)
        println("[cascade-guarantee][EN] trials=$trials totalLetterBreaks=$breaks")
        assertTrue(breaks == 0, "EN: a remaining target lost letters it can never get back: $breaks occurrences")
    }

    private fun measureLetterSufficiency(pool: List<String>, fillerPool: String, trialCount: Int, seedOffset: Int): Pair<Int, Int> {
        var trials = 0
        var totalLetterBreaks = 0

        repeat(trialCount) { i ->
            val seed = seedOffset + i
            val rng = Random(seed)
            val targets = pool.shuffled(rng).take(3)
            val generated = generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng, fillerPool = fillerPool) ?: return@repeat
            trials++

            var grid = generated.levelGrid
            var remaining = targets.toMutableSet()
            var nextId = 100_000L

            while (remaining.isNotEmpty()) {
                val word = remaining.firstOrNull { it.length == grid.size } ?: break
                val forcedCells = grid.cells.mapIndexed { r, row ->
                    if (r == 0) word.mapIndexed { c, ch -> Cell(ch, grid.cells[0][c].id) } else row
                }
                val forcedGrid = Grid(grid.size, forcedCells)
                val cascade = resolveCascade(
                    grid = forcedGrid,
                    targetsRemaining = remaining,
                    nextId = { nextId++ },
                    refillLetter = { fillerPool.random(rng) },
                    rng = rng,
                )
                grid = cascade.grid
                remaining = cascade.remainingTargets.toMutableSet()

                for (target in remaining) {
                    if (!gridHasSufficientLetters(grid, target)) totalLetterBreaks++
                }
            }
        }
        return trials to totalLetterBreaks
    }

    /**
     * Hard zero, not a soft guard: after the exhaustive-search fix, resolveCascade reports
     * [CascadeResult.hadUnconfirmedArrangement] whenever it could not find (via full scoped
     * search, not sampling) a refill that keeps every remaining target BFS-reachable. Before this
     * fix, an interim bounded-random-retry approach measured 21/2000 (1.05%) and then 1/1000
     * (0.1%) residual failures — both real, nonzero regressions from R4's "structural, by
     * construction" guarantee, not acceptable as a permanent soft threshold per
     * ALGORITHM_VALIDATION.md R4. This asserts the flag is never set at all.
     */
    @Test
    fun cascadesNeverFallBackToAnUnconfirmedArrangementTurkish() {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val (trials, unconfirmed) = measureUnconfirmedArrangements(pool4, DEFAULT_FILLER_POOL, trialCount = 3000, seedOffset = 2_000_000)
        println("[cascade-guarantee][TR] trials=$trials unconfirmedCount=$unconfirmed (was 21/2000 (1.05%), then 1/1000 (0.1%), before the exhaustive-search fix)")
        assertTrue(unconfirmed == 0, "TR: resolveCascade fell back to an unconfirmed-reachable arrangement $unconfirmed time(s) -- R4's guarantee no longer holds by construction")
    }

    @Test
    fun cascadesNeverFallBackToAnUnconfirmedArrangementEnglish() {
        // Matches the Turkish variant's trial count exactly (3000) -- the whole point of the
        // language-parity pass this test came out of was to stop assuming EN behaves the same
        // as TR "by architecture" without actually measuring it at the same rigor, so trimming
        // EN's sample size here would undercut that. An earlier version of this test ran EN at
        // 1500 (half of TR) purely to keep total suite runtime down; that tradeoff was never
        // written down as a deliberate decision anywhere, which is exactly the kind of
        // undocumented gap this project's own conventions don't allow -- so it's fixed to match
        // instead of retroactively justified.
        val pool4 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val (trials, unconfirmed) = measureUnconfirmedArrangements(pool4, English.fillerPool, trialCount = 3000, seedOffset = 3_000_000)
        println("[cascade-guarantee][EN] trials=$trials unconfirmedCount=$unconfirmed")
        assertTrue(unconfirmed == 0, "EN: resolveCascade fell back to an unconfirmed-reachable arrangement $unconfirmed time(s) -- R4's guarantee no longer holds by construction")
    }

    private fun measureUnconfirmedArrangements(pool: List<String>, fillerPool: String, trialCount: Int, seedOffset: Int): Pair<Int, Int> {
        var trials = 0
        var unconfirmedCount = 0

        repeat(trialCount) { i ->
            val seed = seedOffset + i
            val rng = Random(seed)
            val targets = pool.shuffled(rng).take(3)
            val generated = generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng, fillerPool = fillerPool) ?: return@repeat
            trials++

            var grid = generated.levelGrid
            var remaining = targets.toMutableSet()
            var nextId = 200_000L

            while (remaining.isNotEmpty()) {
                val word = remaining.firstOrNull { it.length == grid.size } ?: break
                val forcedCells = grid.cells.mapIndexed { r, row ->
                    if (r == 0) word.mapIndexed { c, ch -> Cell(ch, grid.cells[0][c].id) } else row
                }
                val forcedGrid = Grid(grid.size, forcedCells)
                val cascade = resolveCascade(
                    grid = forcedGrid,
                    targetsRemaining = remaining,
                    nextId = { nextId++ },
                    refillLetter = { fillerPool.random(rng) },
                    rng = rng,
                )
                if (cascade.hadUnconfirmedArrangement) unconfirmedCount++
                grid = cascade.grid
                remaining = cascade.remainingTargets.toMutableSet()
            }
        }
        return trials to unconfirmedCount
    }

    /**
     * Directly targets the perceived "words must be found in a specific order" report: for each
     * generated level, force-complete its 3 target words in EVERY possible order (all 3! = 6
     * permutations) and confirm none of them ever leaves a later target letter-insufficient.
     * There is no ordering concept anywhere in findMatchedWords/resolveCascade's design (both
     * operate on the full remaining-targets Set) — the illusion of a required order was this
     * same cascade/intersection bug: some orders happened to destroy a not-yet-found word's
     * needed letter while others didn't. Fixing that (see the two tests above) should make every
     * order equally viable.
     */
    @Test
    fun completingTargetWordsInAnyOrderNeverBreaksALaterOneTurkish() {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val (levels, orderings, breaks) = measureOrderInvariance(pool4, DEFAULT_FILLER_POOL, levelCount = 150, seedOffset = 4_000_000)
        println("[cascade-guarantee][TR] levelsChecked=$levels orderingsChecked=$orderings breaks=$breaks")
        assertTrue(breaks == 0, "TR: some completion order broke a later target's letters: $breaks occurrences")
    }

    @Test
    fun completingTargetWordsInAnyOrderNeverBreaksALaterOneEnglish() {
        // Matches the Turkish variant's level count exactly (150 levels x 6 orderings = 900
        // checks) -- see cascadesNeverFallBackToAnUnconfirmedArrangementEnglish's comment for why
        // this isn't trimmed down anymore.
        val pool4 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val (levels, orderings, breaks) = measureOrderInvariance(pool4, English.fillerPool, levelCount = 150, seedOffset = 5_000_000)
        println("[cascade-guarantee][EN] levelsChecked=$levels orderingsChecked=$orderings breaks=$breaks")
        assertTrue(breaks == 0, "EN: some completion order broke a later target's letters: $breaks occurrences")
    }

    private fun measureOrderInvariance(pool: List<String>, fillerPool: String, levelCount: Int, seedOffset: Int): Triple<Int, Int, Int> {
        var levelsChecked = 0
        var orderingsChecked = 0
        var breaks = 0

        repeat(levelCount) { i ->
            val seed = seedOffset + i
            val rng = Random(seed)
            val targets = pool.shuffled(rng).take(3)
            val generated = generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng, fillerPool = fillerPool) ?: return@repeat
            levelsChecked++

            for (order in targets.permutations()) {
                orderingsChecked++
                var grid = generated.levelGrid
                var remaining = targets.toMutableSet()
                var nextId = 300_000L

                for (word in order) {
                    if (word !in remaining) continue
                    val forcedCells = grid.cells.mapIndexed { r, row ->
                        if (r == 0) word.mapIndexed { c, ch -> Cell(ch, grid.cells[0][c].id) } else row
                    }
                    val forcedGrid = Grid(grid.size, forcedCells)
                    val cascade = resolveCascade(
                        grid = forcedGrid,
                        targetsRemaining = remaining,
                        nextId = { nextId++ },
                        refillLetter = { fillerPool.random(rng) },
                        rng = rng,
                    )
                    grid = cascade.grid
                    remaining = cascade.remainingTargets.toMutableSet()

                    for (target in remaining) {
                        if (!gridHasSufficientLetters(grid, target)) breaks++
                    }
                }
            }
        }
        return Triple(levelsChecked, orderingsChecked, breaks)
    }

    private fun <T> List<T>.permutations(): List<List<T>> {
        if (size <= 1) return listOf(this)
        return indices.flatMap { i ->
            val rest = this.toMutableList().also { it.removeAt(i) }
            rest.permutations().map { listOf(this[i]) + it }
        }
    }

    private fun gridHasSufficientLetters(grid: Grid, word: String): Boolean {
        val gridCounts = grid.cells.flatten().groupingBy { it.letter }.eachCount()
        val wordCounts = word.groupingBy { it }.eachCount()
        return wordCounts.all { (ch, count) -> (gridCounts[ch] ?: 0) >= count }
    }
}
