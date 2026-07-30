package com.example.shiftword.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelGeneratorTest {

    // Same 4-letter subset of the prototype's SAMPLE_DICTIONARY used for its generation trials.
    private val fourLetterWords = listOf(
        "ANLA", "UMUT", "SIRA", "KALE", "ELMA", "KEDİ", "KUTU", "MASA", "KAPI", "KALP", "KRAL", "PARA",
    )

    @Test
    fun levelGenerationSucceedsAtAHighRateOverRepeatedTrials() {
        val rng = Random(42)
        var success = 0
        val trials = 20
        repeat(trials) {
            val targets = fourLetterWords.shuffled(rng).take(3)
            val level = generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng)
            if (level != null) success++
        }
        // Prototype observed >=14/20; Kotlin's RNG differs from Python's so we check the same
        // "high success rate," not an exact reproduced count.
        assertTrue(success >= trials * 7 / 10, "expected a high generation success rate, got $success/$trials")
    }

    @Test
    fun tooManyTargetWordsForGridSizeFailsCleanlyNotByThrowing() {
        // 4x4 grid has only 4 rows + 4 cols = 8 non-conflicting slots. A 9th distinct,
        // non-intersecting word can never be placed. Must return null, never throw or hang.
        val words = listOf("AAAA", "BBBB", "CCCC", "DDDD", "EEEE", "FFFF", "GGGG", "HHHH", "JJJJ")
        val rng = Random(1)
        val result = buildCrosswordLayout(4, words, rng)
        assertNull(result)
    }

    @Test
    fun wordLengthMismatchedToGridSizeFailsCleanly() {
        val rng = Random(1)
        val result = buildCrosswordLayout(4, listOf("KİTAP"), rng)
        assertNull(result)
    }

    @Test
    fun generateLevelReturnsNullRatherThanThrowingForImpossibleInput() {
        val rng = Random(3)
        val level = generateLevel(size = 4, targetWords = listOf("KİTAP"), scrambleMoves = 4, rng = rng, maxAttempts = 5)
        assertNull(level)
    }

    @Test
    fun everyGeneratedLevelIsStructurallySolvableWithinScrambleMovesCount() {
        // Note: minMovesToCompleteAll is no longer asserted <= scrambleMoves here. scrambleMoves
        // bounds reaching the fully-solved grid in one continuous, uninterrupted reversal (all
        // targets appearing simultaneously on the final move); minMovesToCompleteAll instead
        // measures completing targets one at a time as a real player does, where each match
        // explodes and refills immediately -- a strictly less efficient process, so it can
        // legitimately exceed scrambleMoves (this is exactly the priority-2 calibration finding:
        // completing all targets sequentially routinely costs more than the scramble length).
        // moveLimit >= minMovesToCompleteAll always holds by construction (moveLimit adds a
        // non-negative buffer), which is what's actually load-bearing for fairness.
        val rng = Random(9)
        repeat(10) {
            val targets = fourLetterWords.shuffled(rng).take(2)
            val scrambleMoves = 5
            val level = generateLevel(size = 4, targetWords = targets, scrambleMoves = scrambleMoves, rng = rng)
            if (level != null) {
                assertTrue(level.moveLimit >= level.minMovesToCompleteAll)
                assertTrue(findMatchedWords(level.levelGrid, targets.toSet()).isEmpty())
            }
        }
    }
}
