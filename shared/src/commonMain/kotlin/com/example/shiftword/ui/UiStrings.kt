package com.example.shiftword.ui

import com.example.shiftword.model.Axis

/**
 * Phase 9: there is no Compose Multiplatform string-resource pipeline in this project (no
 * `composeResources/values/strings.xml`) — UI text up to this point was hardcoded Turkish
 * literals (with a few already-English dev-only strings mixed in). This is a from-scratch,
 * hand-rolled localization object rather than a resource-file migration, kept intentionally
 * small (exactly the strings the three screens use) since a full resource pipeline is more
 * machinery than two languages and a handful of screens currently need.
 */
data class UiStrings(
    val appTitle: String,
    val appTagline: String,
    val play: String,
    val dailyPuzzle: String,
    val settings: String,
    val wordsFoundLabel: String,
    val dayStreakLabel: String,
    val backToMenu: String,
    val moves: (used: Int, limit: Int) -> String,
    val hint: String,
    // Feature 3 (GAME_DESIGN.md §9c): hint-credit economy. hintWithCredits labels the button
    // itself (e.g. "İpucu (2)"); hintExhausted is the short message shown once the global pool
    // hits zero and the button becomes disabled.
    val hintWithCredits: (creditsRemaining: Int) -> String,
    val hintExhausted: String,
    // Priority-2 UX finding: raw 0-indexed technical phrasing ("Satır 0 geri" / "Row 0 backward")
    // reads like debug output, not a suggestion a casual player intuitively parses. Ordinal
    // (1-indexed) position + a plain directional word (left/right/up/down, not the internal
    // forward/backward convention Move.forward encodes — see ARCHITECTURE.md §2 for why row-
    // forward=right and col-forward=down) is what this now formats instead.
    val tryHint: (axis: Axis, index: Int, forward: Boolean) -> String,
    val levelComplete: String,
    val optimalMoves: (minMoves: Int, used: Int) -> String,
    val usedMoves: (used: Int) -> String,
    // Feature 2 (GAME_DESIGN.md §9b): shown alongside, not instead of, the star rating.
    val scoreLabel: (score: Int) -> String,
    val nextLevel: String,
    val playAgain: String,
    val outOfMoves: String,
    val debugTools: String,
    val forceCompleteWord: String,
    val settingsTitle: String,
    val soundLabel: String,
    val languageLabel: String,
    val languageTurkish: String,
    val languageEnglish: String,
    // Feature 1B (GAME_DESIGN.md): opt-in, off-by-default drag-time win highlight toggle label.
    // Deliberately not reusing "İpucu"/"Hint" -- that phrase now also names the hint-credit
    // economy (Feature 3) and reusing it here would blur two unrelated assists in Settings.
    val winHighlightLabel: String,
    // Level Select feature (GAME_DESIGN.md): shown as this screen's title, and as a short label
    // under a locked (not-yet-reachable) level card.
    val levelSelectTitle: String,
    val levelLocked: String,
    // Level Select feature: GameScreen's back button now actually lands on Level Select (the
    // nav backstack's previous entry from GAMEPLAY, popBackStack()), not the Main Menu -- reusing
    // backToMenu's "← Menü" label there would be inaccurate now that Level Select sits between
    // them. LevelSelectScreen's own back button still correctly uses backToMenu (it really does
    // go to Main Menu).
    val backToLevelSelect: String,
    // UI layout pass (reference: "Mobil Uygulama UI İskeleti"): shown in GameScreen's title area,
    // where a static heading used to be -- now shows which pack level the player is on.
    val levelNumberLabel: (levelNumber: Int) -> String,
)

val TurkishStrings = UiStrings(
    appTitle = "kelime kaydırma",
    appTagline = "harfleri kaydır. kelime bul. rahatla.",
    play = "Oyna",
    dailyPuzzle = "Günlük Bulmaca",
    settings = "Ayarlar",
    wordsFoundLabel = "kelime bulundu",
    dayStreakLabel = "günlük seri",
    backToMenu = "← Menü",
    moves = { used, limit -> "Hamle: $used/$limit" },
    hint = "İpucu",
    hintWithCredits = { creditsRemaining -> "İpucu ($creditsRemaining)" },
    hintExhausted = "İpucu hakkın kalmadı",
    tryHint = { axis, index, forward ->
        val axisWord = if (axis == Axis.Row) "satır" else "sütun"
        val direction = when (axis) {
            Axis.Row -> if (forward) "sağa" else "sola"
            Axis.Col -> if (forward) "aşağı" else "yukarı"
        }
        "${index + 1}. $axisWord $direction"
    },
    levelComplete = "Seviye Tamamlandı!",
    optimalMoves = { minMoves, used -> "En iyi: $minMoves hamle — sen $used kullandın" },
    usedMoves = { used -> "$used hamle kullandın" },
    scoreLabel = { score -> "Puan: $score" },
    nextLevel = "Sonraki Seviye",
    playAgain = "Tekrar Oyna",
    outOfMoves = "Hamle Kalmadı",
    debugTools = "Hata ayıklama araçları",
    forceCompleteWord = "Bir kelimeyi zorla tamamla",
    settingsTitle = "Ayarlar",
    soundLabel = "Ses",
    languageLabel = "Dil",
    languageTurkish = "Türkçe",
    languageEnglish = "İngilizce",
    winHighlightLabel = "Kazanan Hamle Vurgusu",
    levelSelectTitle = "Seviye Seç",
    levelLocked = "Kilitli",
    backToLevelSelect = "← Seviyeler",
    levelNumberLabel = { levelNumber -> "Seviye $levelNumber" },
)

val EnglishStrings = UiStrings(
    appTitle = "word shift",
    appTagline = "shift the letters. find the word. relax.",
    play = "Play",
    dailyPuzzle = "Daily Puzzle",
    settings = "Settings",
    wordsFoundLabel = "words found",
    dayStreakLabel = "day streak",
    backToMenu = "← Menu",
    moves = { used, limit -> "Moves: $used/$limit" },
    hint = "Hint",
    hintWithCredits = { creditsRemaining -> "Hint ($creditsRemaining)" },
    hintExhausted = "No hints left",
    tryHint = { axis, index, forward ->
        val axisWord = if (axis == Axis.Row) "Row" else "Col"
        val direction = when (axis) {
            Axis.Row -> if (forward) "right" else "left"
            Axis.Col -> if (forward) "down" else "up"
        }
        "$axisWord ${index + 1} $direction"
    },
    levelComplete = "Level Complete!",
    optimalMoves = { minMoves, used -> "Optimal: $minMoves moves — you used $used" },
    usedMoves = { used -> "You used $used moves" },
    scoreLabel = { score -> "Score: $score" },
    nextLevel = "Next Level",
    playAgain = "Play Again",
    outOfMoves = "Out of moves",
    debugTools = "Debug tools",
    forceCompleteWord = "Force-complete a word",
    settingsTitle = "Settings",
    soundLabel = "Sound",
    languageLabel = "Language",
    languageTurkish = "Turkish",
    languageEnglish = "English",
    winHighlightLabel = "Winning Move Highlight",
    levelSelectTitle = "Select Level",
    levelLocked = "Locked",
    backToLevelSelect = "← Levels",
    levelNumberLabel = { levelNumber -> "Level $levelNumber" },
)

fun stringsForLanguage(code: String): UiStrings = if (code == "en") EnglishStrings else TurkishStrings
