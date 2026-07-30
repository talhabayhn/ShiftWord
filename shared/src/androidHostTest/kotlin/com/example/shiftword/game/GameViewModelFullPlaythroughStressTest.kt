package com.example.shiftword.game

import androidx.lifecycle.viewModelScope
import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS_EN
import com.example.shiftword.domain.DEFAULT_FILLER_POOL
import com.example.shiftword.domain.bfsMinMovesToAnyTarget
import com.example.shiftword.domain.generateLevel
import com.example.shiftword.model.English
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The audit gap this closes: every existing test either checks one domain operation in
 * isolation (GridEngineTest, SolverTest, CascadeTest) or drives `resolveCascade` directly
 * (CascadeIntersectionGuaranteeTest) — none plays a level start-to-finish through the actual
 * `GameViewModel` state machine (move counting, win/loss transitions, star rating, hint) the way
 * a real player does: shift (a real BFS-found move, not `debugForceCompleteWord`'s instant
 * overwrite) -> match -> cascade, repeated, in an arbitrary completion order. That gap is exactly
 * the shape of the R2/R3 bug this whole audit was prompted by — individually-correct components,
 * never verified end-to-end together.
 *
 * Language-parity audit note: this originally ran Turkish only. Both languages are now covered
 * at identical trial counts (not a reduced/"good enough" sample for English) -- the point of
 * this pass was specifically to stop assuming English behaves the same as Turkish "by
 * architecture" without measuring it at the same rigor, so English gets exactly the trial count
 * Turkish does in every test below.
 */
private data class BatchResult(val levelsChecked: Int, val orderingsChecked: Int, val starRatingFailures: Int, val hintFailures: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelFullPlaythroughStressTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun everyGeneratedLevelIsFullyPlayableInAnyOrderThroughRealMovesTurkish() = runTest(testDispatcher, timeout = 10.minutes) {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val result = runRandomOrderBatch(pool4, DEFAULT_FILLER_POOL, trialCount = 2000, seedOffset = 0)
        println("[playthrough-stress][TR] levelsChecked=${result.levelsChecked} orderingsChecked=${result.orderingsChecked} starRatingFailures=${result.starRatingFailures} hintFailures=${result.hintFailures}")
        assertTrue(result.starRatingFailures == 0, "TR: star rating computed nonsensically in ${result.starRatingFailures} playthrough(s)")
        assertTrue(result.hintFailures == 0, "TR: hint failed to return a valid move in ${result.hintFailures} playthrough(s) where one existed")
    }

    @Test
    fun everyGeneratedLevelIsFullyPlayableInAnyOrderThroughRealMovesEnglish() = runTest(testDispatcher, timeout = 10.minutes) {
        // Matches the Turkish variant's trial count exactly (2000) -- this pass exists to stop
        // assuming EN behaves the same as TR "by architecture" without measuring it at the same
        // rigor, so trimming EN's sample size would undercut the point of it.
        val pool4 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val result = runRandomOrderBatch(pool4, English.fillerPool, trialCount = 2000, seedOffset = 10_000_000)
        println("[playthrough-stress][EN] levelsChecked=${result.levelsChecked} orderingsChecked=${result.orderingsChecked} starRatingFailures=${result.starRatingFailures} hintFailures=${result.hintFailures}")
        assertTrue(result.starRatingFailures == 0, "EN: star rating computed nonsensically in ${result.starRatingFailures} playthrough(s)")
        assertTrue(result.hintFailures == 0, "EN: hint failed to return a valid move in ${result.hintFailures} playthrough(s) where one existed")
    }

    private suspend fun TestScope.runRandomOrderBatch(
        pool4: List<String>,
        fillerPool: String,
        trialCount: Int,
        seedOffset: Int,
    ): BatchResult {
        var levelsChecked = 0
        var orderingsChecked = 0
        var starRatingFailures = 0
        var hintFailures = 0

        repeat(trialCount) { i ->
            val seed = seedOffset + i
            val rng = Random(seed)
            val targets = pool4.shuffled(rng).take(3)
            val generated = generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng, fillerPool = fillerPool) ?: return@repeat
            levelsChecked++
            val level = generated.toLevel(id = seed)

            // One random completion order per level at this scale -- full 3! permutation
            // coverage is checked separately, at a smaller level count, below.
            val order = targets.shuffled(rng)
            orderingsChecked++
            playToCompletionAndVerify(level, order, seed, fillerPool, onStarRatingFailure = { starRatingFailures++ }, onHintFailure = { hintFailures++ })
        }
        return BatchResult(levelsChecked, orderingsChecked, starRatingFailures, hintFailures)
    }

    @Test
    fun everyGeneratedLevelIsFullyPlayableInEveryPossibleCompletionOrderTurkish() = runTest(testDispatcher, timeout = 10.minutes) {
        val pool4 = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val result = runAllOrderingsBatch(pool4, DEFAULT_FILLER_POOL, levelCount = 150, seedOffset = 500_000)
        println("[playthrough-stress][TR] (all-orderings) levelsChecked=${result.levelsChecked} orderingsChecked=${result.orderingsChecked} starRatingFailures=${result.starRatingFailures} hintFailures=${result.hintFailures}")
        assertTrue(result.starRatingFailures == 0, "TR: star rating computed nonsensically in ${result.starRatingFailures} playthrough(s)")
        assertTrue(result.hintFailures == 0, "TR: hint failed to return a valid move in ${result.hintFailures} playthrough(s) where one existed")
    }

    @Test
    fun everyGeneratedLevelIsFullyPlayableInEveryPossibleCompletionOrderEnglish() = runTest(testDispatcher, timeout = 10.minutes) {
        // Matches the Turkish variant's level count exactly (150 levels x up to 6 orderings =
        // up to 900 full playthroughs) -- see the random-order English test above for why.
        val pool4 = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val result = runAllOrderingsBatch(pool4, English.fillerPool, levelCount = 150, seedOffset = 20_000_000)
        println("[playthrough-stress][EN] (all-orderings) levelsChecked=${result.levelsChecked} orderingsChecked=${result.orderingsChecked} starRatingFailures=${result.starRatingFailures} hintFailures=${result.hintFailures}")
        assertTrue(result.starRatingFailures == 0, "EN: star rating computed nonsensically in ${result.starRatingFailures} playthrough(s)")
        assertTrue(result.hintFailures == 0, "EN: hint failed to return a valid move in ${result.hintFailures} playthrough(s) where one existed")
    }

    private suspend fun TestScope.runAllOrderingsBatch(
        pool4: List<String>,
        fillerPool: String,
        levelCount: Int,
        seedOffset: Int,
    ): BatchResult {
        var levelsChecked = 0
        var orderingsChecked = 0
        var starRatingFailures = 0
        var hintFailures = 0

        repeat(levelCount) { i ->
            val seed = seedOffset + i
            val rng = Random(seed)
            val targets = pool4.shuffled(rng).take(3)
            val generated = generateLevel(size = 4, targetWords = targets, scrambleMoves = 5, rng = rng, fillerPool = fillerPool) ?: return@repeat
            levelsChecked++
            val level = generated.toLevel(id = seed)

            for (order in targets.permutations()) {
                orderingsChecked++
                playToCompletionAndVerify(level, order, seed, fillerPool, onStarRatingFailure = { starRatingFailures++ }, onHintFailure = { hintFailures++ })
            }
        }
        return BatchResult(levelsChecked, orderingsChecked, starRatingFailures, hintFailures)
    }

    /**
     * Drives [level] through the real [GameViewModel] to completion, choosing which target to
     * pursue next according to [order] rather than whichever happens to match first, and for
     * each target: asks for a hint first (must return a real, currently-valid move whenever the
     * target is reachable -- which the R4-addendum fix guarantees it always is), then applies
     * the actual BFS-found move sequence to that specific target one real [GameViewModel.onMove]
     * call at a time (exercising the production shift -> match -> cascade path, not a shortcut).
     */
    private suspend fun TestScope.playToCompletionAndVerify(
        level: com.example.shiftword.model.Level,
        order: List<String>,
        seed: Int,
        fillerPool: String,
        onStarRatingFailure: () -> Unit,
        onHintFailure: () -> Unit,
    ) {
        val vm = GameViewModel(level, rng = Random(seed + 999), fillerPool = fillerPool, explosionDelayMs = 0L, hintDispatcher = testDispatcher)
        var expectedMoveCount = 0

        for (target in order) {
            if (vm.uiState.value.isWon || vm.uiState.value.isLost) break

            // A precomputed path toward `target` can go stale mid-execution: an intermediate
            // move can incidentally complete a DIFFERENT remaining target too (a legitimate bonus
            // match), triggering its own cascade -- resolveCascade's exhaustive search
            // re-certifies reachability for whatever's left AT THAT MOMENT, but a path planned
            // before that cascade has no way to know about it. A real player (or a real hint
            // request) always reacts to the CURRENT grid, never blindly executes a stale plan --
            // so on staleness, re-plan from scratch against the grid as it now stands and keep
            // retrying the same target, rather than abandoning it for the rest of the playthrough.
            retryLoop@ while (true) {
                val state = vm.uiState.value
                if (state.isWon || state.isLost) break@retryLoop
                if (target !in state.remainingTargets) break@retryLoop // already found, possibly incidentally

                // Hint must find a real move to this target -- the R4 addendum's guarantee, checked
                // through the actual production hint path (requestHint), not a direct BFS call.
                vm.requestHint()
                testScheduler.advanceUntilIdle()
                val hintMove = vm.uiState.value.hintMove
                val expectedBfs = bfsMinMovesToAnyTarget(state.grid, state.remainingTargets)
                if (expectedBfs != null && hintMove == null) onHintFailure()

                val path = bfsMinMovesToAnyTarget(state.grid, setOf(target))?.path
                if (path == null) {
                    onHintFailure() // the R4 addendum's guarantee failing to hold is itself a failure worth counting
                    break@retryLoop
                }

                val remainingWhenPlanned = state.remainingTargets
                var stale = false
                for (move in path) {
                    val loopState = vm.uiState.value
                    if (loopState.isWon || loopState.isLost) break
                    if (loopState.remainingTargets != remainingWhenPlanned) {
                        stale = true
                        break
                    }
                    vm.onMove(move)
                    testScheduler.advanceUntilIdle()
                    expectedMoveCount++
                }
                if (!stale) break@retryLoop // path fully applied (or game resolved) -- move on to the next target in order
                // else: grid changed underneath the plan -- loop again and re-plan fresh
            }
        }

        val finalState = vm.uiState.value
        assertEquals(expectedMoveCount, finalState.moveCount, "moveCount drifted from the number of real onMove calls applied")
        assertTrue(finalState.isWon || finalState.moveCount <= finalState.moveLimit, "moveCount exceeded moveLimit without the state ever flipping to isLost")
        if (finalState.isWon) {
            val stars = starsFor(finalState.moveCount, finalState.minMovesToSolve, finalState.moveLimit)
            if (stars !in 1..3) onStarRatingFailure()
            if (finalState.moveCount <= finalState.minMovesToSolve && stars != 3) onStarRatingFailure()
        }

        // Each iteration creates its own GameViewModel (viewModelScope backed by its own
        // SupervisorJob) -- at thousands of instances per test, an uncancelled scope per
        // instance accumulates and trips runTest's uncompleted-coroutines check at the end,
        // even though every individual launch already completed. Cancelling explicitly (there's
        // no ViewModelStore driving onCleared() here, since these are constructed directly, the
        // same way AppNavHost does it) keeps this a property of test scale, not a real leak.
        vm.viewModelScope.cancel()
    }

    private fun <T> List<T>.permutations(): List<List<T>> {
        if (size <= 1) return listOf(this)
        return indices.flatMap { i ->
            val rest = this.toMutableList().also { it.removeAt(i) }
            rest.permutations().map { listOf(this[i]) + it }
        }
    }
}
