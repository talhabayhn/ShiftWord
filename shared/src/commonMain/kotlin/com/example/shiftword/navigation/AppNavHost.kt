package com.example.shiftword.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shiftword.data.DictionaryRepository
import com.example.shiftword.data.LevelRepository
import com.example.shiftword.data.ProgressRepository
import com.example.shiftword.data.SettingsRepository
import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.domain.generateLevel
import com.example.shiftword.game.GameViewModel
import com.example.shiftword.game.NoSoundEffects
import com.example.shiftword.game.platformSoundEffects
import com.example.shiftword.model.Level
import com.example.shiftword.model.LanguageProfiles
import com.example.shiftword.ui.GameScreen
import com.example.shiftword.ui.MainMenuScreen
import com.example.shiftword.ui.MainMenuStats
import com.example.shiftword.ui.SettingsScreen
import com.example.shiftword.ui.SplashScreen
import com.example.shiftword.ui.stringsForLanguage
import kotlinx.coroutines.cancel
import kotlin.random.Random
import kotlin.time.Clock

private object Routes {
    const val SPLASH = "splash"
    const val MENU = "menu"
    const val GAMEPLAY = "gameplay"
    const val SETTINGS = "settings"
}

/**
 * Reads target words from the persisted, validator-gated dictionary (DictionaryRepository),
 * not the in-memory CURATED_DICTIONARY_SEED_WORDS* constants directly -- an audit found that
 * AppNavHost previously bypassed the repository entirely, meaning the R1 validator gate and the
 * `word` table it protects were never actually exercised by the running app, only by tests. The
 * constants are now only ever read once, by DictionaryRepository.seedIfNeeded(), to seed the DB.
 */
private fun generateRandomLevel(languageCode: String, dictionaryRepository: DictionaryRepository): Level {
    val fourLetterWords = dictionaryRepository.wordsOfLength(4, languageCode)
    val targets = fourLetterWords.shuffled(Random.Default).take(3)
    val generated = checkNotNull(
        generateLevel(
            size = 4,
            targetWords = targets,
            scrambleMoves = 5,
            rng = Random.Default,
            fillerPool = LanguageProfiles.forCode(languageCode).fillerPool,
        ),
    )
    return generated.toLevel(id = Random.nextInt(1, Int.MAX_VALUE))
}

/**
 * splash -> menu -> gameplay flow (menu <-> settings, gameplay -> menu), per
 * IMPLEMENTATION_ROADMAP.md Phase 7. Level Complete stays an inline GameScreen state block
 * rather than a separate route — see GameScreen's onReplaySameLevel/onNextLevel: both are just
 * "swap the GameViewModel this same screen shows," which needs no navigation transition and
 * avoids awkward back-stack semantics (what would "back" from a solved-level screen even pop
 * to?). Reasoning recorded in ARCHITECTURE.md alongside the rest of the Phase 7 decisions.
 */
@Composable
fun AppNavHost(database: WordShiftDatabase, showDevTools: Boolean = false) {
    val navController = rememberNavController()
    val dictionaryRepository = remember { DictionaryRepository(database).also { it.seedIfNeeded() } }
    val levelRepository = remember { LevelRepository(database) }
    val progressRepository = remember { ProgressRepository(database) }
    val settingsRepository = remember { SettingsRepository(database) }

    // Read once per app-shell instantiation; the Settings screen mutates this same instance
    // going forward, so every route re-composed after a language change sees the new value.
    var languageCode by remember { mutableStateOf(settingsRepository.language()) }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.MENU) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MENU) {
            // Re-queried fresh every time this destination re-enters composition (e.g.
            // returning from gameplay after a completion), not cached across navigations.
            val stats = remember {
                MainMenuStats(
                    wordsFound = progressRepository.totalWordsFound(),
                    dayStreak = progressRepository.currentDayStreak(),
                )
            }
            MainMenuScreen(
                stats = stats,
                onPlay = { navController.navigate(Routes.GAMEPLAY) },
                // Daily Puzzle mode remains backlog (GAME_DESIGN.md §8 / Phase 6 decision) —
                // the button exists per the mockup but is intentionally a no-op, not silently
                // wired to a half-built feature.
                onDailyPuzzle = {},
                onSettings = { navController.navigate(Routes.SETTINGS) },
                strings = stringsForLanguage(languageCode),
            )
        }

        composable(Routes.GAMEPLAY) {
            var currentLevel by remember { mutableStateOf(generateRandomLevel(languageCode, dictionaryRepository)) }
            var attempt by remember { mutableIntStateOf(0) }
            val soundEnabled = remember { settingsRepository.isSoundEnabled() }
            val fillerPool = remember(languageCode) { LanguageProfiles.forCode(languageCode).fillerPool }

            val viewModel = remember(currentLevel, attempt) {
                GameViewModel(
                    level = currentLevel,
                    fillerPool = fillerPool,
                    soundEffects = if (soundEnabled) platformSoundEffects() else NoSoundEffects,
                    onLevelCompleted = { stars, movesUsed ->
                        levelRepository.insert(currentLevel)
                        progressRepository.recordCompletion(
                            levelId = currentLevel.id,
                            stars = stars,
                            bestMoves = movesUsed,
                            completedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                        )
                    },
                )
            }

            // Audit finding 4d: GameViewModel is constructed via a plain remember(...) call, not
            // through a ViewModelStoreOwner/factory, so nothing cancelled its viewModelScope when
            // this composable left composition (navigating back to menu, or a new GameViewModel
            // replacing this one on replay/next-level) -- an in-flight cascade (including
            // onLevelCompleted, which writes to the database) kept running after the player had
            // already moved on. Cancelling on dispose means a level in progress when the player
            // leaves does NOT silently finish counting as completed behind their back, matching
            // normal game UX expectations: it only counts if you're still there to see it resolve.
            DisposableEffect(viewModel) {
                onDispose { viewModel.viewModelScope.cancel() }
            }

            GameScreen(
                viewModel = viewModel,
                showDevTools = showDevTools,
                onBackToMenu = { navController.popBackStack() },
                onReplaySameLevel = { attempt++ },
                onNextLevel = {
                    currentLevel = generateRandomLevel(languageCode, dictionaryRepository)
                    attempt++
                },
                strings = stringsForLanguage(languageCode),
            )
        }

        composable(Routes.SETTINGS) {
            var soundEnabled by remember { mutableStateOf(settingsRepository.isSoundEnabled()) }
            SettingsScreen(
                soundEnabled = soundEnabled,
                onSoundEnabledChange = { enabled ->
                    soundEnabled = enabled
                    settingsRepository.setSoundEnabled(enabled)
                },
                onBack = { navController.popBackStack() },
                languageCode = languageCode,
                onLanguageCodeChange = { code ->
                    languageCode = code
                    settingsRepository.setLanguage(code)
                },
                strings = stringsForLanguage(languageCode),
            )
        }
    }
}
