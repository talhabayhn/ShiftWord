package com.example.shiftword.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.Level
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var levelRepository: LevelRepository
    private lateinit var progressRepository: ProgressRepository

    private val today = LocalDate(2026, 7, 28)

    private fun epochMillisOn(date: LocalDate): Long =
        date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun levelWithWords(id: Int, words: List<String>) = Level(
        id = id,
        gridSize = words.first().length,
        initialCells = List(words.first().length) { List(words.first().length) { 'P' } },
        targetWords = words,
        moveLimit = 5,
        minMovesToSolve = 2,
        minMovesIsExact = true,
    )

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WordShiftDatabase.Schema.create(driver)
        val database = createDatabase(driver)
        levelRepository = LevelRepository(database)
        progressRepository = ProgressRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun totalWordsFoundSumsTargetWordCountsOfCompletedLevelsOnly() {
        levelRepository.insert(levelWithWords(1, listOf("KALE", "UMUT")))
        levelRepository.insert(levelWithWords(2, listOf("SIRA", "ELMA", "KEDİ")))
        levelRepository.insert(levelWithWords(3, listOf("KUTU"))) // never completed

        progressRepository.recordCompletion(1, stars = 3, bestMoves = 2, completedAtEpochMillis = epochMillisOn(today))
        progressRepository.recordCompletion(2, stars = 2, bestMoves = 4, completedAtEpochMillis = epochMillisOn(today))

        assertEquals(5L, progressRepository.totalWordsFound())
    }

    @Test
    fun noProgressMeansZeroWordsFoundAndZeroStreak() {
        assertEquals(0L, progressRepository.totalWordsFound())
        assertEquals(0, progressRepository.currentDayStreak(today))
    }

    @Test
    fun consecutiveDaysEndingTodayCountTowardTheStreak() {
        // Each day's play completes a different level — progress is keyed one-row-per-level
        // (its best result), so a multi-day history naturally comes from distinct levels
        // completed on distinct days, not the same level re-completed.
        levelRepository.insert(levelWithWords(1, listOf("KALE")))
        levelRepository.insert(levelWithWords(2, listOf("UMUT")))
        levelRepository.insert(levelWithWords(3, listOf("SIRA")))
        progressRepository.recordCompletion(1, 3, 2, epochMillisOn(today))
        progressRepository.recordCompletion(2, 3, 2, epochMillisOn(today.minus(DatePeriod(days = 1))))
        progressRepository.recordCompletion(3, 3, 2, epochMillisOn(today.minus(DatePeriod(days = 2))))

        assertEquals(3, progressRepository.currentDayStreak(today))
    }

    @Test
    fun aCompletionYesterdayStillCountsTowardTodaysStreak() {
        // Player hasn't opened the app yet today — the streak they earned through yesterday
        // must still show, not reset to 0 just because "today" has no completion yet.
        levelRepository.insert(levelWithWords(1, listOf("KALE")))
        levelRepository.insert(levelWithWords(2, listOf("UMUT")))
        progressRepository.recordCompletion(1, 3, 2, epochMillisOn(today.minus(DatePeriod(days = 1))))
        progressRepository.recordCompletion(2, 3, 2, epochMillisOn(today.minus(DatePeriod(days = 2))))

        assertEquals(2, progressRepository.currentDayStreak(today))
    }

    @Test
    fun missingAFullDayBreaksTheStreak() {
        levelRepository.insert(levelWithWords(1, listOf("KALE")))
        progressRepository.recordCompletion(1, 3, 2, epochMillisOn(today.minus(DatePeriod(days = 2))))

        assertEquals(0, progressRepository.currentDayStreak(today))
    }

    @Test
    fun aGapInTheMiddleStopsCountingPastTheGap() {
        levelRepository.insert(levelWithWords(1, listOf("KALE")))
        levelRepository.insert(levelWithWords(2, listOf("UMUT")))
        progressRepository.recordCompletion(1, 3, 2, epochMillisOn(today))
        // Skip yesterday entirely.
        progressRepository.recordCompletion(2, 3, 2, epochMillisOn(today.minus(DatePeriod(days = 2))))

        assertEquals(1, progressRepository.currentDayStreak(today))
    }
}
