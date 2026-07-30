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
