package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.Turkish
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun todayLocalDate(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

data class ProgressEntry(val stars: Int, val bestMoves: Int, val completedAtEpochMillis: Long)

class ProgressRepository(private val database: WordShiftDatabase) {

    fun recordCompletion(levelId: Int, stars: Int, bestMoves: Int, completedAtEpochMillis: Long, language: String = Turkish.code) {
        database.progressQueries.upsertProgress(
            levelId = levelId.toLong(),
            language = language,
            stars = stars.toLong(),
            bestMoves = bestMoves.toLong(),
            completedAt = completedAtEpochMillis,
        )
    }

    /** [language]'s per-level progress, keyed by level number — Level Select's star-display and
     * unlock-logic source (see `com.example.shiftword.game.buildLevelSelectEntries`, which derives
     * "furthest reached" from this map's keys). Only completed levels have an entry; an unreached
     * level simply has none. */
    fun byLevelForLanguage(language: String): Map<Int, ProgressEntry> =
        database.progressQueries.selectAllForLanguage(language).executeAsList().associate {
            it.levelId.toInt() to ProgressEntry(stars = it.stars.toInt(), bestMoves = it.bestMoves.toInt(), completedAtEpochMillis = it.completedAt)
        }

    /**
     * Sum of target-word counts across every completed level — the main menu's "words found"
     * stat. Derived from the level table's own target lists rather than a separate running
     * counter, so it can never drift out of sync with what's actually recorded as completed.
     */
    fun totalWordsFound(): Long {
        val completed = database.progressQueries.selectAll().executeAsList()
        return completed.sumOf { row ->
            // Each progress row now carries its own language (Level Select feature) -- the join
            // must match on it too, since (levelId, language) is level's actual composite key.
            // This aggregate itself stays unpartitioned/global, summed across both languages,
            // exactly as already decided -- only the join key changed, not what's being summed.
            database.levelQueries.selectById(row.levelId, row.language).executeAsOneOrNull()?.targetWords?.size ?: 0
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
