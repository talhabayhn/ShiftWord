package com.example.shiftword.game

import com.example.shiftword.data.ProgressEntry

/** One row of the Level Select screen: a level number, its star rating if completed
 * (`null` if never played), and whether it's currently playable. */
data class LevelSelectEntry(val levelNumber: Int, val stars: Int?, val isUnlocked: Boolean)

/**
 * Pure unlock-logic function, kept independent of any repository/Compose so it's trivially unit
 * testable (GAME_DESIGN.md's Level Select section) -- this is exactly the kind of state-transition
 * logic ("what's playable, what's locked") this project has gotten wrong before via untested
 * ad-hoc reasoning (stale sessions, replay bugs), so it's isolated rather than inlined into the
 * screen composable.
 *
 * Unlock rule: every level up to and including the furthest one [progressByLevel] has a
 * completion record for is unlocked, PLUS exactly one more (the next level to try) -- otherwise a
 * player could never progress past their best. Level 1 is always unlocked even with zero
 * progress. [progressByLevel] is expected scoped to one language already (see
 * ProgressRepository.byLevelForLanguage) -- this function has no language concept of its own.
 *
 * "Furthest reached" only ever considers keys within `1..packSize`: [progressByLevel] can contain
 * progress rows for level numbers outside that range -- most notably the pre-pack-model ad-hoc
 * levels kept (not deleted) by `2.sqm`'s migration, which have huge random-Int `levelId`s like
 * 2034700723. Without this bound, that single stray row becomes `maxOrNull()` and unlocks the
 * ENTIRE pack (found on-device: every one of the 50 seeded levels showed unlocked on first
 * install over pre-existing test data) -- exactly the kind of state-transition bug this function
 * was written standalone and tested specifically to avoid.
 */
fun buildLevelSelectEntries(packSize: Int, progressByLevel: Map<Int, ProgressEntry>): List<LevelSelectEntry> {
    val furthestReached = progressByLevel.keys.filter { it in 1..packSize }.maxOrNull() ?: 0
    val unlockedUpTo = (furthestReached + 1).coerceIn(1, packSize)
    return (1..packSize).map { levelNumber ->
        LevelSelectEntry(
            levelNumber = levelNumber,
            stars = progressByLevel[levelNumber]?.stars,
            isUnlocked = levelNumber <= unlockedUpTo,
        )
    }
}
