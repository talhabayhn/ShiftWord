package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.English
import com.example.shiftword.model.Level
import com.example.shiftword.model.Turkish
import com.example.shiftword.tools.DEFAULT_DIFFICULTY_TIERS
import com.example.shiftword.tools.DifficultyTier
import com.example.shiftword.tools.generateTieredLevelPack
import kotlin.random.Random

/** Number of levels seeded per language's pack — see GAME_DESIGN.md's Level Select section.
 * Deliberately a small, easy-to-change constant, not a config value: originally 50 (chosen to
 * comfortably cover the intended difficulty curve without over-building for a larger number),
 * expanded to 100 once that curve needed more room (GAME_DESIGN.md §5's tier extension). Bumping
 * this alone is sufficient -- no schema migration is needed: [seedPackIfNeeded]'s existing
 * idempotent-by-count check re-seeds from the same fixed [DEFAULT_DIFFICULTY_TIERS]-driven,
 * deterministic RNG sequence, which reproduces levels 1-50 byte-identically (same tier order,
 * same parameters, same seed) while genuinely adding 51-100 as new rows -- see that function's own
 * doc comment and `LevelRepositoryTest.expandingThePackSizePreservesExistingLevelsAndProgress`. */
const val LEVEL_PACK_SIZE = 100

class LevelRepository(private val database: WordShiftDatabase) {

    fun insert(level: Level) {
        database.levelQueries.insertLevel(
            id = level.id.toLong(),
            language = level.language,
            gridSize = level.gridSize.toLong(),
            initialCells = level.initialCells,
            targetWords = level.targetWords,
            moveLimit = level.moveLimit.toLong(),
            minMovesToSolve = level.minMovesToSolve.toLong(),
            minMovesIsExact = level.minMovesIsExact,
        )
    }

    fun findById(id: Int, language: String = Turkish.code): Level? =
        database.levelQueries.selectById(id.toLong(), language).executeAsOneOrNull()?.let {
            Level(
                id = it.id.toInt(),
                gridSize = it.gridSize.toInt(),
                initialCells = it.initialCells,
                targetWords = it.targetWords,
                moveLimit = it.moveLimit.toInt(),
                minMovesToSolve = it.minMovesToSolve.toInt(),
                minMovesIsExact = it.minMovesIsExact,
                language = it.language,
            )
        }

    /** All of [language]'s pack levels, ordered by level number — Level Select's list source. */
    fun allForLanguage(language: String = Turkish.code): List<Level> =
        database.levelQueries.selectAllForLanguage(language).executeAsList().map {
            Level(
                id = it.id.toInt(),
                gridSize = it.gridSize.toInt(),
                initialCells = it.initialCells,
                targetWords = it.targetWords,
                moveLimit = it.moveLimit.toInt(),
                minMovesToSolve = it.minMovesToSolve.toInt(),
                minMovesIsExact = it.minMovesIsExact,
                language = it.language,
            )
        }

    fun all(): List<Level> =
        database.levelQueries.selectAll().executeAsList().map {
            Level(
                id = it.id.toInt(),
                gridSize = it.gridSize.toInt(),
                initialCells = it.initialCells,
                targetWords = it.targetWords,
                moveLimit = it.moveLimit.toInt(),
                minMovesToSolve = it.minMovesToSolve.toInt(),
                minMovesIsExact = it.minMovesIsExact,
                language = it.language,
            )
        }

    fun clearAll() = database.levelQueries.clearAll()

    /**
     * Seeds [language]'s level pack (see [LEVEL_PACK_SIZE]) on first need, and is a no-op after
     * that — same idempotent-by-count-check pattern as `DictionaryRepository.seedIfNeeded`.
     * Generation is deterministic (a fixed [seed], not `Random.Default`): the same pack content
     * is produced on every fresh install/device, which is what actually makes "level 12" a
     * stable, reproducible puzzle rather than merely a stable *identity* pointing at whatever
     * happened to generate first. Uses the existing, unmodified `generateLevel` pipeline via
     * `generateTieredLevelPack` (Phase 6 curation tooling, difficulty-tiered per GAME_DESIGN.md
     * §5 — see `LevelPackGenerator.DEFAULT_DIFFICULTY_TIERS`) — this changes level *identity* and
     * per-tier parameters, not the generation algorithm itself.
     *
     * [wordPool] must contain words of every grid size [tiers] references (4- and 5-letter, for
     * the default tiers) — `AppNavHost` passes `DictionaryRepository.allWords(language)`
     * unfiltered by length for exactly this reason; a length-4-only pool (as some pre-tiering
     * tests used) would silently generate zero levels for the 5x5 tiers.
     *
     * **First-launch freeze fix (item 7):** for the real, default [tiers], live generation is
     * skipped entirely in favor of [PREGENERATED_LEVELS_TR]/[PREGENERATED_LEVELS_EN] --
     * bundled-in-source data produced once by `PregeneratedLevelPackExportTool` from this exact
     * [DEFAULT_DIFFICULTY_TIERS]/seed/dictionary combination. Real-device profiling found
     * `generateTieredLevelPack` blocking the main thread for 25-48s on first launch (the whole
     * 100-level pack, both languages, generated synchronously inside a Composable `remember`
     * block) -- confirmed via `Choreographer: Skipped 2044 frames!`/an HWUI `Davey!` frame, real
     * ANR risk. Since the pack is already deterministic and fixed-seed (see this function's own
     * doc comment above), there is nothing to compute at runtime: seeding becomes a handful of
     * `INSERT OR REPLACE` calls against already-known data, no BFS/placement work at all. Live
     * generation is kept as the fallback for non-default [tiers] (e.g.
     * `LevelRepositoryTest.expandingThePackSizePreservesExistingLevelsAndProgress`'s simulated
     * pre-expansion pack), so nothing here weakens that test's own guarantee.
     */
    fun seedPackIfNeeded(
        language: String,
        wordPool: List<String>,
        fillerPool: String,
        tiers: List<DifficultyTier> = DEFAULT_DIFFICULTY_TIERS,
        seed: Long = 20260101L,
    ) {
        if (database.levelQueries.countForLanguage(language).executeAsOne() >= LEVEL_PACK_SIZE.toLong()) return

        val pregenerated = if (tiers == DEFAULT_DIFFICULTY_TIERS && seed == 20260101L) {
            when (language) {
                Turkish.code -> PREGENERATED_LEVELS_TR
                English.code -> PREGENERATED_LEVELS_EN
                else -> null
            }
        } else {
            null
        }

        val levels = pregenerated ?: generateTieredLevelPack(
            wordPool = wordPool,
            tiers = tiers,
            rng = Random(seed),
            fillerPool = fillerPool,
            language = language,
        ).entries.map { it.level }

        database.transaction {
            levels.forEach { level -> insert(level) }
        }
    }
}
