package com.example.shiftword

import androidx.compose.ui.window.ComposeUIViewController
import com.example.shiftword.data.DatabaseDriverFactory

fun MainViewController(showDevTools: Boolean = false) =
    ComposeUIViewController { App(showDevTools = showDevTools, databaseDriverFactory = DatabaseDriverFactory()) }
