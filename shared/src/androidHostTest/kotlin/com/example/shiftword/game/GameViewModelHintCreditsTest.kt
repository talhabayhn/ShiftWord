package com.example.shiftword.game

import androidx.lifecycle.viewModelScope
import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import com.example.shiftword.domain.DEFAULT_FILLER_POOL
import com.example.shiftword.domain.generateLevel
import com.example.shiftword.model.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * Feature 3 (GAME_DESIGN.md §9c): requestHint's credit gating, driven through the real
 * GameViewModel (not a mock) -- confirms a credit is consumed exactly once per accepted request,
 * onHintUsed (the persistence hook AppNavHost wires to SettingsRepository.consumeHintCredit) is
 * invoked in lockstep, and further requests are inert once the pool hits zero rather than going
 * negative or double-invoking onHintUsed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelHintCreditsTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun buildLevel(seed: Int): Level {
        val rng = Random(seed)
        val pool4 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val targets = pool4.shuffled(rng).take(3)
        val generated = checkNotNull(generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng, fillerPool = DEFAULT_FILLER_POOL))
        return generated.toLevel(id = seed)
    }

    @Test
    fun requestHintConsumesOneGlobalCreditPerAcceptedRequestAndBlocksAtZero() = runTest(testDispatcher, timeout = 1.minutes) {
        val level = buildLevel(seed = 42)
        var hintUsedCalls = 0
        val vm = GameViewModel(
            level,
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            initialHintCredits = 2,
            onHintUsed = { hintUsedCalls++ },
        )

        vm.requestHint()
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.hintCreditsRemaining)
        assertEquals(1, hintUsedCalls)

        vm.requestHint()
        testScheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.hintCreditsRemaining)
        assertEquals(2, hintUsedCalls)

        // Third request: no credits left -- must be a no-op, not decrement below zero or fire
        // onHintUsed again.
        vm.requestHint()
        testScheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.hintCreditsRemaining)
        assertEquals(2, hintUsedCalls, "requestHint must not consume a credit or invoke onHintUsed once the pool is exhausted")

        vm.viewModelScope.cancel()
    }

    @Test
    fun creditsDoNotResetAcrossGameViewModelInstancesMatchingTheGlobalPoolContract() = runTest(testDispatcher, timeout = 1.minutes) {
        // Simulates the real AppNavHost wiring: a new GameViewModel per level/replay attempt is
        // constructed with whatever initialHintCredits the caller currently has persisted --
        // GameViewModel itself has no notion of "reset on new level," proving the pool being
        // global (not per-level) is a property of how the caller threads the value through, which
        // this test exercises end-to-end at the GameViewModel boundary.
        var persistedCredits = 3
        val levelOne = buildLevel(seed = 7)
        val vmOne = GameViewModel(
            levelOne,
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            initialHintCredits = persistedCredits,
            onHintUsed = { persistedCredits-- },
        )
        vmOne.requestHint()
        testScheduler.advanceUntilIdle()
        assertEquals(2, persistedCredits)
        vmOne.viewModelScope.cancel()

        val levelTwo = buildLevel(seed = 8)
        val vmTwo = GameViewModel(
            levelTwo,
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            initialHintCredits = persistedCredits, // NOT reset back to 3 for the new level
            onHintUsed = { persistedCredits-- },
        )
        assertEquals(2, vmTwo.uiState.value.hintCreditsRemaining)
        vmTwo.viewModelScope.cancel()
    }
}
