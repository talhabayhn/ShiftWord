package com.example.shiftword

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
        // MainActivity calls enableEdgeToEdge() (correct, modern approach -- not reverted here),
        // which means Compose content draws UNDER the system status/navigation bars by default;
        // nothing consumes those insets automatically. Applied once, here at the root -- not
        // per-screen -- so every screen (splash, menu, level select, gameplay, settings) is
        // protected consistently without each one needing its own inset-handling code.
        // `safeDrawing` (not just `systemBars`) also covers display cutouts and the IME, which
        // costs nothing on screens without a cutout/keyboard and protects the ones that do.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AppNavHost(
                database = database,
                showDevTools = showDevTools,
            )
        }
    }
}
