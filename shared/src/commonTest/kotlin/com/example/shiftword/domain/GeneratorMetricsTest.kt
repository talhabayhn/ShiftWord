package com.example.shiftword.domain

import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS_EN
import com.example.shiftword.model.buildAndValidateDictionary
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Phase 2 hardening measurements (ALGORITHM_VALIDATION.md R2/R6). These print actual
 * measured numbers to stdout (captured in the JUnit XML system-out) rather than only
 * asserting pass/fail, per the Phase 2 exit criteria.
 */
class GeneratorMetricsTest {

    private val fourLetterWords = listOf(
        "ANLA", "UMUT", "SIRA", "KALE", "ELMA", "KEDİ", "KUTU", "MASA", "KAPI", "KALP", "KRAL", "PARA",
    )
    private val fiveLetterWords = listOf(
        "KİTAP", "DUMAN", "ÇİÇEK", "BALIK", "TAVAN", "ORMAN", "YAZAR",
        "MASAL", "LAMBA", "TARAK", "SEPET", "ARABA", "RESİM", "KALEM", "SINAV",
    )

    // A larger, still hand-curated (not synthetic-random) pool of real 4-letter Turkish
    // words, standing in for a bigger dictionary until Phase 3 seeds a real one.
    private val largerFourLetterPool = fourLetterWords + listOf(
        "ATEŞ", "KOLA", "KOYU", "YARA", "TAVA", "KAYA", "YAZI", "DERE", "GÖRE", "MAVİ",
        "ATLI", "BABA", "ANNE", "ODUN", "YASA", "KIRK", "SOBA", "ARKA",
    )

    @Test
    fun successRateOver300TrialsAt4x4() {
        val rng = Random(42)
        val trials = 300
        var success = 0
        repeat(trials) {
            val targets = fourLetterWords.shuffled(rng).take(3)
            if (generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng) != null) success++
        }
        println("[metrics] 4x4 success rate: $success/$trials (${success * 100.0 / trials}%)")
        assertTrue(success >= trials * 9 / 10, "expected >=90% success at 4x4, got $success/$trials")
    }

    @Test
    fun successRateOver300TrialsAt5x5() {
        val rng = Random(43)
        val trials = 300
        var success = 0
        repeat(trials) {
            val targets = fiveLetterWords.shuffled(rng).take(3)
            if (generateLevel(size = 5, targetWords = targets, scrambleMoves = 6, rng = rng) != null) success++
        }
        println("[metrics] 5x5 success rate: $success/$trials (${success * 100.0 / trials}%)")
        assertTrue(success >= trials * 9 / 10, "expected >=90% success at 5x5, got $success/$trials")
    }

    @Test
    fun intersectionRateAt4x4MatchingPrototypeConditions() {
        // Same shape as the Python prototype's R2 test: 12-word four-letter pool, 15 trials,
        // 3 targets each, count trials with at least one intersecting placement.
        val rng = Random(1)
        val trials = 15
        var withIntersection = 0
        repeat(trials) {
            val targets = fourLetterWords.shuffled(rng).take(3)
            val result = generateSolvedGrid(4, targets, DEFAULT_FILLER_POOL, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 4x4 intersection rate (15-trial, matching prototype conditions): " +
                "$withIntersection/$trials — Python prototype measured 3/15 (20%) under equivalent conditions",
        )
    }

    @Test
    fun intersectionRateAt4x4LargerTrialCountForStability() {
        val rng = Random(1)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = fourLetterWords.shuffled(rng).take(3)
            val result = generateSolvedGrid(4, targets, DEFAULT_FILLER_POOL, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 4x4 intersection rate (12-word pool, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        // Guard against regressing back toward the prototype's own ~20% baseline: the
        // multi-ordering search in generateSolvedGrid should keep this comfortably higher.
        assertTrue(withIntersection >= trials * 35 / 100, "intersection rate regressed below 35%: $withIntersection/$trials")
    }

    @Test
    fun intersectionRateAt4x4WithLargerWordPool() {
        buildAndValidateDictionary(largerFourLetterPool)
        val rng = Random(1)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = largerFourLetterPool.shuffled(rng).take(3)
            val result = generateSolvedGrid(4, targets, DEFAULT_FILLER_POOL, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 4x4 intersection rate (${largerFourLetterPool.size}-word pool, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        assertTrue(withIntersection >= trials * 35 / 100, "intersection rate regressed below 35%: $withIntersection/$trials")
    }

    @Test
    fun intersectionRateAt5x5() {
        val rng = Random(1)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = fiveLetterWords.shuffled(rng).take(3)
            val result = generateSolvedGrid(5, targets, DEFAULT_FILLER_POOL, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 5x5 intersection rate (${fiveLetterWords.size}-word pool, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
    }

    // Real-scale regression guards (ALGORITHM_VALIDATION.md R2 "open item"): the guards above
    // were calibrated against small hand-picked pools (12-30 words) and pass at thresholds the
    // real curated dictionaries can't meet — not because the fix regressed, but because a
    // lexically diverse real dictionary naturally has fewer letter-overlapping target triples.
    // These four guards run against the actual shipped dictionaries (both languages), thresholded
    // with headroom below the measured real-scale numbers (see DICTIONARY_SOURCING.md), so a
    // future change that degrades real-world intersection quality gets caught even though the
    // small-pool guards wouldn't notice it. Both languages are covered so a regression specific
    // to one language's word shapes/letter distribution can't hide behind the other passing.

    @Test
    fun intersectionRateAt4x4RealTurkishDictionary() {
        val pool = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val rng = Random(10)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(3)
            val result = generateSolvedGrid(4, targets, DEFAULT_FILLER_POOL, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 4x4 intersection rate (real TR ${pool.size}-word curated dictionary, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        // Measured 30.4% (152/500) at real scale — guard at 25%, with headroom below that.
        assertTrue(withIntersection >= trials * 25 / 100, "real-scale TR 4x4 intersection rate regressed below 25%: $withIntersection/$trials")
    }

    @Test
    fun intersectionRateAt5x5RealTurkishDictionary() {
        val pool = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 5 }
        val rng = Random(11)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(3)
            val result = generateSolvedGrid(5, targets, DEFAULT_FILLER_POOL, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 5x5 intersection rate (real TR ${pool.size}-word curated dictionary, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        // Measured 42.2% (211/500) at real scale — guard at 35%, with headroom below that.
        assertTrue(withIntersection >= trials * 35 / 100, "real-scale TR 5x5 intersection rate regressed below 35%: $withIntersection/$trials")
    }

    @Test
    fun intersectionRateAt4x4RealEnglishDictionary() {
        val pool = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val rng = Random(10)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(3)
            val result = generateSolvedGrid(4, targets, com.example.shiftword.model.English.fillerPool, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 4x4 intersection rate (real EN ${pool.size}-word curated dictionary, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        // Measured 29.4% (147/500) at real scale — guard at 25%, with headroom below that.
        assertTrue(withIntersection >= trials * 25 / 100, "real-scale EN 4x4 intersection rate regressed below 25%: $withIntersection/$trials")
    }

    @Test
    fun intersectionRateAt5x5RealEnglishDictionary() {
        val pool = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 5 }
        val rng = Random(11)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(3)
            val result = generateSolvedGrid(5, targets, com.example.shiftword.model.English.fillerPool, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 5x5 intersection rate (real EN ${pool.size}-word curated dictionary, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        // Measured 55.2% (276/500) at real scale — guard at 45%, with headroom below that.
        assertTrue(withIntersection >= trials * 45 / 100, "real-scale EN 5x5 intersection rate regressed below 45%: $withIntersection/$trials")
    }

    // Levels-41-50 difficulty tier (GAME_DESIGN.md §5 scaling, shipped as-specced -- see that
    // section's note): 5x5, 4 target words instead of 3. A 4th target changes the placement
    // search's constraint density, so the 3-word real-scale guards above can't be assumed to
    // cover it -- measured fresh here, same as MoveLimitCalibrationTest's dedicated 4-word
    // variants, before this combo shipped.
    //
    // Measured 10.0% (TR) / 20.6% (EN) -- well below the 3-word 5x5 numbers (42.2%/55.2%) and
    // below every other guard's 25-45% band. Decision (not a bug, not deferred like
    // letter-rarity): shipped as-is. This is judged an inherent geometric ceiling -- 4 words
    // competing for a 5x5 grid's 10 rows/columns leaves mathematically less room per word than
    // the 3-word tier, not something pool-selection can fix -- rather than investing in
    // intersection-biasing (unscoped, uncertain payoff, same category already deferred for
    // letter-rarity). These guards are still added, calibrated to THIS combo's own measured
    // reality with headroom below it (not the usual 25-45% band), so a future regression that
    // breaks even this lower bar still gets caught rather than this combo shipping with no
    // guard at all.

    @Test
    fun intersectionRateAt5x5With4TargetsRealTurkishDictionary() {
        val pool = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 5 }
        val rng = Random(12)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(4)
            val result = generateSolvedGrid(5, targets, DEFAULT_FILLER_POOL, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 5x5 intersection rate, 4 targets (real TR ${pool.size}-word curated dictionary, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        // Measured 10.0% (50/500) at real scale -- guard at 5%, with headroom below that (not the
        // 25%+ band used elsewhere in this file; see the tier note above for why this combo's
        // ceiling is inherently lower).
        assertTrue(withIntersection >= trials * 5 / 100, "real-scale TR 5x5/4-word intersection rate regressed below 5%: $withIntersection/$trials")
    }

    @Test
    fun intersectionRateAt5x5With4TargetsRealEnglishDictionary() {
        val pool = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 5 }
        val rng = Random(13)
        val trials = 500
        var withIntersection = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(4)
            val result = generateSolvedGrid(5, targets, com.example.shiftword.model.English.fillerPool, targets.toSet(), rng)
            if (result != null) {
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println(
            "[metrics] 5x5 intersection rate, 4 targets (real EN ${pool.size}-word curated dictionary, $trials trials): " +
                "$withIntersection/$trials (${withIntersection * 100.0 / trials}%)",
        )
        // Measured 20.6% (103/500) at real scale -- guard at 10%, with headroom below that (not
        // the 25%+ band used elsewhere in this file; see the tier note above for why this combo's
        // ceiling is inherently lower).
        assertTrue(withIntersection >= trials * 10 / 100, "real-scale EN 5x5/4-word intersection rate regressed below 10%: $withIntersection/$trials")
    }

    @Test
    fun dictionaryScalePerformanceWith3000SyntheticWords() {
        // ALGORITHM_VALIDATION.md R6 claims "simulated at 3,000 words, 30 generations in
        // 64ms" but word_shift_prototype_v2.py contains no such benchmark code — there is no
        // executable Python baseline to diff against. This measures the Kotlin port fresh.
        val letters = com.example.shiftword.model.Turkish.alphabetUpper.toList()
        val rng = Random(7)
        val syntheticWords = (1..3000)
            .map { (List(4 + rng.nextInt(5)) { letters.random(rng) }).joinToString("") }
            .toSet()
            .toList()

        val validationTime = measureTime {
            buildAndValidateDictionary(syntheticWords)
        }
        println("[metrics] validated ${syntheticWords.size} synthetic words in $validationTime")

        val rng2 = Random(8)
        val generationTime = measureTime {
            repeat(30) {
                val targets = fourLetterWords.shuffled(rng2).take(3)
                generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng2)
            }
        }
        println("[metrics] 30 generateLevel calls (target-set lookup only, ${syntheticWords.size}-word dictionary unused by design) took $generationTime")
    }
}
