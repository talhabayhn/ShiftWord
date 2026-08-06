package com.example.shiftword.domain

import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS_EN
import com.example.shiftword.model.English
import com.example.shiftword.model.Grid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Manual playtesting report (priority-2): the move limit "varies inconsistently and often feels
 * too low," with some levels genuinely requiring 25-30+ moves to complete all 3 target words --
 * far outside GAME_DESIGN.md's intended ~10-12 move range. Suspicion: `generateLevel`'s
 * `moveLimit = minMoves + buffer` (LevelGenerator.kt) is calibrated only against
 * `bfsMinMovesToAnyTarget`'s result -- the BFS distance to whichever ONE target is nearest to the
 * scrambled starting grid -- and was never validated against the real objective, which is
 * completing ALL 3 targets.
 *
 * This measures, for real generated levels, the actual number of moves a real playthrough needs
 * to complete all 3 targets: at each step, ask BFS for the nearest remaining target from the
 * CURRENT grid (mirroring a real hint request -- see the R4 addendum's "point-in-time, not
 * standing" clarification in ALGORITHM_VALIDATION.md), apply that path one real move at a time
 * through the actual `resolveCascade` path, and re-plan from scratch if an intervening cascade
 * makes the in-flight plan stale. This is the same real-playthrough mechanism already used by
 * `GameViewModelFullPlaythroughStressTest` and `CascadeIntersectionGuaranteeTest`, just measuring
 * total move count instead of correctness.
 */
class MoveLimitCalibrationTest {

    private data class CalibrationResult(
        val levelsChecked: Int,
        val moveLimits: List<Int>,
        val actualMovesToCompleteAll3: List<Int>,
        val exceedsMoveLimitCount: Int,
        val unreachableCount: Int,
    )

    // Follow-up finding: the first version of this test only ever measured 4x4 (ADDITIONAL_TARGET_
    // MOVES_ESTIMATE was tuned exclusively against those numbers). 5x5 has a different branching
    // factor (4*5=20 vs 4*4=16) and default scrambleMoves (6 vs 5), so the same constant's
    // headroom was never actually checked there -- these variants close that gap.

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllThreeTargetsNotJustTheNearestOne4x4Turkish() {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val result = measureActualMovesToCompleteAllTargets(pool4, DEFAULT_FILLER_POOL, size = 4, scrambleMoves = 5, trialCount = 500, seedOffset = 6_000_000)
        report("TR-4x4", result)
        assertTrue(result.unreachableCount == 0, "TR-4x4: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        // Hard regression guard for the priority-2 finding: before the fix, this was 381/500
        // (76.2%) -- moveLimit was calibrated only against the nearest single target, not
        // completing all 3. Must be exactly zero now, not a soft/reduced-rate threshold, since
        // the fix removed the miscalibration structurally rather than just improving odds.
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "TR-4x4: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play (was 381/500 / 76.2% before the fix)",
        )
    }

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllThreeTargetsNotJustTheNearestOne4x4English() {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val result = measureActualMovesToCompleteAllTargets(pool4, English.fillerPool, size = 4, scrambleMoves = 5, trialCount = 500, seedOffset = 7_000_000)
        report("EN-4x4", result)
        assertTrue(result.unreachableCount == 0, "EN-4x4: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        // Was 389/500 (77.8%) before the fix -- see the Turkish variant's comment above.
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "EN-4x4: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play (was 389/500 / 77.8% before the fix)",
        )
    }

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllThreeTargetsNotJustTheNearestOne5x5Turkish() {
        val pool5 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 5 }
        // Trial count kept much lower than the 4x4 variants (25 vs 500): this test chains real
        // resolveCascade calls (with its own nested BFS reachability search) per level, and at
        // 5x5's larger branching factor (20 vs 16) that combination is exactly what forced the
        // generateLevel pivot away from doing this same thing at generation time (see the R4
        // addendum in ALGORITHM_VALIDATION.md and LevelGenerator.kt's doc comments). Even this
        // measurement -- a one-off run, not nested inside a retry loop -- hit OutOfMemoryError at
        // 100 trials with the default test-worker heap; shared/build.gradle.kts now sets
        // maxHeapSize=3g for test tasks specifically to accommodate this, and the trial count is
        // kept modest on top of that rather than relying on heap alone.
        val result = measureActualMovesToCompleteAllTargets(pool5, DEFAULT_FILLER_POOL, size = 5, scrambleMoves = 6, trialCount = 25, seedOffset = 8_000_000)
        report("TR-5x5", result)
        assertTrue(result.unreachableCount == 0, "TR-5x5: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "TR-5x5: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play",
        )
    }

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllThreeTargetsNotJustTheNearestOne5x5English() {
        val pool5 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 5 }
        val result = measureActualMovesToCompleteAllTargets(pool5, English.fillerPool, size = 5, scrambleMoves = 6, trialCount = 25, seedOffset = 9_000_000)
        report("EN-5x5", result)
        assertTrue(result.unreachableCount == 0, "EN-5x5: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "EN-5x5: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play",
        )
    }

    // New combo for the levels-41-50 difficulty tier (GAME_DESIGN.md §5 scaling, wired into
    // LevelPackGenerator/LevelRepository): 5x5 grid, 4 target words (up from 3), scrambleMoves=7
    // (up from 6). Nothing about the 3-word 5x5 calibration above can be assumed to carry over --
    // a 4th target changes both ADDITIONAL_TARGET_MOVES_ESTIMATE's headroom and the real
    // completion-move distribution -- so this gets its own dedicated measurement per the same
    // hard-regression-guard standard as every other grid-size/word-count combo in this file,
    // not shipped on assumption.

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllFourTargets5x5Turkish() {
        val pool5 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 5 }
        val result = measureActualMovesToCompleteAllTargets(pool5, DEFAULT_FILLER_POOL, size = 5, scrambleMoves = 7, wordsPerLevel = 4, trialCount = 25, seedOffset = 10_000_000)
        report("TR-5x5-4word", result)
        assertTrue(result.unreachableCount == 0, "TR-5x5-4word: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "TR-5x5-4word: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play",
        )
    }

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllFourTargets5x5English() {
        val pool5 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 5 }
        val result = measureActualMovesToCompleteAllTargets(pool5, English.fillerPool, size = 5, scrambleMoves = 7, wordsPerLevel = 4, trialCount = 25, seedOffset = 11_000_000)
        report("EN-5x5-4word", result)
        assertTrue(result.unreachableCount == 0, "EN-5x5-4word: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "EN-5x5-4word: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play",
        )
    }

    // Levels 51-100 pack expansion (GAME_DESIGN.md §5 tier extension): reuses the exact 41-50
    // grid/word-count/scrambleMoves combo (5x5, 4 targets, scrambleMoves=7) -- already covered by
    // GeneratorMetricsTest's density guards (a pure function of grid size/word count) and by this
    // file's own moveLimitIsCalibratedAgainstCompletingAllFourTargets5x5* tests above (same BFS
    // cost profile, already fast). Escalates difficulty via a tighter move-limit `buffer` instead
    // of a deeper scrambleMoves -- scrambleMoves=9/12/15 was tried first and abandoned: a probe at
    // scrambleMoves=9 didn't complete even a single 5-trial run in 20+ minutes of active CPU time,
    // strongly suggesting a deeper scramble routinely pushes bfsMinMovesToAnyTarget's nearest-target
    // search past BFS_HARD_DEPTH_CAP=5 into the expensive exhaustive-search path (ALGORITHM_
    // VALIDATION.md R4) far more often than scrambleMoves=7 does -- the same BFS call a real Hint
    // request runs in production, so this was a real signal of potential on-device lag, not just a
    // slow test. Buffer reduction changes nothing about generation cost, only how much move slack
    // the player gets against the same real distance.
    //
    // buffer=1 was measured and REJECTED, not just untested: it passed English (0/25) but failed
    // Turkish at 2/25 (8.0%) -- a real, non-noise failure rate by this project's hard-zero standard
    // (see the R4 addendum's "must be exactly zero, not a soft/reduced-rate threshold" precedent).
    // buffer=2 passed both languages cleanly (TR 0/25, EN 0/25) and is what levels 51-100 actually
    // ship with -- see DEFAULT_DIFFICULTY_TIERS' doc comment. No buffer=1 test is kept here since
    // that combo isn't shipped anywhere; a lingering test asserting it passes would just be a
    // permanently-failing assertion for the Turkish case.

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllFourTargets5x5Buffer2Turkish() {
        val pool5 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 5 }
        val result = measureActualMovesToCompleteAllTargets(pool5, DEFAULT_FILLER_POOL, size = 5, scrambleMoves = 7, wordsPerLevel = 4, buffer = 2, trialCount = 25, seedOffset = 12_000_000)
        report("TR-5x5-4word-buffer2", result)
        assertTrue(result.unreachableCount == 0, "TR-5x5-4word-buffer2: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "TR-5x5-4word-buffer2: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play",
        )
    }

    @Test
    fun moveLimitIsCalibratedAgainstCompletingAllFourTargets5x5Buffer2English() {
        val pool5 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 5 }
        val result = measureActualMovesToCompleteAllTargets(pool5, English.fillerPool, size = 5, scrambleMoves = 7, wordsPerLevel = 4, buffer = 2, trialCount = 25, seedOffset = 13_000_000)
        report("EN-5x5-4word-buffer2", result)
        assertTrue(result.unreachableCount == 0, "EN-5x5-4word-buffer2: a target became permanently unreachable mid-playthrough (should be impossible per R4)")
        assertTrue(
            result.exceedsMoveLimitCount == 0,
            "EN-5x5-4word-buffer2: ${result.exceedsMoveLimitCount}/${result.levelsChecked} levels needed more real moves than moveLimit allowed even under optimal play",
        )
    }

    private fun report(label: String, r: CalibrationResult) {
        val actual = r.actualMovesToCompleteAll3
        val limits = r.moveLimits
        val avgActual = actual.average()
        val avgLimit = limits.average()
        val pctExceeding = 100.0 * r.exceedsMoveLimitCount / r.levelsChecked
        println(
            "[move-limit-calibration][$label] levelsChecked=${r.levelsChecked} " +
                "moveLimit(min/avg/max)=${limits.min()}/${"%.1f".format(avgLimit)}/${limits.max()} " +
                "actualMovesToCompleteAll3(min/avg/max)=${actual.min()}/${"%.1f".format(avgActual)}/${actual.max()} " +
                "exceedsMoveLimit=${r.exceedsMoveLimitCount}/${r.levelsChecked} (${"%.1f".format(pctExceeding)}%) " +
                "unreachable=${r.unreachableCount}",
        )
    }

    private fun measureActualMovesToCompleteAllTargets(
        pool: List<String>,
        fillerPool: String,
        size: Int,
        scrambleMoves: Int,
        trialCount: Int,
        seedOffset: Int,
        wordsPerLevel: Int = 3,
        buffer: Int = 3,
    ): CalibrationResult {
        var levelsChecked = 0
        val moveLimits = mutableListOf<Int>()
        val actualMoves = mutableListOf<Int>()
        var exceedsMoveLimitCount = 0
        var unreachableCount = 0

        repeat(trialCount) { i ->
            val seed = seedOffset + i
            val rng = Random(seed)
            val targets = pool.shuffled(rng).take(wordsPerLevel)
            val generated = generateLevel(size = size, targetWords = targets, scrambleMoves = scrambleMoves, rng = rng, buffer = buffer, fillerPool = fillerPool) ?: return@repeat
            levelsChecked++

            var grid = generated.levelGrid
            var remaining = targets.toSet()
            var nextId = 400_000L
            var totalMoves = 0
            var gaveUp = false

            while (remaining.isNotEmpty() && !gaveUp) {
                val bfsResult = bfsMinMovesToAnyTarget(grid, remaining)
                if (bfsResult == null) {
                    unreachableCount++
                    gaveUp = true
                    break
                }
                val remainingWhenPlanned = remaining
                for (move in bfsResult.path) {
                    if (remaining != remainingWhenPlanned) break // stale -- re-plan from scratch below
                    grid = grid.apply(move)
                    totalMoves++
                    val matches = getMatchesWithPositions(grid, remaining).filter { it.first in remaining }
                    if (matches.isNotEmpty()) {
                        val cascade = resolveCascade(
                            grid = grid,
                            targetsRemaining = remaining,
                            nextId = { nextId++ },
                            refillLetter = { fillerPool.random(rng) },
                            rng = rng,
                        )
                        grid = cascade.grid
                        remaining = cascade.remainingTargets
                    }
                }
            }

            if (!gaveUp) {
                moveLimits.add(generated.moveLimit)
                actualMoves.add(totalMoves)
                if (totalMoves > generated.moveLimit) exceedsMoveLimitCount++
            }
        }

        return CalibrationResult(levelsChecked, moveLimits, actualMoves, exceedsMoveLimitCount, unreachableCount)
    }
}
