package com.example.shiftword

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.shiftword.data.DatabaseDriverFactory
import com.example.shiftword.data.createDatabase
import com.example.shiftword.navigation.AppNavHost
import com.example.shiftword.ui.theme.WordShiftTheme

// Debug/playtest tooling only (see GameScreen's DevMenu) — the app shell (androidApp's real
// BuildConfig.DEBUG, iosApp's #if DEBUG) decides this, not shared code itself, since a KMP
// common module has no reliable cross-platform notion of "is this a debug build" on its own.
@Composable
@Preview
fun App(showDevTools: Boolean = false, databaseDriverFactory: DatabaseDriverFactory) {
    WordShiftTheme {
        val database = remember { createDatabase(databaseDriverFactory.createDriver()) }
        AppNavHost(
            database = database,
            showDevTools = showDevTools,
        )
    }
}
