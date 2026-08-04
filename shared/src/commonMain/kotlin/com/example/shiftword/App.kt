package com.example.shiftword

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.shiftword.data.DatabaseDriverFactory
import com.example.shiftword.data.SettingsRepository
import com.example.shiftword.data.createDatabase
import com.example.shiftword.game.SoundEffectsFactory
import com.example.shiftword.navigation.AppNavHost
import com.example.shiftword.ui.ApplyStatusBarAppearance
import com.example.shiftword.ui.theme.WordShiftTheme

// Debug/playtest tooling only (see GameScreen's DevMenu) — the app shell (androidApp's real
// BuildConfig.DEBUG, iosApp's #if DEBUG) decides this, not shared code itself, since a KMP
// common module has no reliable cross-platform notion of "is this a debug build" on its own.
@Composable
@Preview
fun App(
    showDevTools: Boolean = false,
    databaseDriverFactory: DatabaseDriverFactory,
    // Real sound files (SOUND_SOURCING.md): constructed by the platform entry point
    // (MainActivity.kt / MainViewController.kt), same reasoning as databaseDriverFactory above --
    // Android's implementation needs a Context, iOS's doesn't, so only platform-specific code can
    // build one. Threaded straight through to AppNavHost's GAMEPLAY route, which is the only
    // place a real GameViewModel (and therefore real SoundEffects) gets constructed.
    soundEffectsFactory: SoundEffectsFactory,
) {
    val database = remember { createDatabase(databaseDriverFactory.createDriver()) }
    // Read once at app-shell construction; App owns the live in-memory value from here on so
    // WordShiftTheme (below) and ApplyStatusBarAppearance both react immediately to a change made
    // in Settings, without waiting for a recomposition triggered from elsewhere. SettingsScreen
    // (via AppNavHost's onDarkModeEnabledChange/onReducedMotionEnabledChange callbacks) both
    // updates this state AND persists through settingsRepository -- see AppNavHost's doc comment.
    val settingsRepository = remember { SettingsRepository(database) }
    var darkModeEnabled by remember { mutableStateOf(settingsRepository.isDarkModeEnabled()) }
    var reducedMotionEnabled by remember { mutableStateOf(settingsRepository.isReducedMotionEnabled()) }

    WordShiftTheme(darkTheme = darkModeEnabled) {
        ApplyStatusBarAppearance(darkTheme = darkModeEnabled)
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
                settingsRepository = settingsRepository,
                soundEffectsFactory = soundEffectsFactory,
                darkModeEnabled = darkModeEnabled,
                onDarkModeEnabledChange = { enabled ->
                    darkModeEnabled = enabled
                    settingsRepository.setDarkModeEnabled(enabled)
                },
                reducedMotionEnabled = reducedMotionEnabled,
                onReducedMotionEnabledChange = { enabled ->
                    reducedMotionEnabled = enabled
                    settingsRepository.setReducedMotionEnabled(enabled)
                },
            )
        }
    }
}
