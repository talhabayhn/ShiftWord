package com.example.shiftword.data

import com.example.shiftword.model.English
import com.example.shiftword.model.buildAndValidateDictionary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CuratedDictionaryEnValidationTest {

    @Test
    fun theFullEnglishDictionaryValidatesCleanlyThroughTheRealGateWithTheEnglishProfile() {
        // Authoritative check against the actual Kotlin validateDictionaryWord gate (via
        // buildAndValidateDictionary), run with the English LanguageProfile -- not a Python
        // replica, and not silently defaulting to the Turkish profile.
        val validated = buildAndValidateDictionary(CURATED_DICTIONARY_SEED_WORDS_EN, English)
        assertEquals(CURATED_DICTIONARY_SEED_WORDS_EN.size, validated.size)
        assertEquals(1073, CURATED_DICTIONARY_SEED_WORDS_EN.size)
    }

    @Test
    fun everyWordIsExactlyFourOrFiveLettersMatchingCurrentGridSizes() {
        assertTrue(CURATED_DICTIONARY_SEED_WORDS_EN.all { it.length == 4 || it.length == 5 })
    }

    @Test
    fun noDuplicateWords() {
        assertEquals(CURATED_DICTIONARY_SEED_WORDS_EN.size, CURATED_DICTIONARY_SEED_WORDS_EN.toSet().size)
    }

    @Test
    fun everyWordIsPureAsciiUppercase() {
        assertTrue(CURATED_DICTIONARY_SEED_WORDS_EN.all { word -> word.all { it in English.alphabetUpper } })
    }
}
