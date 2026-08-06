package com.example.shiftword

import androidx.compose.ui.window.ComposeUIViewController
import com.example.shiftword.data.DatabaseDriverFactory
import com.example.shiftword.game.SoundEffectsFactory

fun MainViewController() =
    ComposeUIViewController {
        App(
            databaseDriverFactory = DatabaseDriverFactory(),
            soundEffectsFactory = SoundEffectsFactory(),
        )
    }
