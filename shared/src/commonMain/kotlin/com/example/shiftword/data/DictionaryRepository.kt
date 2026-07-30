package com.example.shiftword.data

import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.model.DictionaryValidationException
import com.example.shiftword.model.English
import com.example.shiftword.model.LanguageProfiles
import com.example.shiftword.model.Turkish
import com.example.shiftword.model.validateDictionaryWord

data class DictionaryImportResult(
    val insertedCount: Int,
    val rejected: List<Pair<String, String>>,
)

/**
 * Every word must pass [validateDictionaryWord] before it is inserted — no word reaches the
 * `word` table without clearing the alphabet + round-trip checks first. See
 * ALGORITHM_VALIDATION.md Risk R1. A rejected word is skipped and reported, not fatal to the
 * rest of the batch: a large real dictionary import shouldn't abort entirely over one bad row.
 *
 * [language] defaults to Turkish's code so existing call sites are unaffected by Phase 9's
 * English support; the `word` table's primary key is `(word, language)` so both dictionaries
 * coexist without collision (see Word.sq).
 */
class DictionaryRepository(private val database: WordShiftDatabase) {

    fun importWords(words: List<String>, language: String = Turkish.code): DictionaryImportResult {
        val profile = LanguageProfiles.forCode(language)
        var inserted = 0
        val rejected = mutableListOf<Pair<String, String>>()
        database.transaction {
            for (word in words) {
                try {
                    validateDictionaryWord(word, profile)
                } catch (e: DictionaryValidationException) {
                    rejected.add(word to (e.message ?: "invalid"))
                    continue
                }
                database.wordQueries.insertWord(word, word.length.toLong(), language)
                inserted++
            }
        }
        return DictionaryImportResult(inserted, rejected)
    }

    fun allWords(language: String = Turkish.code): Set<String> =
        database.wordQueries.selectAll(language).executeAsList().map { it.word }.toSet()

    fun wordsOfLength(length: Int, language: String = Turkish.code): Set<String> =
        database.wordQueries.selectByLength(length.toLong(), language).executeAsList().map { it.word }.toSet()

    fun wordCount(language: String = Turkish.code): Long = database.wordQueries.countAll(language).executeAsOne()

    fun clearAll() = database.wordQueries.clearAll()

    /**
     * Seeds both curated word lists into the `word` table on first launch, and is a no-op on
     * every launch after that. Was previously never called anywhere in the running app — see
     * ARCHITECTURE.md's dictionary-seeding note and the audit finding that motivated this: the
     * R1 validator gate and the `word` table it protects were entirely decorative until a real
     * production call site called [importWords]. Idempotent by checking [wordCount] first,
     * since [importWords] uses a plain `INSERT` (Word.sq) that would otherwise violate the
     * `(word, language)` primary key on a second launch.
     */
    fun seedIfNeeded() {
        if (wordCount(Turkish.code) == 0L) importWords(CURATED_DICTIONARY_SEED_WORDS, Turkish.code)
        if (wordCount(English.code) == 0L) importWords(CURATED_DICTIONARY_SEED_WORDS_EN, English.code)
    }
}
