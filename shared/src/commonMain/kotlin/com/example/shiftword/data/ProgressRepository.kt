package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun todayLocalDate(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class ProgressRepository(private val database: WordShiftDatabase) {

    fun recordCompletion(levelId: Int, stars: Int, bestMoves: Int, completedAtEpochMillis: Long) {
        database.progressQueries.upsertProgress(
            levelId = levelId.toLong(),
            stars = stars.toLong(),
            bestMoves = bestMoves.toLong(),
            completedAt = completedAtEpochMillis,
        )
    }

    /**
     * Sum of target-word counts across every completed level — the main menu's "words found"
     * stat. Derived from the level table's own target lists rather than a separate running
     * counter, so it can never drift out of sync with what's actually recorded as completed.
     */
    fun totalWordsFound(): Long {
        val completed = database.progressQueries.selectAll().executeAsList()
        return completed.sumOf { row ->
            database.levelQueries.selectById(row.levelId).executeAsOneOrNull()?.targetWords?.size ?: 0
        }.toLong()
    }

    /**
     * Consecutive days with at least one level completion, ending at the most recent
     * completion day. A streak is only considered broken once a full day has been skipped —
     * if the player's last completion was yesterday, the streak still counts today (matching
     * common daily-streak UX, e.g. Duolingo) rather than dropping to 0 the instant a new day
     * starts before they've played. Returns 0 if there's no progress at all, or if more than
     * one day has passed since the last completion.
     */
    fun currentDayStreak(today: LocalDate = todayLocalDate()): Int {
        val completedDays = database.progressQueries.selectAll().executeAsList()
            .map { Instant.fromEpochMilliseconds(it.completedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date }
            .toSet()
        if (completedDays.isEmpty()) return 0

        val mostRecent = completedDays.max()
        val daysSinceLastCompletion = today.toEpochDays() - mostRecent.toEpochDays()
        if (daysSinceLastCompletion > 1) return 0

        var streak = 0
        var day = mostRecent
        while (day in completedDays) {
            streak++
            day = day.minus(DatePeriod(days = 1))
        }
        return streak
    }

    fun clearAll() = database.progressQueries.clearAll()
}
