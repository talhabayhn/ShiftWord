package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.Turkish

// Feature 3: hint economy starting/refill credit count -- a named constant so SettingsRepository
// and any caller relying on "the default" (a brand-new row, or a cold-start refill) stay in sync.
const val DEFAULT_HINT_CREDITS = 3

class SettingsRepository(private val database: WordShiftDatabase) {

    private fun currentRow() = database.settingsQueries.selectSettings().executeAsOneOrNull()

    // SqlDelight generates plain INTEGER columns as Long -- this repository's public API stays
    // Int (matching GameViewModel/GameUiState's hintCreditsRemaining), converting at this one
    // boundary rather than leaking Long into every caller.
    private fun write(
        soundEnabled: Boolean,
        language: String,
        winHighlightEnabled: Boolean,
        hintCredits: Int,
        reducedMotionEnabled: Boolean,
        darkModeEnabled: Boolean,
        hasSeenOnboarding: Boolean,
    ) {
        database.settingsQueries.upsertSettings(soundEnabled, language, winHighlightEnabled, hintCredits.toLong(), reducedMotionEnabled, darkModeEnabled, hasSeenOnboarding)
    }

    fun isSoundEnabled(): Boolean = currentRow()?.soundEnabled ?: true

    fun setSoundEnabled(enabled: Boolean) {
        val row = currentRow()
        write(enabled, row?.language ?: Turkish.code, row?.winHighlightEnabled ?: false, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS, row?.reducedMotionEnabled ?: false, row?.darkModeEnabled ?: false, row?.hasSeenOnboarding ?: false)
    }

    fun language(): String = currentRow()?.language ?: Turkish.code

    fun setLanguage(code: String) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, code, row?.winHighlightEnabled ?: false, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS, row?.reducedMotionEnabled ?: false, row?.darkModeEnabled ?: false, row?.hasSeenOnboarding ?: false)
    }

    // Feature 1B (GAME_DESIGN.md): opt-in, off-by-default drag-time highlight of a winning
    // row/column. A real difficulty lever, not a cosmetic default -- must stay false unless the
    // player explicitly opts in via Settings.
    fun isWinHighlightEnabled(): Boolean = currentRow()?.winHighlightEnabled ?: false

    fun setWinHighlightEnabled(enabled: Boolean) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, enabled, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS, row?.reducedMotionEnabled ?: false, row?.darkModeEnabled ?: false, row?.hasSeenOnboarding ?: false)
    }

    // Feature 3: hint credits are a GLOBAL pool -- spending them in one level, or starting a new
    // one, must NOT refill them. Only refillHintCredits() (wired to a genuine cold start, see
    // AppNavHost) resets the count.
    fun hintCreditsRemaining(): Int = currentRow()?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS

    fun consumeHintCredit() {
        val row = currentRow()
        val newCredits = ((row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS) - 1).coerceAtLeast(0)
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, row?.winHighlightEnabled ?: false, newCredits, row?.reducedMotionEnabled ?: false, row?.darkModeEnabled ?: false, row?.hasSeenOnboarding ?: false)
    }

    fun refillHintCredits() {
        val row = currentRow()
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, row?.winHighlightEnabled ?: false, DEFAULT_HINT_CREDITS, row?.reducedMotionEnabled ?: false, row?.darkModeEnabled ?: false, row?.hasSeenOnboarding ?: false)
    }

    // Accessibility setting (GAME_DESIGN.md addendum): off by default -- the existing animated
    // experience stays the default for existing users, same reasoning as winHighlightEnabled.
    fun isReducedMotionEnabled(): Boolean = currentRow()?.reducedMotionEnabled ?: false

    fun setReducedMotionEnabled(enabled: Boolean) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, row?.winHighlightEnabled ?: false, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS, enabled, row?.darkModeEnabled ?: false, row?.hasSeenOnboarding ?: false)
    }

    // Appearance setting (ARCHITECTURE.md §7a): off by default -- light theme stays default.
    fun isDarkModeEnabled(): Boolean = currentRow()?.darkModeEnabled ?: false

    fun setDarkModeEnabled(enabled: Boolean) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, row?.winHighlightEnabled ?: false, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS, row?.reducedMotionEnabled ?: false, enabled, row?.hasSeenOnboarding ?: false)
    }

    // Onboarding (GAME_DESIGN.md §9h): one-time, first-install-only -- NOT re-triggerable from
    // Settings (no setter exists to flip this back to false; setHasSeenOnboarding only ever moves
    // true). Read once per gameplay-composable entry by AppNavHost, same pattern as
    // isWinHighlightEnabled/isSoundEnabled above.
    fun isHasSeenOnboarding(): Boolean = currentRow()?.hasSeenOnboarding ?: false

    fun setHasSeenOnboarding(seen: Boolean) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, row?.winHighlightEnabled ?: false, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS, row?.reducedMotionEnabled ?: false, row?.darkModeEnabled ?: false, seen)
    }
}
