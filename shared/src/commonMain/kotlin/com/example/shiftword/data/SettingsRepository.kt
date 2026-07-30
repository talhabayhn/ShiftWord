package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.Turkish

class SettingsRepository(private val database: WordShiftDatabase) {

    fun isSoundEnabled(): Boolean =
        database.settingsQueries.selectSettings().executeAsOneOrNull()?.soundEnabled ?: true

    fun setSoundEnabled(enabled: Boolean) {
        val currentLanguage = database.settingsQueries.selectSettings().executeAsOneOrNull()?.language ?: Turkish.code
        database.settingsQueries.upsertSettings(enabled, currentLanguage)
    }

    fun language(): String =
        database.settingsQueries.selectSettings().executeAsOneOrNull()?.language ?: Turkish.code

    fun setLanguage(code: String) {
        val currentSoundEnabled = database.settingsQueries.selectSettings().executeAsOneOrNull()?.soundEnabled ?: true
        database.settingsQueries.upsertSettings(currentSoundEnabled, code)
    }
}
