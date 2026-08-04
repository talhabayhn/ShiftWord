package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.Level
import com.example.shiftword.model.Turkish
import com.example.shiftword.tools.DEFAULT_DIFFICULTY_TIERS
import com.example.shiftword.tools.DifficultyTier
import com.example.shiftword.tools.generateTieredLevelPack
import kotlin.random.Random

/** Number of levels seeded per language's pack — see GAME_DESIGN.md's Level Select section.
 * Deliberately a small, easy-to-change constant, not a config value: 50 was chosen to comfortably
 * cover the intended difficulty curve without over-building for a larger number today. */
const val LEVEL_PACK_SIZE = 50

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
     */
    fun seedPackIfNeeded(
        language: String,
        wordPool: List<String>,
        fillerPool: String,
        tiers: List<DifficultyTier> = DEFAULT_DIFFICULTY_TIERS,
        seed: Long = 20260101L,
    ) {
        if (database.levelQueries.countForLanguage(language).executeAsOne() >= LEVEL_PACK_SIZE.toLong()) return
        val report = generateTieredLevelPack(
            wordPool = wordPool,
            tiers = tiers,
            rng = Random(seed),
            fillerPool = fillerPool,
            language = language,
        )
        database.transaction {
            report.entries.forEach { entry -> insert(entry.level) }
        }
    }
}
