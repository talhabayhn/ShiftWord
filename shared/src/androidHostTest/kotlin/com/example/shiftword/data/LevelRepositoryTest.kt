package com.example.shiftword.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.English
import com.example.shiftword.model.Turkish
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Level Select feature (GAME_DESIGN.md): covers the pack-seeding/lookup mechanics that replaced
 * ad-hoc per-advance generation -- Step 4a's investigation found the old model gave levels no
 * stable identity to replay by, so these behaviors (deterministic content, per-language isolation,
 * idempotent re-seeding) are exactly what makes "the same level 12" a meaningful, testable claim.
 */
class LevelRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var levelRepository: LevelRepository

    // Unfiltered by length: seedPackIfNeeded now generates a difficulty-tiered pack
    // (GAME_DESIGN.md §5) spanning both 4x4 (levels 1-30) and 5x5 (levels 31-50) grids, so the
    // pool must carry both 4- and 5-letter words -- see seedPackIfNeeded's own doc comment.
    private val wordPool = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 || it.length == 5 }

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WordShiftDatabase.Schema.create(driver)
        levelRepository = LevelRepository(createDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun seedingProducesExactlyThePackSizeForTheRequestedLanguage() {
        levelRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)

        assertEquals(LEVEL_PACK_SIZE, levelRepository.allForLanguage(Turkish.code).size)
    }

    @Test
    fun eachPackLevelIsFindableByItsNumber() {
        levelRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)

        for (number in 1..LEVEL_PACK_SIZE) {
            assertNotNull(levelRepository.findById(number, Turkish.code), "level $number should exist after seeding")
        }
        assertNull(levelRepository.findById(LEVEL_PACK_SIZE + 1, Turkish.code), "the pack must not extend past its declared size")
    }

    @Test
    fun reSeedingIsANoOpAndDoesNotChangeExistingLevels() {
        levelRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)
        val levelFiveBefore = levelRepository.findById(5, Turkish.code)

        // Calling again (e.g. every app cold start) must not regenerate/duplicate the pack.
        levelRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)
        val levelFiveAfter = levelRepository.findById(5, Turkish.code)

        assertEquals(LEVEL_PACK_SIZE, levelRepository.allForLanguage(Turkish.code).size)
        assertEquals(levelFiveBefore, levelFiveAfter)
    }

    @Test
    fun seedingIsDeterministicAcrossFreshDatabases() {
        levelRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)
        val firstRunLevelTen = levelRepository.findById(10, Turkish.code)

        val secondDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WordShiftDatabase.Schema.create(secondDriver)
        val secondRepository = LevelRepository(createDatabase(secondDriver))
        secondRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)
        val secondRunLevelTen = secondRepository.findById(10, Turkish.code)
        secondDriver.close()

        // Same content on every fresh install/device -- a fixed seed, not Random.Default -- is
        // what makes "level 10" a stable PUZZLE, not just a stable identity pointing at whatever
        // happened to generate first.
        assertEquals(firstRunLevelTen, secondRunLevelTen)
    }

    @Test
    fun turkishAndEnglishPacksCoexistWithoutCollidingOnLevelNumber() {
        val englishWordPool = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 || it.length == 5 }
        levelRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)
        levelRepository.seedPackIfNeeded(English.code, englishWordPool, English.fillerPool)

        val turkishLevelOne = assertNotNull(levelRepository.findById(1, Turkish.code))
        val englishLevelOne = assertNotNull(levelRepository.findById(1, English.code))

        assertEquals(Turkish.code, turkishLevelOne.language)
        assertEquals(English.code, englishLevelOne.language)
        assertNotEquals(
            turkishLevelOne.targetWords,
            englishLevelOne.targetWords,
            "sanity: the two languages' level 1 must actually be different puzzles, not accidentally sharing a row",
        )
        assertEquals(LEVEL_PACK_SIZE, levelRepository.allForLanguage(Turkish.code).size)
        assertEquals(LEVEL_PACK_SIZE, levelRepository.allForLanguage(English.code).size)
    }

    @Test
    fun insertUsesOrReplaceSoASecondInsertForTheSameIdAndLanguageOverwritesRatherThanFails() {
        // Exercises the same INSERT OR REPLACE semantics 2.sqm's migration note documents as the
        // safety net for a theoretical legacy-ad-hoc-row collision: re-inserting (id, language)
        // must silently win, not throw a primary-key-violation.
        levelRepository.seedPackIfNeeded(Turkish.code, wordPool, Turkish.fillerPool)
        val original = assertNotNull(levelRepository.findById(1, Turkish.code))

        val replacement = original.copy(targetWords = listOf("YENİ"), moveLimit = 99)
        levelRepository.insert(replacement)

        assertEquals(replacement, levelRepository.findById(1, Turkish.code))
    }
}
