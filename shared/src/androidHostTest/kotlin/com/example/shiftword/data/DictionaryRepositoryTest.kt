package com.example.shiftword.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.English
import com.example.shiftword.model.Turkish
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictionaryRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: DictionaryRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WordShiftDatabase.Schema.create(driver)
        repository = DictionaryRepository(createDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun validWordsAreInsertedAndReadableBackFromTheDatabase() {
        val result = repository.importWords(CURATED_DICTIONARY_SEED_WORDS)
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size, result.insertedCount)
        assertTrue(result.rejected.isEmpty())
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.toSet(), repository.allWords())
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size.toLong(), repository.wordCount())
    }

    @Test
    fun aWordWithAnInvalidCharacterIsRejectedBeforeItEverReachesTheDatabase() {
        // The R1 hard gate: validateDictionaryWord runs before any insertWord call, so a bad
        // word must never appear in the table — not merely "the validator says false" in
        // isolation, but confirmed by actually querying the DB afterward.
        val batch = listOf("KALE", "UMUT", "KAPIX", "SIRA")

        val result = repository.importWords(batch)

        assertEquals(3, result.insertedCount)
        assertEquals(1, result.rejected.size)
        assertEquals("KAPIX", result.rejected.single().first)

        val stored = repository.allWords()
        assertTrue("KAPIX" !in stored, "invalid word must never reach the word table")
        assertEquals(setOf("KALE", "UMUT", "SIRA"), stored)
        assertEquals(3L, repository.wordCount())
    }

    @Test
    fun realScaleImportOfTheCuratedDictionaryRejectsRealisticBadEntriesMixedIn() {
        // The curated list itself is clean (validator-gated at import time, see
        // CuratedDictionaryValidationTest) — so to actually exercise rejection at real scale rather than
        // just reporting "0 rejected," mix in entries mimicking realistic import mistakes: a
        // stray English loanword using letters outside the Turkish alphabet (Q/W/X don't
        // exist in it), and a leftover digit from a dirty source file. Both fail the alphabet
        // check specifically. A round-trip-only failure is not practically constructible from
        // valid-alphabet input by hand — the maps are a closed, self-consistent bijection over
        // exactly that alphabet, so any input that would mismatch on round-trip already fails
        // the alphabet check first. That's a property of the validator, not a gap in this test.
        val batch = CURATED_DICTIONARY_SEED_WORDS + listOf("TAXI", "SHOW", "KALE1")

        val result = repository.importWords(batch)

        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size, result.insertedCount)
        assertEquals(3, result.rejected.size)
        val rejectedWords = result.rejected.map { it.first }.toSet()
        assertEquals(setOf("TAXI", "SHOW", "KALE1"), rejectedWords)
        result.rejected.forEach { (_, reason) ->
            assertTrue(reason.contains("invalid character", ignoreCase = true), "expected an alphabet-check failure, got: $reason")
        }
        assertTrue("TAXI" !in repository.allWords())
        assertTrue("SHOW" !in repository.allWords())
        assertTrue("KALE1" !in repository.allWords())
    }

    @Test
    fun wordsOfLengthFiltersCorrectlyAfterImport() {
        repository.importWords(CURATED_DICTIONARY_SEED_WORDS)
        val fourLetter = repository.wordsOfLength(4)
        assertTrue(fourLetter.all { it.length == 4 })
        assertTrue("KALE" in fourLetter)
        assertTrue("KİTAP" !in fourLetter)
    }

    /**
     * Audit finding: AppNavHost never called anything on DictionaryRepository at all -- it read
     * word pools from the in-memory CURATED_DICTIONARY_SEED_WORDS* constants directly, meaning
     * the R1 validator gate and the `word` table it protects were never exercised by the running
     * app, only by tests. seedIfNeeded() is what AppNavHost now calls on startup; this covers its
     * two real requirements: both languages actually land in the table, keyed correctly, and
     * calling it again (e.g. every subsequent app launch) doesn't blow up on the (word, language)
     * primary key -- Word.sq's insertWord is a plain INSERT, not INSERT OR IGNORE/REPLACE.
     */
    @Test
    fun seedIfNeededImportsBothLanguagesAndIsIdempotentAcrossRepeatedCalls() {
        repository.seedIfNeeded()

        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size.toLong(), repository.wordCount(Turkish.code))
        assertEquals(CURATED_DICTIONARY_SEED_WORDS_EN.size.toLong(), repository.wordCount(English.code))
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.toSet(), repository.allWords(Turkish.code))
        assertEquals(CURATED_DICTIONARY_SEED_WORDS_EN.toSet(), repository.allWords(English.code))

        // Simulates a second app launch against the same (already-seeded) database -- must not
        // throw a primary-key-violation and must not change the counts.
        repository.seedIfNeeded()
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size.toLong(), repository.wordCount(Turkish.code))
        assertEquals(CURATED_DICTIONARY_SEED_WORDS_EN.size.toLong(), repository.wordCount(English.code))
    }

    /**
     * Closes a TESTING_GAPS.md item directly: Word.sq's (word, language) composite key was only
     * incidentally exercised by seedIfNeeded's real-dictionary test above, which doesn't prove
     * anything about an actual same-spelling collision (the real TR/EN dictionaries may or may
     * not happen to share any exact spellings). "PART" uses only letters common to both
     * alphabets (no Q/W/X, which Turkish lacks), so a genuine collision is possible in principle
     * -- this constructs one directly instead of relying on whether it happens to occur in real
     * data, to prove the composite key actually handles it, and that language-scoped queries
     * never leak a word exclusive to the other language.
     */
    @Test
    fun identicallySpelledWordsInBothLanguagesCoexistWithoutCollisionOrCrossContamination() {
        val turkishWords = listOf("PART", "KALE")
        val englishWords = listOf("PART", "LIVE")

        repository.importWords(turkishWords, Turkish.code)
        repository.importWords(englishWords, English.code)

        assertEquals(2L, repository.wordCount(Turkish.code))
        assertEquals(2L, repository.wordCount(English.code))
        assertEquals(setOf("PART", "KALE"), repository.allWords(Turkish.code))
        assertEquals(setOf("PART", "LIVE"), repository.allWords(English.code))

        // Cross-contamination check: a language-scoped query must never return the other
        // language's exclusive word, even though "PART" itself is legitimately present in both.
        assertTrue("LIVE" !in repository.allWords(Turkish.code))
        assertTrue("KALE" !in repository.allWords(English.code))
        assertEquals(setOf("PART", "KALE"), repository.wordsOfLength(4, Turkish.code))
        assertEquals(setOf("PART", "LIVE"), repository.wordsOfLength(4, English.code))
    }
}
