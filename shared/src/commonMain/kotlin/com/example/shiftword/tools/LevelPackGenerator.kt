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
    // Levels 51-100 (GAME_DESIGN.md §5 tier extension): escalates difficulty via a tighter
    // move-limit buffer rather than a deeper scrambleMoves -- see DEFAULT_DIFFICULTY_TIERS' doc
    // comment for why scrambleMoves was ruled out as the lever for this range.
    buffer: Int = 3,
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
        val generated = generateLevel(gridSize, targets, scrambleMoves, rng, buffer = buffer, fillerPool = fillerPool)
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
    // Levels 51-100 (GAME_DESIGN.md §5 tier extension): default 3 matches generateLevel's own
    // default, so every pre-existing tier (1-50) is unaffected unless a tier explicitly overrides
    // it. See DEFAULT_DIFFICULTY_TIERS' doc comment for why the new tiers tighten this instead of
    // scrambleMoves.
    val buffer: Int = 3,
)

/**
 * GAME_DESIGN.md §5's difficulty curve, as actually shipped. Grid size, target word count, and
 * move-limit tightness (via `scrambleMoves`) all escalate monotonically across tiers -- word
 * overlap density does NOT (see GAME_DESIGN.md §5's own note): 11-30 measures ~47-53%
 * intersection, 31-40 ~42-65%, but 41-50 drops to ~10-21%, an inherent geometric ceiling of
 * packing 4 words into a 5x5 grid's 10 rows/cols, not a bug -- see ALGORITHM_VALIDATION.md's R4
 * addendum and GeneratorMetricsTest's dedicated 5x5/4-word guards for the measured numbers this
 * combo actually ships with.
 *
 * Level 1 (GAME_DESIGN.md §9h, onboarding): split off from the rest of the original 1-10 tier as
 * its own single-level range with a much tighter `scrambleMoves=2`, not just left as the first
 * level of the normal 1-10 tier. Measured directly (`MoveLimitCalibrationTest`): the original
 * tier-1 parameters (`scrambleMoves=5`, same grid size/word count) require ~4.9 real moves on
 * average (1-9 range, 500-trial sample) to complete under optimal play -- indistinguishable in
 * difficulty from an ordinary early-game puzzle, not a "close to solved already" teaching moment
 * for a player who has never even performed the drag-to-shift gesture before. `scrambleMoves=2`
 * measures ~2.8 moves on average (1-6 range, same sample size) -- the tightest of the three
 * candidates checked, chosen deliberately since this is a single fixed-seed puzzle generated once
 * for the whole pack (not per-install), so there is no need to hedge against a wide real-world
 * distribution the way a per-tier average does. Levels 2-10 keep the original parameters
 * unchanged -- this is a one-level-only carve-out, not a tier-wide difficulty change.
 */
// Levels 51-100 (GAME_DESIGN.md §5 tier extension, pack expansion 50->100): escalates via a
// tighter move-limit buffer instead of a deeper scrambleMoves. scrambleMoves was tried first
// (9/12/15, continuing 41-50's escalation pattern) and abandoned: a MoveLimitCalibrationTest
// probe at scrambleMoves=9/5x5/4-word didn't complete even a single 5-trial run in 20+ minutes
// of active CPU time, strongly suggesting a deeper scramble routinely pushes
// bfsMinMovesToAnyTarget's nearest-target search past BFS_HARD_DEPTH_CAP=5, forcing the
// expensive exhaustive-search path (see ALGORITHM_VALIDATION.md R4) far more often than 41-50's
// scrambleMoves=7 does. That same BFS call is what a real Hint request runs in production, so
// this wasn't just a slow test -- it was a real signal that levels 51+ could make Hint feel
// laggy or freeze-prone on real devices. Buffer reduction keeps every generation parameter
// (grid size, word count, scrambleMoves) IDENTICAL to the already-shipped, already-fast 41-50
// tier -- same BFS cost profile, zero new risk -- and gets its difficulty purely from giving the
// player less slack against the same real distance.
//
// A single buffer=2 tier, not two tiers stepping down to buffer=1: measured directly
// (MoveLimitCalibrationTest), buffer=2 passed both languages cleanly (TR 0/25, EN 0/25), but
// buffer=1 -- tried for a tighter final 71-100 tier -- passed English (0/25) and FAILED Turkish
// at 2/25 (8.0%), a real failure by this project's hard-zero standard, not noise. Levels 51-100
// are therefore one tier, not a two-step escalation; a further difficulty lever for this range is
// future work, not shipped speculatively on an unvalidated buffer value.
val DEFAULT_DIFFICULTY_TIERS = listOf(
    DifficultyTier(1..1, gridSize = 4, wordsPerLevel = 2, scrambleMoves = 2),
    // Early-game generosity (item 5): +2 move-limit buffer over the default 3, so a new player's
    // first real levels (level 1 itself is separately tuned for onboarding, see above) have more
    // room for mistakes while still learning the drag-to-shift mechanic. Safe by construction,
    // not something that needed recalibration: buffer only ever ADDS to moveLimit (`generateLevel`:
    // moveLimit = minMoves + buffer), so this can only make an already-passing level MORE
    // forgiving, never risk exceedsMoveLimitCount regressing on MoveLimitCalibrationTest's
    // existing hard-zero guards.
    DifficultyTier(2..10, gridSize = 4, wordsPerLevel = 2, scrambleMoves = 5, buffer = 5),
    DifficultyTier(11..30, gridSize = 4, wordsPerLevel = 3, scrambleMoves = 5),
    DifficultyTier(31..40, gridSize = 5, wordsPerLevel = 3, scrambleMoves = 6),
    DifficultyTier(41..50, gridSize = 5, wordsPerLevel = 4, scrambleMoves = 7),
    DifficultyTier(51..100, gridSize = 5, wordsPerLevel = 4, scrambleMoves = 7, buffer = 2),
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
            buffer = tier.buffer,
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
