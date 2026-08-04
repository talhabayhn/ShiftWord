package com.example.shiftword.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.English
import com.example.shiftword.model.Turkish
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Closes a TESTING_GAPS.md item: SettingsRepository had zero tests. The invariant that actually
 * matters here (see Settings.sq's comment) is that setSoundEnabled and setLanguage each use their
 * own INSERT ... ON CONFLICT-free read-then-`INSERT OR REPLACE` write specifically so toggling one
 * setting never clobbers the other back to its column default -- that's the whole reason
 * upsertSettings takes both columns every time instead of a plain single-column update.
 */
class SettingsRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: SettingsRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WordShiftDatabase.Schema.create(driver)
        repository = SettingsRepository(createDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun defaultsAreSoundOnAndTurkishBeforeAnythingIsEverSet() {
        assertTrue(repository.isSoundEnabled())
        assertEquals(Turkish.code, repository.language())
        assertFalse(repository.isWinHighlightEnabled(), "win highlight is a real difficulty lever -- must default off")
        assertEquals(DEFAULT_HINT_CREDITS, repository.hintCreditsRemaining())
        assertFalse(repository.isReducedMotionEnabled(), "reduced motion must default off -- the existing animated experience stays default")
        assertFalse(repository.isDarkModeEnabled(), "dark mode must default off -- light theme stays default")
        assertFalse(repository.isHasSeenOnboarding(), "onboarding must default unseen -- a brand-new install must always see it once")
    }

    /**
     * GAME_DESIGN.md §9h: hasSeenOnboarding is a one-time flag -- these prove it round-trips
     * correctly and, per the existing read-then-INSERT-OR-REPLACE pattern, never clobbers or is
     * clobbered by any other setting.
     */
    @Test
    fun settingHasSeenOnboardingPersistsAndDoesNotClobberOtherSettings() {
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.setWinHighlightEnabled(true)
        repository.consumeHintCredit()
        repository.setReducedMotionEnabled(true)
        repository.setDarkModeEnabled(true)

        repository.setHasSeenOnboarding(true)

        assertTrue(repository.isHasSeenOnboarding())
        assertFalse(repository.isSoundEnabled(), "setting hasSeenOnboarding must not reset sound back to its column default")
        assertEquals(English.code, repository.language(), "setting hasSeenOnboarding must not reset language back to its column default")
        assertTrue(repository.isWinHighlightEnabled(), "setting hasSeenOnboarding must not reset win highlight back to its column default")
        assertEquals(DEFAULT_HINT_CREDITS - 1, repository.hintCreditsRemaining(), "setting hasSeenOnboarding must not reset hint credits back to their column default")
        assertTrue(repository.isReducedMotionEnabled(), "setting hasSeenOnboarding must not reset reduced motion back to its column default")
        assertTrue(repository.isDarkModeEnabled(), "setting hasSeenOnboarding must not reset dark mode back to its column default")
    }

    @Test
    fun settingOtherSettingsDoesNotClobberAPreviouslySetHasSeenOnboarding() {
        repository.setHasSeenOnboarding(true)

        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.setWinHighlightEnabled(true)
        repository.consumeHintCredit()
        repository.setReducedMotionEnabled(true)
        repository.setDarkModeEnabled(true)

        assertTrue(repository.isHasSeenOnboarding(), "toggling other settings must not reset hasSeenOnboarding back to its column default")
    }

    @Test
    fun settingReducedMotionEnabledDoesNotClobberOtherSettings() {
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.setWinHighlightEnabled(true)
        repository.consumeHintCredit()
        repository.setDarkModeEnabled(true)

        repository.setReducedMotionEnabled(true)

        assertTrue(repository.isReducedMotionEnabled())
        assertFalse(repository.isSoundEnabled(), "toggling reduced motion must not reset sound back to its column default")
        assertEquals(English.code, repository.language(), "toggling reduced motion must not reset language back to its column default")
        assertTrue(repository.isWinHighlightEnabled(), "toggling reduced motion must not reset win highlight back to its column default")
        assertEquals(DEFAULT_HINT_CREDITS - 1, repository.hintCreditsRemaining(), "toggling reduced motion must not reset hint credits back to their column default")
        assertTrue(repository.isDarkModeEnabled(), "toggling reduced motion must not reset dark mode back to its column default")
    }

    @Test
    fun settingDarkModeEnabledDoesNotClobberOtherSettings() {
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.setWinHighlightEnabled(true)
        repository.consumeHintCredit()
        repository.setReducedMotionEnabled(true)

        repository.setDarkModeEnabled(true)

        assertTrue(repository.isDarkModeEnabled())
        assertFalse(repository.isSoundEnabled(), "toggling dark mode must not reset sound back to its column default")
        assertEquals(English.code, repository.language(), "toggling dark mode must not reset language back to its column default")
        assertTrue(repository.isWinHighlightEnabled(), "toggling dark mode must not reset win highlight back to its column default")
        assertEquals(DEFAULT_HINT_CREDITS - 1, repository.hintCreditsRemaining(), "toggling dark mode must not reset hint credits back to their column default")
        assertTrue(repository.isReducedMotionEnabled(), "toggling dark mode must not reset reduced motion back to its column default")
    }

    @Test
    fun settingOtherSettingsDoesNotClobberAPreviouslySetReducedMotionOrDarkMode() {
        repository.setReducedMotionEnabled(true)
        repository.setDarkModeEnabled(true)

        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.setWinHighlightEnabled(true)
        repository.consumeHintCredit()

        assertTrue(repository.isReducedMotionEnabled(), "toggling other settings must not reset reduced motion back to its column default")
        assertTrue(repository.isDarkModeEnabled(), "toggling other settings must not reset dark mode back to its column default")
    }

    @Test
    fun settingWinHighlightEnabledDoesNotClobberSoundOrLanguage() {
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.setWinHighlightEnabled(true)

        assertTrue(repository.isWinHighlightEnabled())
        assertFalse(repository.isSoundEnabled(), "toggling win highlight must not reset sound back to its column default")
        assertEquals(English.code, repository.language(), "toggling win highlight must not reset language back to its column default")
    }

    @Test
    fun settingSoundOrLanguageDoesNotClobberAPreviouslySetWinHighlight() {
        repository.setWinHighlightEnabled(true)
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)

        assertTrue(repository.isWinHighlightEnabled(), "toggling sound/language must not reset win highlight back to its column default")
    }

    @Test
    fun consumingAHintCreditDecrementsAndPersists() {
        repository.consumeHintCredit()
        assertEquals(DEFAULT_HINT_CREDITS - 1, repository.hintCreditsRemaining())
        repository.consumeHintCredit()
        assertEquals(DEFAULT_HINT_CREDITS - 2, repository.hintCreditsRemaining())
    }

    @Test
    fun consumingBelowZeroCreditsClampsAtZeroRatherThanGoingNegative() {
        repeat(DEFAULT_HINT_CREDITS + 5) { repository.consumeHintCredit() }
        assertEquals(0, repository.hintCreditsRemaining())
    }

    @Test
    fun refillHintCreditsResetsToTheDefaultRegardlessOfHowManyWereSpent() {
        repeat(DEFAULT_HINT_CREDITS) { repository.consumeHintCredit() }
        assertEquals(0, repository.hintCreditsRemaining())
        repository.refillHintCredits()
        assertEquals(DEFAULT_HINT_CREDITS, repository.hintCreditsRemaining())
    }

    /**
     * Feature 3 (GAME_DESIGN.md §9c): credits are a GLOBAL pool that refills ONLY on a genuine
     * cold start -- AppNavHost wires refillHintCredits() to fire exactly once, at its own initial
     * composition, never on menu/settings navigation or level transitions. There is no Compose UI
     * test infrastructure in this project to drive AppNavHost's navigation directly (see
     * TESTING_GAPS.md item 1), so this test instead proves the underlying repository contract
     * that behavior depends on: repeatedly reading hintCreditsRemaining() -- standing in for
     * however many times a player navigates to/from the menu or starts a new level within the
     * same process -- never changes the persisted value on its own; only an explicit
     * refillHintCredits() call does.
     */
    @Test
    fun repeatedReadsSimulatingMenuNavigationDoNotRefillCredits() {
        repository.consumeHintCredit()
        val afterOneSpend = repository.hintCreditsRemaining()

        repeat(10) { repository.hintCreditsRemaining() }

        assertEquals(afterOneSpend, repository.hintCreditsRemaining(), "reading credits repeatedly must not itself refill them")
    }

    @Test
    fun settingHintCreditsDoesNotClobberSoundOrLanguage() {
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.consumeHintCredit()

        assertFalse(repository.isSoundEnabled(), "consuming a hint credit must not reset sound back to its column default")
        assertEquals(English.code, repository.language(), "consuming a hint credit must not reset language back to its column default")
    }

    @Test
    fun settingSoundOrLanguageDoesNotClobberAPreviouslyConsumedHintCredit() {
        repository.consumeHintCredit()
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)

        assertEquals(DEFAULT_HINT_CREDITS - 1, repository.hintCreditsRemaining(), "toggling sound/language must not reset hint credits back to their column default")
    }

    @Test
    fun settingSoundEnabledDoesNotClobberAPreviouslySetLanguage() {
        repository.setLanguage(English.code)
        repository.setSoundEnabled(false)

        assertFalse(repository.isSoundEnabled())
        assertEquals(English.code, repository.language(), "toggling sound must not reset language back to its column default")
    }

    @Test
    fun settingLanguageDoesNotClobberAPreviouslySetSoundPreference() {
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)

        assertEquals(English.code, repository.language())
        assertFalse(repository.isSoundEnabled(), "toggling language must not reset soundEnabled back to its column default")
    }

    @Test
    fun bothSettingsSurviveSeveralInterleavedToggles() {
        repository.setSoundEnabled(false)
        repository.setLanguage(English.code)
        repository.setSoundEnabled(true)
        repository.setLanguage(Turkish.code)
        repository.setLanguage(English.code)

        assertTrue(repository.isSoundEnabled())
        assertEquals(English.code, repository.language())
    }

    /**
     * Real-device crash, reproduced then fixed: a device with the app already installed under
     * the pre-`winHighlightEnabled`/`hintCredits` schema (v1: id, soundEnabled, language only)
     * crashed on launch with "no such column: settings.winHighlightEnabled" (and separately,
     * before these two branches were merged, "no such column: settings.hintCredits") --
     * AndroidSqliteDriver only calls Schema.create() on a brand-new DB file; an existing
     * on-device DB is only ever brought up to date by Schema.migrate(), which does nothing unless
     * a .sqm file exists to define it. This drives the actual migration path (not just a fresh
     * Schema.create()), starting from a hand-built v1 table matching what a real installed
     * device's DB looks like, to prove 1.sqm's ALTER TABLE statements genuinely fix existing
     * installs rather than only working for brand-new ones (which every other test in this file,
     * via WordShiftDatabase.Schema.create(), would never have caught).
     */
    // 2.sqm (the Level Select migration, v2->v3) touches `level`/`progress`, not just `settings` --
    // any test that runs Schema.migrate() across version 2 needs those tables to already exist in
    // their pre-2.sqm shape (no `language` column yet), or 2.sqm's own ALTER/rebuild statements
    // fail with "no such table: level" against a synthetic settings-only database. Real devices
    // always have all three tables (Schema.create() makes them together), so this mirrors that.
    private fun createPreLevelSelectLevelAndProgressTables(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            "CREATE TABLE level (id INTEGER NOT NULL PRIMARY KEY, gridSize INTEGER NOT NULL, initialCells TEXT NOT NULL, targetWords TEXT NOT NULL, moveLimit INTEGER NOT NULL, minMovesToSolve INTEGER NOT NULL, minMovesIsExact INTEGER NOT NULL)",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE progress (levelId INTEGER NOT NULL PRIMARY KEY, stars INTEGER NOT NULL, bestMoves INTEGER NOT NULL, completedAt INTEGER NOT NULL)",
            0,
        )
    }

    @Test
    fun migratingFromTheOriginalV1SchemaAddsBothNewColumnsWithoutLosingExistingData() {
        val migrationDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        migrationDriver.execute(null, "CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, soundEnabled INTEGER NOT NULL DEFAULT 1, language TEXT NOT NULL DEFAULT 'tr')", 0)
        migrationDriver.execute(null, "INSERT INTO settings(id, soundEnabled, language) VALUES (0, 0, 'en')", 0)
        createPreLevelSelectLevelAndProgressTables(migrationDriver)

        // Migrates all the way to the latest schema (not just the version this migration used to
        // stop at) -- every migration test in this file should always target the latest schema,
        // or it stops proving what an actual already-installed device experiences on a real
        // upgrade (which always jumps straight to the latest version, never stops partway).
        WordShiftDatabase.Schema.migrate(migrationDriver, 1, WordShiftDatabase.Schema.version)

        val migratedRepository = SettingsRepository(createDatabase(migrationDriver))
        assertFalse(migratedRepository.isSoundEnabled(), "pre-existing soundEnabled value must survive the migration")
        assertEquals(English.code, migratedRepository.language(), "pre-existing language value must survive the migration")
        assertFalse(migratedRepository.isWinHighlightEnabled(), "winHighlightEnabled must land with its DEFAULT value on migrated rows, not crash or come back null")
        assertEquals(DEFAULT_HINT_CREDITS, migratedRepository.hintCreditsRemaining(), "hintCredits must land with its DEFAULT value on migrated rows, not crash or come back 0")
        assertFalse(migratedRepository.isReducedMotionEnabled(), "reducedMotionEnabled must land with its DEFAULT value on migrated rows, not crash or come back null")
        assertFalse(migratedRepository.isDarkModeEnabled(), "darkModeEnabled must land with its DEFAULT value on migrated rows, not crash or come back null")
        assertFalse(migratedRepository.isHasSeenOnboarding(), "hasSeenOnboarding must land with its DEFAULT value on migrated rows, not crash or come back null")
        migrationDriver.close()
    }

    /**
     * Same class of real-device risk as the v1->v2 migration test above (3.sqm's own doc comment
     * links back to this), but starting from the v2 schema (post-winHighlight/hintCredits,
     * pre-reducedMotion/darkMode) an already-installed device would actually have today, not a
     * from-scratch v1 table -- proves 3.sqm's two ALTER TABLE statements apply cleanly on top of
     * an already-migrated real column set, not just on the original v1 shape.
     */
    @Test
    fun migratingFromTheV2SchemaAddsReducedMotionAndDarkModeWithoutLosingExistingData() {
        val migrationDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        migrationDriver.execute(
            null,
            "CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, soundEnabled INTEGER NOT NULL DEFAULT 1, language TEXT NOT NULL DEFAULT 'tr', winHighlightEnabled INTEGER NOT NULL DEFAULT 0, hintCredits INTEGER NOT NULL DEFAULT 3)",
            0,
        )
        migrationDriver.execute(null, "INSERT INTO settings(id, soundEnabled, language, winHighlightEnabled, hintCredits) VALUES (0, 0, 'en', 1, 1)", 0)
        createPreLevelSelectLevelAndProgressTables(migrationDriver)

        WordShiftDatabase.Schema.migrate(migrationDriver, 2, WordShiftDatabase.Schema.version)

        val migratedRepository = SettingsRepository(createDatabase(migrationDriver))
        assertFalse(migratedRepository.isSoundEnabled(), "pre-existing soundEnabled value must survive the migration")
        assertEquals(English.code, migratedRepository.language(), "pre-existing language value must survive the migration")
        assertTrue(migratedRepository.isWinHighlightEnabled(), "pre-existing winHighlightEnabled value must survive the migration")
        assertEquals(1, migratedRepository.hintCreditsRemaining(), "pre-existing hintCredits value must survive the migration")
        assertFalse(migratedRepository.isReducedMotionEnabled(), "reducedMotionEnabled must land with its DEFAULT value on migrated rows, not crash or come back null")
        assertFalse(migratedRepository.isDarkModeEnabled(), "darkModeEnabled must land with its DEFAULT value on migrated rows, not crash or come back null")
        assertFalse(migratedRepository.isHasSeenOnboarding(), "hasSeenOnboarding must land with its DEFAULT value on migrated rows, not crash or come back null")
        migrationDriver.close()
    }

    // Post-2.sqm shape (language column + composite primary key) -- 4.sqm and 5.sqm both only
    // DELETE rows from these tables, they don't change their column shape, so this is what any
    // already-installed real device's level/progress tables look like by the time 5.sqm/6.sqm run.
    private fun createPostLevelSelectLevelAndProgressTablesWithSampleRows(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            "CREATE TABLE level (id INTEGER NOT NULL, language TEXT NOT NULL DEFAULT 'tr', gridSize INTEGER NOT NULL, initialCells TEXT NOT NULL, targetWords TEXT NOT NULL, moveLimit INTEGER NOT NULL, minMovesToSolve INTEGER NOT NULL, minMovesIsExact INTEGER NOT NULL, PRIMARY KEY (id, language))",
            0,
        )
        driver.execute(null, "INSERT INTO level(id, language, gridSize, initialCells, targetWords, moveLimit, minMovesToSolve, minMovesIsExact) VALUES (1, 'tr', 4, 'AAAA', 'KALE', 8, 5, 0)", 0)
        driver.execute(
            null,
            "CREATE TABLE progress (levelId INTEGER NOT NULL, language TEXT NOT NULL DEFAULT 'tr', stars INTEGER NOT NULL, bestMoves INTEGER NOT NULL, completedAt INTEGER NOT NULL, PRIMARY KEY (levelId, language))",
            0,
        )
        driver.execute(null, "INSERT INTO progress(levelId, language, stars, bestMoves, completedAt) VALUES (1, 'tr', 3, 5, 1000)", 0)
    }

    /**
     * GAME_DESIGN.md §9g's addendum / §9h: migration #3 in this project's "pre-launch progress
     * reset" pattern -- level 1's onboarding-tuned regeneration (5.sqm) wipes `level`/`progress`
     * the same way 4.sqm did for the difficulty-tiering pass, and hasSeenOnboarding (6.sqm) lands
     * in the same v4->v7 jump. Starts from a v4-shaped database (post-3.sqm, pre-5.sqm/6.sqm) with
     * real rows in `level`/`progress`, matching what an already-installed real device actually has
     * at this point, not a from-scratch Schema.create().
     */
    @Test
    fun migratingFromTheV4SchemaWipesLevelAndProgressAndAddsHasSeenOnboarding() {
        val migrationDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        migrationDriver.execute(
            null,
            "CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, soundEnabled INTEGER NOT NULL DEFAULT 1, language TEXT NOT NULL DEFAULT 'tr', winHighlightEnabled INTEGER NOT NULL DEFAULT 0, hintCredits INTEGER NOT NULL DEFAULT 3, reducedMotionEnabled INTEGER NOT NULL DEFAULT 0, darkModeEnabled INTEGER NOT NULL DEFAULT 0)",
            0,
        )
        migrationDriver.execute(null, "INSERT INTO settings(id, soundEnabled, language, winHighlightEnabled, hintCredits, reducedMotionEnabled, darkModeEnabled) VALUES (0, 0, 'en', 1, 1, 1, 1)", 0)
        createPostLevelSelectLevelAndProgressTablesWithSampleRows(migrationDriver)

        WordShiftDatabase.Schema.migrate(migrationDriver, 4, WordShiftDatabase.Schema.version)

        val migratedRepository = SettingsRepository(createDatabase(migrationDriver))
        assertFalse(migratedRepository.isSoundEnabled(), "pre-existing soundEnabled value must survive the migration")
        assertEquals(English.code, migratedRepository.language(), "pre-existing language value must survive the migration")
        assertTrue(migratedRepository.isWinHighlightEnabled(), "pre-existing winHighlightEnabled value must survive the migration")
        assertEquals(1, migratedRepository.hintCreditsRemaining(), "pre-existing hintCredits value must survive the migration")
        assertTrue(migratedRepository.isReducedMotionEnabled(), "pre-existing reducedMotionEnabled value must survive the migration")
        assertTrue(migratedRepository.isDarkModeEnabled(), "pre-existing darkModeEnabled value must survive the migration")
        assertFalse(migratedRepository.isHasSeenOnboarding(), "hasSeenOnboarding must land with its DEFAULT value on migrated rows, not crash or come back null")

        val migratedDatabase = createDatabase(migrationDriver)
        assertTrue(LevelRepository(migratedDatabase).all().isEmpty(), "5.sqm must wipe pre-existing level rows ahead of level 1's onboarding-tuned regeneration")
        assertTrue(ProgressRepository(migratedDatabase).byLevelForLanguage(Turkish.code).isEmpty(), "5.sqm must wipe pre-existing progress rows referencing the old level content")
        migrationDriver.close()
    }
}
