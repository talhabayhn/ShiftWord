package com.example.shiftword.game

import androidx.lifecycle.viewModelScope
import com.example.shiftword.domain.bfsMinMovesToAnyTarget
import com.example.shiftword.model.Axis
import com.example.shiftword.model.Level
import com.example.shiftword.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    // viewModelScope launches on Dispatchers.Main.immediate; tests must install a test
    // dispatcher there and drive that SAME scheduler, or advanceUntilIdle() controls nothing.
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
    fun initialStateReflectsTheLevelBeforeAnyMove() {
        val level = levelOf(listOf("ALEK", "UPPP", "TPPP", "UPPP"), listOf("KALE", "KUTU"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L)
        val state = vm.uiState.value
        assertEquals(0, state.moveCount)
        assertFalse(state.isWon)
        assertFalse(state.isLost)
        assertEquals(setOf("KALE", "KUTU"), state.remainingTargets)
        assertEquals(listOf("ALEK", "UPPP", "TPPP", "UPPP"), state.grid.rowsAsStrings())
    }

    @Test
    fun aMoveWithNoMatchJustIncrementsMoveCount() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 3)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L)
        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.moveCount)
        assertTrue(state.foundWords.isEmpty())
        assertFalse(state.isWon)
        assertFalse(state.isLost)
    }

    @Test
    fun aMoveThatCompletesBothATargetRowAndColumnFindsBothWordsInOneMove() = runTest(testDispatcher) {
        // Same deterministic construction as CascadeTest's Phase-1/2 scenario: one row shift
        // completes "KALE" (row) and "KUTU" (col) simultaneously.
        val level = levelOf(listOf("ALEK", "UPPP", "TPPP", "UPPP"), listOf("KALE", "KUTU"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(setOf("KALE", "KUTU"), state.foundWords)
        assertTrue(state.isWon)
        assertEquals(1, state.moveCount)
        assertTrue(state.explodingCellIds.isEmpty(), "exploding-cell marker should be cleared once cascade resolves")
    }

    @Test
    fun explodingCellIdsArePopulatedDuringTheDelayThenClearedAfterCascadeResolves() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "UPPP", "TPPP", "UPPP"), listOf("KALE", "KUTU"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 1000L, hintDispatcher = testDispatcher)

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceTimeBy(1L) // let the coroutine reach the post-match, pre-delay state
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.explodingCellIds.isNotEmpty(), "expected exploding cells to be marked before the delay elapses")
        assertFalse(vm.uiState.value.isWon, "cascade must not resolve until after the explosion delay")

        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isWon)
        assertTrue(vm.uiState.value.explodingCellIds.isEmpty())
    }

    /**
     * P3 real-device playtesting finding: once resolveCascade() moved from the always-single-
     * threaded Main.immediate dispatcher onto hintDispatcher (a real, potentially multi-threaded
     * pool in production -- see processShiftedGrid's doc comment), a second onMove()/
     * debugForceCompleteWord() call landing while an earlier one's cascade was still resolving
     * stopped being merely confusing and became a genuine data race: both calls' resolveCascade
     * invocations would mutate the shared nextCellId var and pull from the shared rng from two
     * threads at once. Neither GameViewModel nor GridBoard's drag gesture previously gated on
     * explodingCellIds the way the Hint button already did, so this was reachable. Fixed via
     * isBusy(), checked at the top of every entry point. This test proves a move attempted while
     * a previous one is still exploding is silently ignored (state unchanged), not just that the
     * race can't be observed -- the ignored move's grid.apply() must never even run.
     */
    @Test
    fun aSecondMoveWhileAPreviousCascadeIsStillExplodingIsIgnored() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "UPPP", "TPPP", "UPPP"), listOf("KALE", "KUTU"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 1000L, hintDispatcher = testDispatcher)

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()
        val explodingState = vm.uiState.value
        assertTrue(explodingState.explodingCellIds.isNotEmpty(), "sanity: must still be exploding at this point")

        vm.onMove(Move(Axis.Col, 3, forward = true)) // attempted while busy -- must be a no-op
        testScheduler.runCurrent()
        assertEquals(explodingState, vm.uiState.value, "a move attempted while a previous cascade is still exploding must be silently ignored")

        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isWon, "the original (only) move's cascade must still resolve normally")
    }

    @Test
    fun moveLimitExhaustedWithoutAllTargetsFoundIsALoss() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 2)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L)

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()
        vm.onMove(Move(Axis.Row, 1, forward = true))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.moveCount)
        assertFalse(state.isWon)
        assertTrue(state.isLost)
    }

    @Test
    fun movesAreIgnoredOnceTheGameIsWonOrLost() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "UPPP", "TPPP", "UPPP"), listOf("KALE", "KUTU"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isWon)

        val wonState = vm.uiState.value
        vm.onMove(Move(Axis.Row, 1, forward = true))
        testScheduler.advanceUntilIdle()
        assertEquals(wonState, vm.uiState.value)
    }

    @Test
    fun winTakesPriorityWhenTheFinalAllowedMoveBothCompletesAndExhaustsTheLimit() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "UPPP", "TPPP", "UPPP"), listOf("KALE", "KUTU"), moveLimit = 1)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.moveCount)
        assertTrue(state.isWon, "winning on the last available move must count as a win, not a loss")
        assertFalse(state.isLost)
    }

    @Test
    fun requestHintReturnsTheSameMoveTheExistingBfsSolverWouldReturnDirectly() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        val expected = bfsMinMovesToAnyTarget(vm.uiState.value.grid, vm.uiState.value.remainingTargets)
        checkNotNull(expected)

        vm.requestHint()
        testScheduler.advanceUntilIdle()

        assertEquals(expected.path.first(), vm.uiState.value.hintMove)
    }

    /**
     * Real-device manual playtesting report: tapping Hint repeatedly returned the same
     * suggestion ("col2 backward") even after applying it, and the repeated suggestion no
     * longer corresponded to a valid move on the actual grid. ALGORITHM_VALIDATION.md's R4
     * addendum concluded this was a test-harness bug, not a production one, because
     * requestHint re-derives its BFS path against the CURRENT grid on every call rather than
     * reusing a stored plan (see requestHint's own doc comment). This test independently
     * verifies that claim end-to-end through the real GameViewModel, not just by reading the
     * source: request a hint, apply it, request again, and confirm the second suggestion is a
     * genuinely different, freshly-recomputed move rather than a stale repeat of the first.
     */
    @Test
    fun secondHintAfterApplyingTheFirstIsFreshNotAStaleRepeat() = runTest(testDispatcher) {
        val level = levelOf(
            listOf("ALEK", "PEKI", "PPPP", "PPPP"),
            listOf("KALE", "IPEK"),
            moveLimit = 5,
        )
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        vm.requestHint()
        testScheduler.advanceUntilIdle()
        val hint1 = vm.uiState.value.hintMove
        checkNotNull(hint1) { "expected a hint move before any target is found" }

        vm.onMove(hint1)
        testScheduler.advanceUntilIdle()
        val afterFirstMove = vm.uiState.value
        assertFalse(afterFirstMove.isWon, "only one of the two targets should be found by the first hint move")
        assertEquals(1, afterFirstMove.foundWords.size)

        vm.requestHint()
        testScheduler.advanceUntilIdle()
        val hint2 = vm.uiState.value.hintMove
        checkNotNull(hint2) { "expected a fresh hint move for the remaining target" }

        assertFalse(
            hint1 == hint2,
            "second hint repeated the first suggestion verbatim after it was already applied -- exactly the stale-hint bug reported from manual playtesting",
        )

        val freshExpected = bfsMinMovesToAnyTarget(afterFirstMove.grid, afterFirstMove.remainingTargets)
        checkNotNull(freshExpected)
        assertEquals(
            freshExpected.path.first(),
            hint2,
            "the second hint must match a BFS path recomputed from the CURRENT grid, not a cached/stale path",
        )
    }

    /**
     * Re-opened priority-1 finding, more specific than the test above: real-device playtesting
     * reported a stale/unhelpful repeated hint specifically when hint usage was interleaved with
     * manual (non-hint) play. The bug: `requestHint()` captured the grid synchronously but the
     * BFS computation ran on a background dispatcher taking real wall-clock time -- nothing
     * stopped the player from making a manual move in that window, and the eventual result was
     * written back unconditionally, describing a grid that was no longer current. The test above
     * couldn't catch this because it always `advanceUntilIdle()`s between actions, serializing
     * everything -- this test uses a genuinely SEPARATE scheduler for the hint dispatcher so the
     * BFS computation can be suspended mid-flight while a real move is applied and committed on
     * the main dispatcher, then resumed afterward, reproducing the exact interleaving a slow
     * hint racing real gameplay would hit on-device.
     */
    @Test
    fun aHintStillComputingWhenAManualMoveLandsIsDroppedNotShownStale() = runTest(testDispatcher) {
        val hintScheduler = TestCoroutineScheduler()
        val hintOnlyDispatcher = StandardTestDispatcher(hintScheduler)
        val level = levelOf(listOf("ALEK", "PEKI", "PPPP", "PPPP"), listOf("KALE", "IPEK"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = hintOnlyDispatcher)

        vm.requestHint() // captures the grid, then suspends dispatching onto hintOnlyDispatcher
        testScheduler.runCurrent() // let the launch{} body run up to (not past) that dispatch boundary

        val gridBeforeMove = vm.uiState.value.grid
        vm.onMove(Move(Axis.Row, 1, forward = true)) // unrelated real move, not hint-guided
        testScheduler.advanceUntilIdle() // fully commit it on the main dispatcher
        val gridAfterMove = vm.uiState.value.grid
        assertTrue(gridBeforeMove !== gridAfterMove, "sanity: the manual move must have actually produced a new grid")
        assertNull(vm.uiState.value.hintMove, "sanity: committing the move already cleared any pending hint")

        // Now let the stale hint's BFS computation -- captured against gridBeforeMove -- finally
        // resolve, exactly like a slow/backgrounded BFS call finishing late after the player
        // already moved on.
        hintScheduler.advanceUntilIdle()
        testScheduler.advanceUntilIdle()

        assertNull(
            vm.uiState.value.hintMove,
            "a hint computed against a grid that's no longer current must never be shown, even if it finishes after the player already moved",
        )
    }

    @Test
    fun hintMoveIsClearedOnceAMoveIsCommitted() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = testDispatcher)

        vm.requestHint()
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.hintMove)

        vm.onMove(Move(Axis.Row, 1, forward = true)) // unrelated move, no match
        testScheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.hintMove)
    }

    /**
     * Priority-2 real-device report: "Tekrar Oyna" (Replay) appears to do nothing. AppNavHost's
     * onReplaySameLevel is just `attempt++`, which forces `remember(currentLevel, attempt)` to
     * build a brand-new GameViewModel from the SAME Level — this test verifies that mechanism
     * actually produces a fully fresh, playable state independent of whatever the previous
     * GameViewModel instance for that same level had already done (lost, moved, found words),
     * which is the part of "replay" actually testable outside Compose/Android. It passes cleanly,
     * which points at the priority-1 cascade/intersection bug as the real explanation for the
     * report: replaying an already-impossible level (this test's CascadeIntersectionGuaranteeTest
     * sibling) just looks like "nothing happens" because the puzzle was never solvable to begin
     * with, not because replay itself was broken.
     */
    @Test
    fun replayingViaANewViewModelForTheSameLevelStartsFullyFresh() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 2)
        val first = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L)
        first.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()
        first.onMove(Move(Axis.Row, 1, forward = true))
        testScheduler.advanceUntilIdle()
        assertTrue(first.uiState.value.isLost, "sanity check: first playthrough should have run out of moves")

        // Mirrors AppNavHost: onReplaySameLevel just builds a new GameViewModel(level = currentLevel, ...).
        val replay = GameViewModel(level, rng = Random(2), explosionDelayMs = 0L)
        val state = replay.uiState.value
        assertEquals(0, state.moveCount)
        assertFalse(state.isWon)
        assertFalse(state.isLost)
        assertTrue(state.foundWords.isEmpty())
        assertEquals(setOf("KALE"), state.remainingTargets)
        assertEquals(level.initialCells, state.grid.letterKey())
    }

    /**
     * Cross-subsystem audit finding (navigation back-stack x in-flight cascade animation):
     * AppNavHost builds each GameViewModel via a plain `remember(currentLevel, attempt) {
     * GameViewModel(...) }` constructor call, NOT through a ViewModelStoreOwner/factory (e.g. the
     * Compose Multiplatform `viewModel()` helper) that would call onCleared()/cancel its
     * viewModelScope automatically when the composable leaves composition (e.g. the player taps
     * "← Menü" mid-explosion-delay). This test demonstrates the underlying mechanism: nothing
     * currently cancels a GameViewModel's coroutine scope when its owning screen goes away, so an
     * in-flight cascade (including onLevelCompleted, which writes progress/level-completion to
     * the database) keeps running and completes anyway. The second half shows that explicit
     * cancellation (what a proper disposal hook, e.g. `DisposableEffect(viewModel) { onDispose {
     * viewModel.viewModelScope.cancel() } }` in AppNavHost, would provide) DOES stop it -- so this
     * is a real, fixable gap, not a fundamental limitation. Whether "still counts as completed
     * after navigating away" is actually the desired product behavior is a separate decision this
     * test does not make.
     */
    @Test
    fun aPendingCascadeStillCallsOnLevelCompletedIfNothingCancelsTheViewModelScope() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        var completedCalls = 0
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 1000L, hintDispatcher = testDispatcher, onLevelCompleted = { _, _ -> completedCalls++ })

        vm.onMove(Move(Axis.Row, 0, forward = true)) // matches KALE, enters the explosion delay
        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isWon, "cascade must still be pending at this point")

        // No navigation happened here (nothing disposed this GameViewModel / cancelled its
        // scope) -- simulates the player having already left the screen without any cleanup
        // hook existing to catch that. The pending cascade completes regardless.
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isWon)
        assertEquals(1, completedCalls, "onLevelCompleted fired even though nothing was still observing this ViewModel")
    }

    @Test
    fun cancellingTheViewModelScopeDoesStopAPendingCascadeFromCompleting() = runTest(testDispatcher) {
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        var completedCalls = 0
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 1000L, hintDispatcher = testDispatcher, onLevelCompleted = { _, _ -> completedCalls++ })

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isWon)

        // What a real disposal hook (DisposableEffect's onDispose, if AppNavHost had one) would
        // do on navigation-away -- proves cancellation is an effective, available fix, not that
        // the underlying coroutine is somehow uncancellable.
        vm.viewModelScope.cancel()
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isWon, "cancelling the scope must stop the pending cascade from ever completing")
        assertEquals(0, completedCalls)
    }

    @Test
    fun debugForceCompleteWordDrivesTheRealExplosionAndCascadePath() = runTest(testDispatcher) {
        val level = levelOf(listOf("PPPP", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 1000L, hintDispatcher = testDispatcher)

        vm.debugForceCompleteWord()
        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.explodingCellIds.isNotEmpty(), "force-complete must go through the same exploding-cell state as a real match")
        assertFalse(vm.uiState.value.isWon, "cascade must not resolve until after the explosion delay, same as a real move")

        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isWon)
        assertEquals(1, vm.uiState.value.moveCount)
    }

    /**
     * P3 audit finding, third instance of the same pattern hint's BFS and cascade's reachability
     * search already hit: onLevelCompleted (a caller-supplied callback -- AppNavHost's real
     * implementation does two synchronous SQLDelight writes) was invoked inline in commit(), i.e.
     * on whatever dispatcher processShiftedGrid's caller used (Main.immediate by default). Fixed
     * the same way: run it on hintDispatcher instead. This test uses a genuinely SEPARATE
     * scheduler for hintDispatcher (same technique as the hint-staleness and cascade-dispatcher
     * tests above) to prove onLevelCompleted only actually runs once its own dispatcher is
     * advanced -- i.e. it is genuinely off the Main-side critical path, not just "probably fine
     * because it's usually fast."
     *
     * Both resolveCascade (fixed earlier) and onLevelCompleted share hintDispatcher here, and
     * they run sequentially -- resolveCascade first (dispatched to hintScheduler, then control
     * returns to Main to call commit()), then onLevelCompleted (commit() dispatches to
     * hintScheduler again). Reaching the point where isWon is set but onLevelCompleted hasn't
     * fired yet therefore needs one alternation per dispatcher hop, not a single advanceUntilIdle
     * pass on either scheduler alone.
     */
    @Test
    fun onLevelCompletedRunsOffMainAndDoesNotBlockStateUpdate() = runTest(testDispatcher) {
        val hintScheduler = TestCoroutineScheduler()
        val hintOnlyDispatcher = StandardTestDispatcher(hintScheduler)
        var completedCalls = 0
        val level = levelOf(listOf("ALEK", "PPPP", "PPPP", "PPPP"), listOf("KALE"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, hintDispatcher = hintOnlyDispatcher, onLevelCompleted = { _, _ -> completedCalls++ })

        vm.onMove(Move(Axis.Row, 0, forward = true)) // matches KALE
        testScheduler.advanceUntilIdle() // reaches, then stops at, resolveCascade's dispatch onto hintScheduler
        assertFalse(vm.uiState.value.isWon, "sanity: resolveCascade hasn't run yet -- its own dispatcher hasn't been advanced")

        hintScheduler.advanceUntilIdle() // runs resolveCascade; its continuation (commit()) is now queued back on Main
        assertEquals(0, completedCalls, "sanity: not reached yet")

        testScheduler.advanceUntilIdle() // resumes on Main, commit() sets isWon, then dispatches onLevelCompleted onto hintScheduler
        assertTrue(vm.uiState.value.isWon, "game state must update without waiting for onLevelCompleted")
        assertEquals(0, completedCalls, "onLevelCompleted must not have run yet -- its dispatcher hasn't been advanced")

        hintScheduler.advanceUntilIdle()
        assertEquals(1, completedCalls, "onLevelCompleted must run once its own dispatcher is actually advanced")
    }

    private class RecordingSoundEffects : SoundEffects {
        val events = mutableListOf<String>()
        override fun playShift() {
            events.add("shift")
        }
        override fun playCascadeStep(step: Int) {
            events.add("cascade:$step")
        }
        override fun playLevelComplete() {
            events.add("levelComplete")
        }
        override fun playGameOver() {
            events.add("gameOver")
        }
    }

    /**
     * P3 real-device playtesting finding: platform sound (ToneGenerator.startTone()) is a
     * synchronous native call whose underlying audio HAL route setup could block for hundreds of
     * milliseconds -- calling it inline on viewModelScope's default Main.immediate dispatcher
     * blocked the main thread and caused measured, reproducible dropped frames during the
     * explosion/cascade animation (dumpsys gfxinfo + logcat "Skipped N frames!" on the physical
     * device). Fixed by dispatching each sound call via `viewModelScope.launch(soundDispatcher)`
     * instead of calling it inline.
     *
     * This test verifies ordering through the real GameViewModel: playShift() is recorded before
     * playCascadeStep(), matching real call order, even though each is dispatched as its own
     * separate coroutine. Using the SAME shared testDispatcher as both Main and soundDispatcher
     * isn't a shortcut that hides a real race -- it specifically demonstrates why a genuinely
     * single-threaded dispatcher can't reorder these: each `launch(soundDispatcher) { ... }` call
     * is queued, in submission order, onto one thread's task queue, and neither playShift() nor
     * playCascadeStep() suspends internally to let a later-queued task cut in front. The
     * production default (a real dedicated single OS thread, see defaultSoundDispatcher's doc
     * comment) provides that same one-thread/no-internal-suspension guarantee, just with a real
     * thread instead of a virtual-time one.
     *
     * Fire-and-forget (game state never awaits sound) isn't separately asserted here via a
     * scheduler trick, because sharing one virtual-time scheduler between Main and soundDispatcher
     * can't cleanly distinguish "didn't wait" from "happened to finish first" -- it's instead a
     * structural guarantee of `launch { }` itself: it returns a Job immediately without suspending
     * the caller, so processShiftedGrid's own execution (match-finding, cascade, commit) can never
     * block on a sound coroutine it merely launched, regardless of which dispatcher that
     * coroutine runs on or how long it takes.
     */
    @Test
    fun soundEffectsFireInRealCallOrder() = runTest(testDispatcher) {
        val recorder = RecordingSoundEffects()
        // Same deterministic construction as CascadeTest's Phase-1/2 scenario: one row shift
        // completes "KALE" (row) and "KUTU" (col) simultaneously -- a single cascade step.
        val level = levelOf(listOf("ALEK", "UPPP", "TPPP", "UPPP"), listOf("KALE", "KUTU"), moveLimit = 5)
        val vm = GameViewModel(level, rng = Random(1), explosionDelayMs = 0L, soundEffects = recorder, soundDispatcher = testDispatcher, hintDispatcher = testDispatcher)

        vm.onMove(Move(Axis.Row, 0, forward = true))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isWon)
        // Both targets complete in this single move, so this scenario also wins the level --
        // levelComplete now legitimately fires too (GameViewModel.commit()'s new win/loss sound
        // hook), still strictly after the shift/cascade sounds that precede the win in real call
        // order.
        assertEquals(listOf("shift", "cascade:1", "levelComplete"), recorder.events, "shift and cascade must be recorded before the level-complete sound that follows them, in real call order")
    }
}
