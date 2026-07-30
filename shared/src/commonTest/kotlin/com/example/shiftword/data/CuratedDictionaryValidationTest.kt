package com.example.shiftword.data

import com.example.shiftword.model.DictionaryValidationException
import com.example.shiftword.model.buildAndValidateDictionary
import com.example.shiftword.model.validateDictionaryWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CuratedDictionaryValidationTest {

    @Test
    fun theFullExpandedDictionaryValidatesCleanlyThroughTheRealGate() {
        // Authoritative check against the actual Kotlin validateDictionaryWord gate (not a
        // Python replica of its logic) — this is what actually ships.
        val validated = buildAndValidateDictionary(CURATED_DICTIONARY_SEED_WORDS)
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size, validated.size)
        assertEquals(1041, CURATED_DICTIONARY_SEED_WORDS.size)
    }

    @Test
    fun everyWordIsExactlyFourOrFiveLettersMatchingCurrentGridSizes() {
        assertTrue(CURATED_DICTIONARY_SEED_WORDS.all { it.length == 4 || it.length == 5 })
    }

    @Test
    fun noDuplicateWords() {
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size, CURATED_DICTIONARY_SEED_WORDS.toSet().size)
    }

    @Test
    fun realSourceWordsWithOttomanCircumflexVowelsAreRejectedByTheAlphabetCheck() {
        // Genuine rejections found in the raw Zemberek source (not contrived test cases):
        // Ottoman-influenced formal-register words using â/î/û (long-vowel circumflex marks)
        // that aren't part of the modern standard Turkish alphabet this game targets. This is
        // exactly why these were filtered out before reaching the curated list, demonstrated
        // here at real scale, the same reporting standard used for the Phase 6 dictionary.
        val rawSourceRejects = listOf("ÂCİZ", "ÂDET", "ÂLEM", "AŞARÎ", "ALENÎ", "AHDÎ")
        for (word in rawSourceRejects) {
            val ex = assertFailsWith<DictionaryValidationException>("expected '$word' to be rejected") {
                validateDictionaryWord(word)
            }
            assertTrue(ex.message?.contains("invalid character", ignoreCase = true) == true, "expected alphabet-check failure for '$word', got: ${ex.message}")
        }
    }
}
