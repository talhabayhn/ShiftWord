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
        assertEquals(DEFAULT_HINT_CREDITS, repository.hintCreditsRemaining())
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
}
