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
    // Level Select feature (GAME_DESIGN.md): stamped onto every entry's Level so the pack is
    // scoped to the language its target words came from. `nextId` below is this pack's stable,
    // deterministic numbering -- see LevelRepository.seedPackIfNeeded and 2.sqm's doc
    // comment for why that determinism matters (guaranteed collision-free within/across the pack,
    // not just low-probability).
    language: String = "tr",
    // Difficulty-tiered packs (GAME_DESIGN.md §5, generateTieredLevelPack below) call this
    // per-tier and need each tier's entries numbered as a continuation of the previous tier's
    // range (e.g. tier 2 starts at 11), not restarting at 1 every time.
    startId: Int = 1,
): LevelPackReport {
    val pool = wordPool.filter { it.length == gridSize }
    val entries = mutableListOf<LevelPackEntry>()
    var failedAttempts = 0
    var nextId = startId
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
        entries.add(LevelPackEntry(nextId, generated.toLevel(nextId, language), targets))
        nextId++
    }
    return LevelPackReport(count, entries, failedAttempts)
}

/**
 * A contiguous slice of level numbers sharing one set of generation parameters -- the wiring
 * for GAME_DESIGN.md §5's difficulty curve into pack generation, which (per the levels-1-50
 * gap investigation) never actually existed before this: `generateLevelPack` alone applies one
 * fixed parameter set to an entire pack regardless of level position, which is exactly what made
 * every level in the pack feel equally difficult.
 */
data class DifficultyTier(
    val levelRange: IntRange,
    val gridSize: Int,
    val wordsPerLevel: Int,
    val scrambleMoves: Int,
)

/**
 * GAME_DESIGN.md §5's difficulty curve, as actually shipped. Grid size, target word count, and
 * move-limit tightness (via `scrambleMoves`) all escalate monotonically across tiers -- word
 * overlap density does NOT (see GAME_DESIGN.md §5's own note): 11-30 measures ~47-53%
 * intersection, 31-40 ~42-65%, but 41-50 drops to ~10-21%, an inherent geometric ceiling of
 * packing 4 words into a 5x5 grid's 10 rows/cols, not a bug -- see ALGORITHM_VALIDATION.md's R4
 * addendum and GeneratorMetricsTest's dedicated 5x5/4-word guards for the measured numbers this
 * combo actually ships with.
 */
val DEFAULT_DIFFICULTY_TIERS = listOf(
    DifficultyTier(1..10, gridSize = 4, wordsPerLevel = 2, scrambleMoves = 5),
    DifficultyTier(11..30, gridSize = 4, wordsPerLevel = 3, scrambleMoves = 5),
    DifficultyTier(31..40, gridSize = 5, wordsPerLevel = 3, scrambleMoves = 6),
    DifficultyTier(41..50, gridSize = 5, wordsPerLevel = 4, scrambleMoves = 7),
)

/**
 * Composes [generateLevelPack] once per tier, each producing its slice of the level-number range
 * with that tier's own parameters, and concatenates the results into a single pack report --
 * [wordPool] must contain words of every grid size referenced by [tiers] (each call filters to
 * its own tier's `gridSize` internally, same as a single-tier [generateLevelPack] call), or that
 * tier will silently generate zero levels.
 */
fun generateTieredLevelPack(
    wordPool: List<String>,
    tiers: List<DifficultyTier>,
    rng: Random,
    maxAttemptsMultiplier: Int = 20,
    fillerPool: String = DEFAULT_FILLER_POOL,
    language: String = "tr",
): LevelPackReport {
    val allEntries = mutableListOf<LevelPackEntry>()
    var totalRequested = 0
    var totalFailedAttempts = 0
    for (tier in tiers) {
        val count = tier.levelRange.last - tier.levelRange.first + 1
        totalRequested += count
        val tierReport = generateLevelPack(
            wordPool = wordPool,
            gridSize = tier.gridSize,
            wordsPerLevel = tier.wordsPerLevel,
            count = count,
            scrambleMoves = tier.scrambleMoves,
            rng = rng,
            maxAttemptsMultiplier = maxAttemptsMultiplier,
            fillerPool = fillerPool,
            language = language,
            startId = tier.levelRange.first,
        )
        allEntries += tierReport.entries
        totalFailedAttempts += tierReport.failedAttempts
    }
    return LevelPackReport(totalRequested, allEntries, totalFailedAttempts)
}
