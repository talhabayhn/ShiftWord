package com.example.shiftword.model

/**
 * Everything the dictionary validator and level generator need to know about a language:
 * its alphabet, locale-independent case folding, and the letter pool used to refill cells
 * after a cascade. [Turkish] and [English] are the two implementations; nothing in
 * `domain/` (GridEngine, Solver, LevelGenerator) depends on either directly — those functions
 * take `Set<String>`/`List<String>`/filler-pool `String` as plain parameters, so a profile only
 * needs to be resolved once, at the data/import and app-shell boundary (DictionaryRepository,
 * AppNavHost, GameViewModel), per ARCHITECTURE.md's "everything not a UI concern is pure
 * Kotlin" layering.
 */
interface LanguageProfile {
    val code: String
    val alphabetUpper: Set<Char>
    val fillerPool: String
    fun upper(s: String): String
    fun lower(s: String): String
}

/** Resolves a persisted/settings language code back to its [LanguageProfile]. Unknown codes
 * fall back to [Turkish] — the app's original default — rather than throwing. */
object LanguageProfiles {
    fun forCode(code: String): LanguageProfile = when (code) {
        English.code -> English
        else -> Turkish
    }
}
