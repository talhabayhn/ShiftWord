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
    fun everyLevelNumberFrom1To100IsCoveredByExactlyOneTier() {
        val covered = DEFAULT_DIFFICULTY_TIERS.flatMap { it.levelRange.toList() }
        assertEquals((1..100).toList(), covered.sorted(), "every level 1-100 must be covered")
        assertEquals(covered.size, covered.toSet().size, "no level number may be covered by more than one tier")
    }

    @Test
    fun levels51To100ReuseThe41To50GeometryButWithATighterBuffer() {
        // Pack expansion (GAME_DESIGN.md §5): levels 51-100 must stay on the same grid/word-count/
        // scrambleMoves combo as 41-50 (already validated -- see MoveLimitCalibrationTest and
        // GeneratorMetricsTest's density guards) and differ ONLY by a tighter buffer. A future edit
        // that drifts scrambleMoves or wordsPerLevel here would silently invalidate that existing
        // calibration/density coverage without a test catching it.
        val tier41to50 = DEFAULT_DIFFICULTY_TIERS.first { it.levelRange == 41..50 }
        val tier51to100 = DEFAULT_DIFFICULTY_TIERS.first { it.levelRange == 51..100 }

        assertEquals(tier41to50.gridSize, tier51to100.gridSize, "51-100 must keep 41-50's grid size")
        assertEquals(tier41to50.wordsPerLevel, tier51to100.wordsPerLevel, "51-100 must keep 41-50's word count")
        assertEquals(tier41to50.scrambleMoves, tier51to100.scrambleMoves, "51-100 must keep 41-50's scrambleMoves -- deeper scrambles were measured to risk expensive BFS exhaustive search, see the tier's own doc comment")
        assertEquals(3, tier41to50.buffer, "sanity: 41-50 must still use the default buffer")
        // buffer=1 was measured and rejected (2/25, 8.0% exceeded the limit for Turkish) --
        // buffer=2 is the validated floor, not an arbitrary choice.
        assertEquals(2, tier51to100.buffer, "51-100's buffer must stay at the calibrated-safe value of 2, not drift back toward the rejected buffer=1")
    }
}
