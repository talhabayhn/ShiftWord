package com.example.shiftword.tools

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GAME_DESIGN.md §9h: level 1 was carved out of the original 1-10 tier into its own single-level
 * range with a tighter `scrambleMoves`, measured (MoveLimitCalibrationTest) to bring its average
 * real solve length down from ~4.9 moves to ~2.8 -- these guard the tier config itself (not just
 * the generated output) so a future edit can't silently merge level 1 back into a wider range or
 * change its parameters without a test failing.
 */
class LevelPackGeneratorTierTest {

    @Test
    fun level1IsItsOwnTierWithATighterScrambleThanLevels2Through10() {
        val level1Tier = DEFAULT_DIFFICULTY_TIERS.first { it.levelRange == 1..1 }
        val restOfOriginalTier1 = DEFAULT_DIFFICULTY_TIERS.first { it.levelRange == 2..10 }

        assertEquals(2, level1Tier.scrambleMoves, "level 1's onboarding-tuned scramble must stay at 2")
        assertEquals(5, restOfOriginalTier1.scrambleMoves, "levels 2-10 must keep the original tier-1 scramble unchanged")
        assertEquals(level1Tier.gridSize, restOfOriginalTier1.gridSize, "level 1's carve-out must not change grid size, only scrambleMoves")
        assertEquals(level1Tier.wordsPerLevel, restOfOriginalTier1.wordsPerLevel, "level 1's carve-out must not change word count, only scrambleMoves")
    }

    @Test
    fun everyLevelNumberFrom1To50IsCoveredByExactlyOneTier() {
        val covered = DEFAULT_DIFFICULTY_TIERS.flatMap { it.levelRange.toList() }
        assertEquals((1..50).toList(), covered.sorted(), "every level 1-50 must be covered")
        assertEquals(covered.size, covered.toSet().size, "no level number may be covered by more than one tier")
    }
}
