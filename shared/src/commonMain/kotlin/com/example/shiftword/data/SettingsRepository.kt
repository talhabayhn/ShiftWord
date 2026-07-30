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
    private fun write(soundEnabled: Boolean, language: String, hintCredits: Int) {
        database.settingsQueries.upsertSettings(soundEnabled, language, hintCredits.toLong())
    }

    fun isSoundEnabled(): Boolean = currentRow()?.soundEnabled ?: true

    fun setSoundEnabled(enabled: Boolean) {
        val row = currentRow()
        write(enabled, row?.language ?: Turkish.code, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS)
    }

    fun language(): String = currentRow()?.language ?: Turkish.code

    fun setLanguage(code: String) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, code, row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS)
    }

    // Feature 3: hint credits are a GLOBAL pool -- spending them in one level, or starting a new
    // one, must NOT refill them. Only refillHintCredits() (wired to a genuine cold start, see
    // AppNavHost) resets the count.
    fun hintCreditsRemaining(): Int = currentRow()?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS

    fun consumeHintCredit() {
        val row = currentRow()
        val newCredits = ((row?.hintCredits?.toInt() ?: DEFAULT_HINT_CREDITS) - 1).coerceAtLeast(0)
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, newCredits)
    }

    fun refillHintCredits() {
        val row = currentRow()
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, DEFAULT_HINT_CREDITS)
    }
}
