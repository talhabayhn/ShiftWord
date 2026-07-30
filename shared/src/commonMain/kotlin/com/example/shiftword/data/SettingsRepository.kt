package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.Turkish

class SettingsRepository(private val database: WordShiftDatabase) {

    private fun currentRow() = database.settingsQueries.selectSettings().executeAsOneOrNull()

    private fun write(soundEnabled: Boolean, language: String, winHighlightEnabled: Boolean) {
        database.settingsQueries.upsertSettings(soundEnabled, language, winHighlightEnabled)
    }

    fun isSoundEnabled(): Boolean = currentRow()?.soundEnabled ?: true

    fun setSoundEnabled(enabled: Boolean) {
        val row = currentRow()
        write(enabled, row?.language ?: Turkish.code, row?.winHighlightEnabled ?: false)
    }

    fun language(): String = currentRow()?.language ?: Turkish.code

    fun setLanguage(code: String) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, code, row?.winHighlightEnabled ?: false)
    }

    // Feature 1B (GAME_DESIGN.md): opt-in, off-by-default drag-time highlight of a winning
    // row/column. A real difficulty lever, not a cosmetic default -- must stay false unless the
    // player explicitly opts in via Settings.
    fun isWinHighlightEnabled(): Boolean = currentRow()?.winHighlightEnabled ?: false

    fun setWinHighlightEnabled(enabled: Boolean) {
        val row = currentRow()
        write(row?.soundEnabled ?: true, row?.language ?: Turkish.code, enabled)
    }
}
