package com.example.shiftword.game

import com.example.shiftword.model.Axis
import com.example.shiftword.model.Level
import com.example.shiftword.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GAME_DESIGN.md §9h: onboarding steps 3 (auto-played hint nudge) and 5 (hint-button callout),
 * driven through the real GameViewModel -- these prove both surfaces are gated purely by the
 * playOnboardingHintOnStart/hintButtonCalloutEligible constructor flags AppNavHost derives from
 * isOnboardingLevel, never fire when those flags are false, and (for the callout) fire at most
 * once per instance regardless of how much further no-match play follows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelOnboardingTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun levelOf(initialCells: List<String>, targetWords: List<String>, moveLimit: Int) = Level(
        id = 1,
        gridSize = initialCells.size,
        initialCells = initialCells.map { it.toList() },
        targetWords = targetWords,
        moveLimit = moveLimit,
        minMovesToSolve = 1,
        minMovesIsExact = true,
    )

    @Test
    fun playOnboardingHintOnStartAutoPlaysAHintWithoutConsumingACreditOrCallingOnHintUsed() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        var hintUsedCalls = 0
        val vm = GameViewModel(
            level,
            rng = Random(1),
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            initialHintCredits = 3,
            onHintUsed = { hintUsedCalls++ },
            playOnboardingHintOnStart = true,
        )
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.hintMove, "the onboarding hint must auto-play a real move")
        assertTrue(state.isOnboardingHint, "must be flagged as the onboarding hint, not a normal requested one")
        assertEquals(3, state.hintCreditsRemaining, "onboarding's auto-played hint must not consume a credit")
        assertEquals(0, hintUsedCalls, "onboarding's auto-played hint must not invoke onHintUsed")
    }

    @Test
    fun playOnboardingHintOnStartFalseNeverAutoPlaysAHint() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher, playOnboardingHintOnStart = false)
        testScheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.hintMove)
        assertFalse(vm.uiState.value.isOnboardingHint)
    }

    @Test
    fun isOnboardingHintClearsOnceARealMoveIsCommitted() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher, playOnboardingHintOnStart = true)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isOnboardingHint, "sanity: onboarding hint must have auto-played first")

        vm.onMove(Move(Axis.Row, 1, forward = true)) // unrelated move, no match
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isOnboardingHint)
        assertNull(vm.uiState.value.hintMove)
    }

    @Test
    fun isOnboardingHintClearsOnceARealHintIsRequested() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher, playOnboardingHintOnStart = true, initialHintCredits = 3)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isOnboardingHint, "sanity: onboarding hint must have auto-played first")

        vm.requestHint()
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isOnboardingHint, "a real hint request must not still be flagged as the onboarding one")
    }

    @Test
    fun hintButtonCalloutTriggersAfterEnoughConsecutiveNoMatchMovesWhenEligible() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 10)
        val vm = GameViewModel(
            level,
            rng = Random(1),
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            hintButtonCalloutEligible = true,
            hintButtonCalloutThresholdMoves = 3,
        )

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hintButtonCalloutVisible, "must not trigger before the threshold is reached")

        vm.onMove(Move(Axis.Row, 1, forward = true))
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hintButtonCalloutVisible)

        vm.onMove(Move(Axis.Row, 2, forward = true)) // third consecutive no-match move
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hintButtonCalloutVisible, "must trigger exactly on the move that reaches the threshold")
    }

    @Test
    fun hintButtonCalloutNeverTriggersWhenNotEligible() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 10)
        val vm = GameViewModel(
            level,
            rng = Random(1),
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            hintButtonCalloutEligible = false,
            hintButtonCalloutThresholdMoves = 2,
        )

        repeat(5) { i ->
            vm.onMove(Move(Axis.Row, i % 4, forward = true))
            testScheduler.advanceUntilIdle()
        }

        assertFalse(vm.uiState.value.hintButtonCalloutVisible, "must never trigger when hintButtonCalloutEligible is false")
    }

    @Test
    fun hintButtonCalloutOnlyEverTriggersOnceEvenAfterFurtherNoMatchMoves() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 10)
        val vm = GameViewModel(
            level,
            rng = Random(1),
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            hintButtonCalloutEligible = true,
            hintButtonCalloutThresholdMoves = 2,
        )

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()
        vm.onMove(Move(Axis.Row, 1, forward = true)) // crosses the threshold, triggers once
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hintButtonCalloutVisible, "sanity: must have triggered on the threshold-crossing move")

        vm.onMove(Move(Axis.Row, 2, forward = true)) // next move: dismissed, and must not re-trigger
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hintButtonCalloutVisible, "must be dismissed by the move after the one that triggered it")

        vm.onMove(Move(Axis.Row, 3, forward = true)) // further no-match moves must never re-trigger it
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hintButtonCalloutVisible, "must only ever trigger once per instance, not re-arm")
    }

    @Test
    fun hintButtonCalloutIsDismissedImmediatelyByARealHintRequest() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 10)
        val vm = GameViewModel(
            level,
            rng = Random(1),
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            hintButtonCalloutEligible = true,
            hintButtonCalloutThresholdMoves = 2,
            initialHintCredits = 3,
        )

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()
        vm.onMove(Move(Axis.Row, 1, forward = true))
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hintButtonCalloutVisible, "sanity: must have triggered")

        vm.requestHint()
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hintButtonCalloutVisible, "using the Hint button must dismiss its own callout immediately")
    }

    @Test
    fun matchingAMoveResetsTheNoMatchStreakSoTheCalloutDoesNotTriggerEarly() = runTest(testDispatcher) {
        // KALE sits ready to match on the first move (row 0 shifted right: ALEK -> KALE), which
        // must reset the streak back to 0 -- two more no-match moves after that must NOT be enough
        // to reach a threshold of 3.
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE", "KUTU"), moveLimit = 10)
        val vm = GameViewModel(
            level,
            rng = Random(1),
            explosionDelayMs = 0L,
            hintDispatcher = testDispatcher,
            hintButtonCalloutEligible = true,
            hintButtonCalloutThresholdMoves = 3,
        )

        vm.onMove(Move(Axis.Row, 0, forward = true)) // matches KALE -- resets streak to 0
        testScheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.movesSinceLastMatch)

        vm.onMove(Move(Axis.Row, 1, forward = true))
        testScheduler.advanceUntilIdle()
        vm.onMove(Move(Axis.Row, 2, forward = true))
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.movesSinceLastMatch)
        assertFalse(vm.uiState.value.hintButtonCalloutVisible, "only 2 no-match moves since the last match -- must not have reached the threshold of 3 yet")
    }
}
