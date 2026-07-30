package com.example.shiftword.tools

import com.example.shiftword.domain.DEFAULT_FILLER_POOL
import com.example.shiftword.domain.generateLevel
import com.example.shiftword.model.Level
import kotlin.random.Random

data class LevelPackEntry(val index: Int, val level: Level, val targetWords: List<String>)

data class LevelPackReport(
    val requested: Int,
    val entries: List<LevelPackEntry>,
    val failedAttempts: Int,
) {
    val successCount: Int get() = entries.size
    val nonExactEntries: List<LevelPackEntry> get() = entries.filter { !it.level.minMovesIsExact }
}

/**
 * Batch-generates a level pack from a curated word pool using the existing [generateLevel]
 * pipeline unchanged — this is authoring/curation tooling, not a new generation algorithm.
 * Levels where BFS didn't resolve within its hard depth cap (Risk R4) are still included (the
 * structural upper-bound move limit is still valid, per R4), but are reported separately via
 * [LevelPackReport.nonExactEntries] so a human can review whether that particular level's move
 * limit feels right before shipping it.
 */
fun generateLevelPack(
    wordPool: List<String>,
    gridSize: Int,
    wordsPerLevel: Int,
    count: Int,
    scrambleMoves: Int,
    rng: Random,
    maxAttemptsMultiplier: Int = 20,
    fillerPool: String = DEFAULT_FILLER_POOL,
): LevelPackReport {
    val pool = wordPool.filter { it.length == gridSize }
    val entries = mutableListOf<LevelPackEntry>()
    var failedAttempts = 0
    var nextId = 1
    var attempts = 0
    val maxAttempts = count * maxAttemptsMultiplier

    while (entries.size < count && attempts < maxAttempts) {
        attempts++
        val targets = pool.shuffled(rng).take(wordsPerLevel)
        val generated = generateLevel(gridSize, targets, scrambleMoves, rng, fillerPool = fillerPool)
        if (generated == null) {
            failedAttempts++
            continue
        }
        entries.add(LevelPackEntry(nextId, generated.toLevel(nextId), targets))
        nextId++
    }
    return LevelPackReport(count, entries, failedAttempts)
}
