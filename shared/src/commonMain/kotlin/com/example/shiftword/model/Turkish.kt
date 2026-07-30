package com.example.shiftword.model

/**
 * Explicit, locale-independent Turkish case mapping. Never use `String.uppercase()` /
 * `.lowercase()` for Turkish text — see ALGORITHM_VALIDATION.md Risk R1 (the "KAPİ" bug).
 */
object Turkish : LanguageProfile {
    val upperMap: Map<Char, Char> = mapOf(
        'i' to 'İ', 'ı' to 'I', 'ç' to 'Ç', 'ğ' to 'Ğ', 'ö' to 'Ö', 'ş' to 'Ş', 'ü' to 'Ü',
    )
    val lowerMap: Map<Char, Char> = mapOf(
        'İ' to 'i', 'I' to 'ı', 'Ç' to 'ç', 'Ğ' to 'ğ', 'Ö' to 'ö', 'Ş' to 'ş', 'Ü' to 'ü',
    )
    override val code: String = "tr"
    override val alphabetUpper: Set<Char> = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ".toSet()
    override val fillerPool: String = "ABCDEFGHIJKLMNOPRSTUVYZÇĞİÖŞÜ"

    private val asciiUpperMap: Map<Char, Char> = ('a'..'z').zip('A'..'Z').toMap()
    private val asciiLowerMap: Map<Char, Char> = ('A'..'Z').zip('a'..'z').toMap()

    override fun upper(s: String): String = s.map { ch -> upperMap[ch] ?: asciiUpperMap[ch] ?: ch }.joinToString("")
    override fun lower(s: String): String = s.map { ch -> lowerMap[ch] ?: asciiLowerMap[ch] ?: ch }.joinToString("")
}

class DictionaryValidationException(message: String) : Exception(message)

/**
 * Two independent checks: alphabet membership and upper(lower(word)) round-trip, run against
 * whichever [LanguageProfile] the word is meant to belong to (defaults to [Turkish] — the
 * original, still primary, language — so existing call sites are unaffected by Phase 9's
 * English support). Cannot catch semantic typos (e.g. "KAPI" vs "KAPİ" for door vs door-ish)
 * — only a trustworthy source dictionary fixes that. See ALGORITHM_VALIDATION.md Risk R1.
 */
fun validateDictionaryWord(word: String, profile: LanguageProfile = Turkish) {
    val invalidChars = word.toSet() - profile.alphabetUpper
    if (invalidChars.isNotEmpty()) {
        throw DictionaryValidationException("'$word': invalid character(s): $invalidChars")
    }
    val roundTrip = profile.upper(profile.lower(word))
    if (roundTrip != word) {
        throw DictionaryValidationException("'$word': round-trip inconsistent -> '$roundTrip'")
    }
}

fun buildAndValidateDictionary(words: List<String>, profile: LanguageProfile = Turkish): Set<String> {
    val validated = mutableSetOf<String>()
    for (w in words) {
        validateDictionaryWord(w, profile)
        validated.add(w)
    }
    return validated
}
