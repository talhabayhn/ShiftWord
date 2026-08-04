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
import com.example.shiftword.data.LEVEL_PACK_SIZE
import com.example.shiftword.data.LevelRepository
import com.example.shiftword.data.ProgressRepository
import com.example.shiftword.data.SettingsRepository
import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.game.GameViewModel
import com.example.shiftword.game.NoSoundEffects
import com.example.shiftword.game.SoundEffectsFactory
import com.example.shiftword.game.buildLevelSelectEntries
import com.example.shiftword.game.isOnboardingLevel
import com.example.shiftword.model.English
import com.example.shiftword.model.LanguageProfiles
import com.example.shiftword.model.Turkish
import com.example.shiftword.ui.GameScreen
import com.example.shiftword.ui.LevelSelectScreen
import com.example.shiftword.ui.MainMenuScreen
import com.example.shiftword.ui.MainMenuStats
import com.example.shiftword.ui.SettingsScreen
import com.example.shiftword.ui.SplashScreen
import com.example.shiftword.ui.stringsForLanguage
import kotlinx.coroutines.cancel
import kotlin.time.Clock

private object Routes {
    const val SPLASH = "splash"
    const val MENU = "menu"
    const val LEVEL_SELECT = "levelSelect"
    const val GAMEPLAY = "gameplay"
    const val SETTINGS = "settings"
}

/**
 * splash -> menu -> level select -> gameplay flow (menu <-> settings, level select <-> menu,
 * gameplay -> menu), per IMPLEMENTATION_ROADMAP.md Phase 7 and the Level Select addition
 * (GAME_DESIGN.md). Level Complete stays an inline GameScreen state block rather than a separate
 * route — see GameScreen's onReplaySameLevel/onNextLevel: both are just "swap the GameViewModel
 * this same screen shows," which needs no navigation transition and avoids awkward back-stack
 * semantics (what would "back" from a solved-level screen even pop to?). Reasoning recorded in
 * ARCHITECTURE.md alongside the rest of the Phase 7 decisions.
 *
 * Levels are no longer generated ad hoc per level-advance -- Step 4a's investigation (see
 * GAME_DESIGN.md) found that model gave levels no stable identity to replay by. Both languages'
 * packs (`LevelRepository.seedPackIfNeeded`, `LEVEL_PACK_SIZE` levels each) are seeded once here,
 * analogous to [DictionaryRepository.seedIfNeeded] just below -- eagerly for both languages, not
 * just the current one, so switching language in Settings always finds that language's pack
 * already there rather than needing a lazy first-time seed.
 */
@Composable
fun AppNavHost(
    database: WordShiftDatabase,
    showDevTools: Boolean = false,
    // Dark mode / Reduced Motion (added post-launch, GAME_DESIGN.md/ARCHITECTURE.md §7a):
    // constructed by the caller (App.kt) rather than here, and the current
    // darkModeEnabled/reducedMotionEnabled values are passed in rather than read fresh from the
    // repository in each nested route -- both settings drive WordShiftTheme/
    // ApplyStatusBarAppearance at App.kt's root, ABOVE this composable, so App.kt needs to own
    // the live value itself; threading it back down here (rather than this composable keeping
    // its own separate remembered copy) keeps a single source of truth instead of two states
    // that could drift out of sync. onDarkModeEnabledChange/onReducedMotionEnabledChange both
    // update that root state AND persist via settingsRepository (see App.kt) -- SETTINGS below
    // only needs to call them, not duplicate either concern itself.
    settingsRepository: SettingsRepository,
    darkModeEnabled: Boolean,
    onDarkModeEnabledChange: (Boolean) -> Unit,
    reducedMotionEnabled: Boolean,
    onReducedMotionEnabledChange: (Boolean) -> Unit,
    // Real sound files (SOUND_SOURCING.md): constructed once by the platform entry point and
    // passed all the way down from App.kt -- see SoundEffectsFactory's own doc comment for why
    // this can't just be built here directly.
    soundEffectsFactory: SoundEffectsFactory,
) {
    val navController = rememberNavController()
    val dictionaryRepository = remember { DictionaryRepository(database).also { it.seedIfNeeded() } }
    val levelRepository = remember { LevelRepository(database) }
    val progressRepository = remember { ProgressRepository(database) }

    remember {
        for (code in listOf(Turkish.code, English.code)) {
            levelRepository.seedPackIfNeeded(
                language = code,
                // Unfiltered by length (not wordsOfLength(4, code)) -- GAME_DESIGN.md §5's
                // difficulty tiers span both 4x4 and 5x5 grids (LevelPackGenerator's
                // DEFAULT_DIFFICULTY_TIERS), so the pool must carry both 4- and 5-letter words or
                // the 5x5 tiers (levels 31-50) would silently generate zero levels.
                wordPool = dictionaryRepository.allWords(code).toList(),
                fillerPool = LanguageProfiles.forCode(code).fillerPool,
            )
        }
    }

    // Feature 3 (GAME_DESIGN.md §9c): hint credits refill ONLY on a genuine cold start. This
    // remember block runs exactly once for AppNavHost's own composition lifetime (per this
    // composable's existing doc comment, that's effectively once per app process) -- never again
    // on menu/settings navigation or level transitions, which are all nested composables entered
    // and re-entered many times within this same AppNavHost instance without this line re-running.
    remember { settingsRepository.refillHintCredits() }

    // Read once per app-shell instantiation; the Settings screen mutates this same instance
    // going forward, so every route re-composed after a language change sees the new value.
    var languageCode by remember { mutableStateOf(settingsRepository.language()) }

    // Set by Level Select just before navigating to GAMEPLAY; read once when that composable
    // enters composition (matches this file's existing pattern of sharing state via AppNavHost-
    // scoped `remember`s rather than nav-route arguments -- e.g. `languageCode` above -- since
    // this app's navigation graph has no argument-passing machinery set up).
    var selectedLevelNumber by remember { mutableIntStateOf(1) }

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
                onPlay = { navController.navigate(Routes.LEVEL_SELECT) },
                // Daily Puzzle mode remains backlog (GAME_DESIGN.md §8 / Phase 6 decision) —
                // the button exists per the mockup but is intentionally a no-op, not silently
                // wired to a half-built feature.
                onDailyPuzzle = {},
                onSettings = { navController.navigate(Routes.SETTINGS) },
                strings = stringsForLanguage(languageCode),
            )
        }

        composable(Routes.LEVEL_SELECT) {
            // Re-queried fresh every time this destination re-enters composition (returning here
            // after finishing/backing out of a level should reflect whatever just changed).
            val entries = remember(languageCode) {
                buildLevelSelectEntries(LEVEL_PACK_SIZE, progressRepository.byLevelForLanguage(languageCode))
            }
            LevelSelectScreen(
                entries = entries,
                onLevelSelected = { levelNumber ->
                    selectedLevelNumber = levelNumber
                    navController.navigate(Routes.GAMEPLAY)
                },
                onBack = { navController.popBackStack() },
                strings = stringsForLanguage(languageCode),
            )
        }

        composable(Routes.GAMEPLAY) {
            // No keys needed: Navigation Compose gives this composable a fresh composition (and
            // therefore a fresh, unkeyed `remember`) every time it's navigated to, same as before
            // Level Select existed -- see this function's doc comment on why selectedLevelNumber/
            // languageCode are read via shared AppNavHost-scoped state rather than nav arguments.
            var currentLevel by remember {
                mutableStateOf(
                    checkNotNull(levelRepository.findById(selectedLevelNumber, languageCode)) {
                        "Level $selectedLevelNumber not found for language '$languageCode' -- pack seeding should have run at AppNavHost startup"
                    },
                )
            }
            var attempt by remember { mutableIntStateOf(0) }
            val soundEnabled = remember { settingsRepository.isSoundEnabled() }
            // Onboarding (GAME_DESIGN.md §9h): read once, same "fresh remember per GAMEPLAY
            // composition" pattern as soundEnabled/winHighlightEnabled above -- correct even
            // across the flip below, since a stale local `false` is exactly what a still-onboarding
            // player should keep seeing for this screen's whole lifetime (see isOnboardingLevel's
            // and GameScreen's own doc comments for why the staleness here is deliberate, not a bug).
            val hasSeenOnboarding = remember { settingsRepository.isHasSeenOnboarding() }
            val onboardingLevel = isOnboardingLevel(hasSeenOnboarding, currentLevel.id)
            // Onboarding step 4: forced ON for level 1 during onboarding regardless of the
            // player's actual Settings toggle, WITHOUT touching the persisted value -- this `||`
            // only affects the in-memory value threaded to GameScreen/GridBoard below,
            // settingsRepository.setWinHighlightEnabled is never called from here.
            val winHighlightEnabled = remember(onboardingLevel) { settingsRepository.isWinHighlightEnabled() || onboardingLevel }
            val fillerPool = remember(languageCode) { LanguageProfiles.forCode(languageCode).fillerPool }

            val viewModel = remember(currentLevel, attempt) {
                GameViewModel(
                    level = currentLevel,
                    fillerPool = fillerPool,
                    soundEffects = if (soundEnabled) soundEffectsFactory.create() else NoSoundEffects,
                    // Feature 3: read fresh on every new GameViewModel (new level or replay
                    // attempt) -- this is what makes the pool GLOBAL rather than per-level: it
                    // reflects whatever was last persisted (including credits spent earlier this
                    // session), never resets just because a new level started.
                    initialHintCredits = settingsRepository.hintCreditsRemaining(),
                    onHintUsed = { settingsRepository.consumeHintCredit() },
                    reducedMotion = reducedMotionEnabled,
                    // Onboarding steps 3/5: both gated by the same onboardingLevel flag GameScreen
                    // uses for the win-highlight override and win explanation above.
                    playOnboardingHintOnStart = onboardingLevel,
                    hintButtonCalloutEligible = onboardingLevel,
                    // Level Select feature: no more levelRepository.insert(currentLevel) here --
                    // pack levels are already persisted at seed time, not on completion, unlike
                    // the old ad-hoc model.
                    onLevelCompleted = { stars, movesUsed ->
                        progressRepository.recordCompletion(
                            levelId = currentLevel.id,
                            language = currentLevel.language,
                            stars = stars,
                            bestMoves = movesUsed,
                            completedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                        )
                        // Onboarding step 6: flips exactly once, on the player's first ever level
                        // completion -- by construction (Level Select's unlock logic keeps every
                        // level but 1 locked until it has a completion record) that's always level
                        // 1's completion while hasSeenOnboarding is still false, so no explicit
                        // level-number check is needed here. Idempotent guard (only write if not
                        // already true) keeps this a no-op on every completion after the first.
                        if (!settingsRepository.isHasSeenOnboarding()) settingsRepository.setHasSeenOnboarding(true)
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
                    // Level Select feature: "next level" now means the next NUMBER in the pack,
                    // not a freshly generated random level. At the pack's last level there's
                    // nothing further to advance to -- GameScreen only shows this button on a win,
                    // so falling back to Level Select (rather than doing nothing) is the sensible
                    // "you've finished everything currently available" outcome.
                    val next = levelRepository.findById(currentLevel.id + 1, languageCode)
                    if (next != null) {
                        currentLevel = next
                        attempt++
                    } else {
                        navController.popBackStack(Routes.LEVEL_SELECT, inclusive = false)
                    }
                },
                strings = stringsForLanguage(languageCode),
                winHighlightEnabled = winHighlightEnabled,
                // UI layout pass: `currentLevel.id` (not `selectedLevelNumber`) since it's the one
                // that actually tracks the displayed level across "Sonraki Seviye" advances -- see
                // GameScreen's levelNumber doc comment for why.
                levelNumber = currentLevel.id,
                reducedMotion = reducedMotionEnabled,
                isOnboardingLevel = onboardingLevel,
            )
        }

        composable(Routes.SETTINGS) {
            var soundEnabled by remember { mutableStateOf(settingsRepository.isSoundEnabled()) }
            var winHighlightEnabled by remember { mutableStateOf(settingsRepository.isWinHighlightEnabled()) }
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
                winHighlightEnabled = winHighlightEnabled,
                onWinHighlightEnabledChange = { enabled ->
                    winHighlightEnabled = enabled
                    settingsRepository.setWinHighlightEnabled(enabled)
                },
                reducedMotionEnabled = reducedMotionEnabled,
                onReducedMotionEnabledChange = onReducedMotionEnabledChange,
                darkModeEnabled = darkModeEnabled,
                onDarkModeEnabledChange = onDarkModeEnabledChange,
            )
        }
    }
}
