package com.example.shiftword.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * English-language-parity counterpart to TurkishTest.kt (closing a TESTING_GAPS.md item). English
 * has no locale-dependent case-folding bug the way Turkish's dotted/dotless I does — this suite
 * exists to confirm that absence directly (standard A-Z case folding round-trips correctly, and
 * the alphabet check correctly rejects characters outside English's 26-letter alphabet, including
 * Turkish-specific letters), not to leave it as an unverified assumption.
 */
class EnglishTest {

    private val sampleWords = listOf(
        "LIVE", "LIAR", "LUCK", "DEBT", "COAT", "PAST", "ZONE", "SIGN", "DIRT",
        "PITY", "WEEK", "TYPE", "GRACE", "GRANT", "PENNY", "TEDDY", "ROBIN",
    )

    @Test
    fun sampleDictionaryValidatesCleanly() {
        val validated = buildAndValidateDictionary(sampleWords, English)
        assertEquals(sampleWords.size, validated.size)
    }

    @Test
    fun invalidCharacterIsRejected() {
        assertFailsWith<DictionaryValidationException> { validateDictionaryWord("LIVE1", English) }
    }

    @Test
    fun turkishSpecificLettersAreRejectedByTheEnglishAlphabetCheck() {
        // Confirms the profile boundary actually holds: a word that's perfectly valid under
        // Turkish (or contains a Turkish-only letter) must not silently pass under English.
        val ex = assertFailsWith<DictionaryValidationException> { validateDictionaryWord("KAPİ", English) }
        assertTrue(ex.message?.contains("invalid character", ignoreCase = true) == true)
        for (word in listOf("ÇİÇEK", "AĞAÇ", "KÖPRÜ", "GÜNEŞ")) {
            assertFailsWith<DictionaryValidationException>("expected '$word' to be rejected under English") {
                validateDictionaryWord(word, English)
            }
        }
    }

    @Test
    fun explicitMapsRoundTripCorrectlyForStandardAsciiCase() {
        assertEquals("live", English.lower("LIVE"))
        assertEquals("LIVE", English.upper("live"))
        assertEquals("A", English.upper("a"))
        assertEquals("z", English.lower("Z"))
    }

    @Test
    fun allSampleWordsRoundTripUnaffectedByCaseFolding() {
        // The English-equivalent of TurkishTest's "no KAPI/KAPİ-style silent corruption" check --
        // there's no known bug to reproduce here (no locale-dependent special-casing exists for
        // plain A-Z), so this just confirms upper(lower(word)) is always the identity for the
        // real dictionary's alphabet, which is what makes that absence a verified fact, not
        // an assumption. Includes a few extra words to cover less-common letters (Q, X, Z, J)
        // that the short hand-picked sample above might otherwise skip.
        val extraLetterCoverageWords = listOf("QUEEN", "EXIST", "JUICE", "SIZE", "JAZZ")
        for (word in sampleWords + extraLetterCoverageWords) {
            assertEquals(word, English.upper(English.lower(word)))
        }
    }

    @Test
    fun alphabetUpperIsExactlyTheTwentySixAsciiLetters() {
        assertEquals(('A'..'Z').toSet(), English.alphabetUpper)
    }

    @Test
    fun fillerPoolContainsOnlyEnglishAlphabetLettersNoTurkishDiacritics() {
        assertTrue(English.fillerPool.all { it in English.alphabetUpper })
        assertTrue(English.fillerPool.none { it in "ÇĞİÖŞÜ" })
    }
}
