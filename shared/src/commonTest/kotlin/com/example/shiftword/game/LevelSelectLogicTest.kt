package com.example.shiftword.game

import com.example.shiftword.data.ProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelSelectLogicTest {

    private fun progress(vararg levelToStars: Pair<Int, Int>) =
        levelToStars.associate { (level, stars) -> level to ProgressEntry(stars = stars, bestMoves = 3, completedAtEpochMillis = 0L) }

    @Test
    fun withNoProgressOnlyLevelOneIsUnlocked() {
        val entries = buildLevelSelectEntries(packSize = 50, progressByLevel = emptyMap())

        assertTrue(entries[0].isUnlocked, "level 1 must always be unlocked, even with zero progress")
        assertNull(entries[0].stars)
        assertFalse(entries[1].isUnlocked, "level 2 must be locked until level 1 is completed")
    }

    @Test
    fun completingALevelUnlocksExactlyOneMoreBeyondIt() {
        val entries = buildLevelSelectEntries(packSize = 50, progressByLevel = progress(1 to 3, 2 to 2, 3 to 1))

        // Furthest reached = 3 -> unlocked up to 4.
        assertTrue(entries[3].isUnlocked, "level 4 (furthest reached + 1) must be unlocked")
        assertFalse(entries[4].isUnlocked, "level 5 must still be locked")
        assertEquals(3, entries[0].stars)
        assertEquals(2, entries[1].stars)
        assertEquals(1, entries[2].stars)
        assertNull(entries[3].stars, "the newly-unlocked next level hasn't been played yet")
    }

    @Test
    fun aGapInProgressStillUnlocksOnlyBasedOnTheHighestLevelReached() {
        // e.g. player jumped ahead via some other route, or data only has a later completion --
        // unlock logic must key off the MAX completed level, not assume a contiguous run.
        val entries = buildLevelSelectEntries(packSize = 50, progressByLevel = progress(1 to 3, 5 to 2))

        assertTrue(entries[4].isUnlocked, "level 5 itself, already completed, must be unlocked")
        assertTrue(entries[5].isUnlocked, "level 6 (furthest reached + 1) must be unlocked")
        assertFalse(entries[6].isUnlocked, "level 7 must still be locked")
        // Levels 2-4 were never completed, but they're still <= furthest reached (5) --
        // unlock is a threshold on level NUMBER, not on having an individual progress record.
        assertTrue(entries[1].isUnlocked)
        assertNull(entries[1].stars)
    }

    @Test
    fun completingTheFinalLevelDoesNotUnlockAnythingBeyondThePack() {
        val entries = buildLevelSelectEntries(packSize = 3, progressByLevel = progress(3 to 3))

        assertEquals(3, entries.size, "the entry list itself is bounded by packSize regardless of progress")
        assertTrue(entries.last().isUnlocked)
    }

    @Test
    fun entriesAreReturnedInLevelNumberOrderCoveringTheWholePack() {
        val entries = buildLevelSelectEntries(packSize = 5, progressByLevel = emptyMap())
        assertEquals(listOf(1, 2, 3, 4, 5), entries.map { it.levelNumber })
    }

    /**
     * Found on-device: 2.sqm's migration deliberately keeps (not deletes) pre-pack-model ad-hoc
     * progress rows, tagged 'tr' -- these have huge random-Int levelIds (e.g. 2034700723) that
     * are NOT real pack level numbers. Without bounding "furthest reached" to 1..packSize, that
     * single stray row became `maxOrNull()` and unlocked the ENTIRE 50-level pack on first
     * install over any pre-existing test/legacy data.
     */
    @Test
    fun aStrayLegacyAdHocProgressRowOutsideThePackRangeDoesNotUnlockEverything() {
        val entries = buildLevelSelectEntries(packSize = 50, progressByLevel = progress(2034700723 to 3))

        assertTrue(entries[0].isUnlocked, "level 1 must still be unlocked regardless")
        assertFalse(entries[1].isUnlocked, "the stray legacy row must not unlock level 2 onward")
        assertFalse(entries.last().isUnlocked, "must especially not unlock the WHOLE pack")
    }

    /**
     * Same bug class as above, but with MULTIPLE distinct stray legacy rows -- realistic given
     * how much ad-hoc/debug-force-complete play happened across this project's dev/testing
     * history before the pack model existed, each call to the old generateRandomLevel() assigning
     * its own independent Random.nextInt(1, Int.MAX_VALUE) id. Confirms the `filter { it in
     * 1..packSize }` bound isn't just correct for the one row it was originally caught with, but
     * for an arbitrary number of them mixed in with genuine in-range pack progress.
     */
    @Test
    fun multipleStrayLegacyRowsMixedWithRealProgressStillComputeTheCorrectUnlock() {
        val entries = buildLevelSelectEntries(
            packSize = 50,
            progressByLevel = progress(
                2034700723 to 3,
                918273645 to 1,
                4001 to 2, // also out of range, but smaller -- must not be mistaken for a real level either
                1 to 3,
                2 to 2,
            ),
        )

        // Real furthest reached is level 2 -> unlocked up to 3, regardless of how many/how large
        // the out-of-range legacy keys are.
        assertTrue(entries[2].isUnlocked, "level 3 (real furthest reached + 1) must be unlocked")
        assertFalse(entries[3].isUnlocked, "level 4 must still be locked")
        assertFalse(entries.last().isUnlocked, "must especially not unlock the whole pack")
        assertEquals(3, entries[0].stars)
        assertEquals(2, entries[1].stars)
    }
}
