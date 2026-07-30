package com.example.shiftword.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.shiftword.db.WordShiftDatabase
import com.example.shiftword.domain.bfsMinMovesToAnyTarget
import com.example.shiftword.domain.findMatchedWords
import com.example.shiftword.domain.generateLevel
import com.example.shiftword.model.Cell
import com.example.shiftword.model.Grid
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateLevelFromPersistedDictionaryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: WordShiftDatabase
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var levelRepository: LevelRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WordShiftDatabase.Schema.create(driver)
        database = createDatabase(driver)
        dictionaryRepository = DictionaryRepository(database)
        levelRepository = LevelRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun generateLevelProducesAValidLevelFromWordsReadOutOfThePersistedDictionary() {
        val importResult = dictionaryRepository.importWords(CURATED_DICTIONARY_SEED_WORDS)
        assertEquals(CURATED_DICTIONARY_SEED_WORDS.size, importResult.insertedCount)

        // Target words come from the DATABASE now, not the in-memory CURATED_DICTIONARY_SEED_WORDS
        // constant — proving the generator works off persisted data, not just an in-memory list.
        val fourLetterWordsFromDb = dictionaryRepository.wordsOfLength(4).toList()
        assertTrue(fourLetterWordsFromDb.size >= 3, "expected at least 3 four-letter seed words")

        val targets = fourLetterWordsFromDb.shuffled(Random(1)).take(3)
        val generated = generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = Random(1))
        assertNotNull(generated)

        val level = generated.toLevel(id = 1)
        levelRepository.insert(level)

        val reloaded = levelRepository.findById(1)
        assertNotNull(reloaded)
        assertEquals(level, reloaded)

        // Reconstruct a Grid from the round-tripped initialCells and confirm it's still a
        // legitimate, unsolved-but-solvable level: no immediate match, but a BFS solution
        // exists reachable within the same structural guarantee the generator promised.
        val reconstructedGrid = Grid(reloaded.gridSize, reloaded.initialCells.map { row -> row.map { Cell(it, -1L) } })
        assertTrue(findMatchedWords(reconstructedGrid, reloaded.targetWords.toSet()).isEmpty())
        val solution = bfsMinMovesToAnyTarget(reconstructedGrid, reloaded.targetWords.toSet())
        assertNotNull(solution)
        assertTrue(solution.minMoves <= reloaded.moveLimit)
    }
}
