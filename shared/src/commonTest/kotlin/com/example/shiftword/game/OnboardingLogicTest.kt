package com.example.shiftword.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GAME_DESIGN.md §9h: [isOnboardingLevel] is the single gate every onboarding surface (auto-played
 * hint nudge, forced win highlight, hint-button callout, first-win explanation) shares -- these
 * cases prove it is provably tied to `hasSeenOnboarding`, not accidentally re-triggerable once that
 * flag flips true, and not accidentally true for any level other than 1.
 */
class OnboardingLogicTest {

    @Test
    fun trueOnlyForLevel1BeforeOnboardingHasBeenSeen() {
        assertTrue(isOnboardingLevel(hasSeenOnboarding = false, levelNumber = 1))
    }

    @Test
    fun falseForLevel1OnceOnboardingHasBeenSeen() {
        assertFalse(isOnboardingLevel(hasSeenOnboarding = true, levelNumber = 1), "must never be re-triggerable once hasSeenOnboarding is true")
    }

    @Test
    fun falseForAnyOtherLevelEvenBeforeOnboardingHasBeenSeen() {
        for (level in listOf(2, 3, 10, 11, 50)) {
            assertFalse(isOnboardingLevel(hasSeenOnboarding = false, levelNumber = level), "level $level must never be treated as the onboarding level")
        }
    }

    @Test
    fun falseForAnyOtherLevelOnceOnboardingHasBeenSeen() {
        for (level in listOf(1, 2, 10, 50)) {
            assertFalse(isOnboardingLevel(hasSeenOnboarding = true, levelNumber = level))
        }
    }
}
