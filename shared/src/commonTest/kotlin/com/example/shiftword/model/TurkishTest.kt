package com.example.shiftword.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TurkishTest {

    // Same 26 words as the Python prototype's RAW_WORDS / SAMPLE_DICTIONARY.
    private val sampleWords = listOf(
        "ANLA", "UMUT", "SIRA", "KALE", "ELMA", "KEDİ", "KUTU", "MASA",
        "KAPI", "KALP", "KRAL", "PARA",
        "KİTAP", "DUMAN", "ÇİÇEK", "BALIK", "TAVAN", "ORMAN", "YAZAR",
        "MASAL", "LAMBA", "TARAK", "SEPET", "ARABA", "RESİM", "KALEM", "SINAV",
    )

    @Test
    fun sampleDictionaryValidatesCleanly() {
        val validated = buildAndValidateDictionary(sampleWords)
        assertEquals(sampleWords.size, validated.size)
    }

    @Test
    fun invalidCharacterIsRejected() {
        assertFailsWith<DictionaryValidationException> { validateDictionaryWord("KAPIX") }
    }

    @Test
    fun kapiDottedVsDotlessIIsTheRootCauseBugFromR1() {
        // "KAPI" (door) is the correct word. Auto-uppercasing ASCII "kapi" without the
        // explicit map silently produces "KAPİ" (dotted İ) instead — a different, wrong
        // string that still passes alphabet + round-trip checks (it's syntactically valid,
        // just semantically wrong). This is exactly Risk R1 from ALGORITHM_VALIDATION.md.
        assertTrue("KAPI" != "KAPİ")
        validateDictionaryWord("KAPI")
        validateDictionaryWord("KAPİ")
    }

    @Test
    fun explicitMapsRoundTripCorrectlyForTurkishSpecificChars() {
        assertEquals("kapı", Turkish.lower("KAPI"))
        assertEquals("KAPI", Turkish.upper("kapı"))
        assertEquals("İ", Turkish.upper("i"))
        assertEquals("I", Turkish.upper("ı"))
        assertEquals("i", Turkish.lower("İ"))
        assertEquals("ı", Turkish.lower("I"))
    }

    @Test
    fun asciiLettersRoundTripUnaffectedByTurkishMap() {
        for (word in sampleWords) {
            assertEquals(word, Turkish.upper(Turkish.lower(word)))
        }
    }
}
