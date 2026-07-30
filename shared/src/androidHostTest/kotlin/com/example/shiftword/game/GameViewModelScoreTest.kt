package com.example.shiftword.game

import androidx.lifecycle.viewModelScope
import com.example.shiftword.model.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Feature 2 (GAME_DESIGN.md §9b): score is independent of StarRating -- this drives a real
 * GameViewModel through a real playthrough (production shift -> match -> cascade -> commit path,
 * via debugForceCompleteWord, same production resolveCascade call as a real move) and checks the
 * resulting foundAtMoveCount / totalScore, rather than testing pointsForWord/scoreForLevel in
 * isolation (see ScoreTest for the pure-function coverage).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelScoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun levelWithThreeFourLetterTargets() = Level(
        id = 1,
        gridSize = 4,
        initialCells = List(4) { List(4) { 'X' } },
        targetWords = listOf("KALE", "MASA", "TUZL"),
        moveLimit = 12,
        minMovesToSolve = 3,
        minMovesIsExact = true,
    )

    @Test
    fun scoreAccumulatesAcrossAllThreeWordsAttributedToEachOnesOwnCompletionMoveCount() = runTest(testDispatcher) {
        val level = levelWithThreeFourLetterTargets()
        val vm = GameViewModel(level, explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        repeat(3) {
            vm.debugForceCompleteWord()
            testScheduler.advanceUntilIdle()
        }

        val state = vm.uiState.value
        assertTrue(state.isWon, "expected all 3 target words to be completed")
        assertEquals(setOf("KALE", "MASA", "TUZL"), state.foundAtMoveCount.keys, "every found word must be attributed a completion move count")

        val expectedScore = state.foundAtMoveCount.values.sumOf { pointsForWord(it, level.moveLimit) }
        assertEquals(expectedScore, state.totalScore, "totalScore must equal the sum of pointsForWord over foundAtMoveCount")
        assertTrue(state.totalScore > 0, "fast force-completions well under the move limit should score positive points")

        vm.viewModelScope.cancel()
    }

    @Test
    fun starRatingIsUnaffectedByScoreTracking() = runTest(testDispatcher) {
        // StarRating.kt/EfficiencyFeedback.kt were deliberately left untouched by this feature --
        // confirm starsFor still computes purely from moveCount/minMovesToSolve/moveLimit,
        // independent of the new foundAtMoveCount/totalScore fields.
        val level = levelWithThreeFourLetterTargets()
        val vm = GameViewModel(level, explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        repeat(3) {
            vm.debugForceCompleteWord()
            testScheduler.advanceUntilIdle()
        }

        val state = vm.uiState.value
        assertTrue(state.isWon)
        val stars = starsFor(state.moveCount, state.minMovesToSolve, state.moveLimit)
        assertTrue(stars in 1..3)

        vm.viewModelScope.cancel()
    }
}
