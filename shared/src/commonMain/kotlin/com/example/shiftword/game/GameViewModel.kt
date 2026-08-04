package com.example.shiftword.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shiftword.domain.DEFAULT_FILLER_POOL
import com.example.shiftword.domain.apply
import com.example.shiftword.domain.bfsMinMovesToAnyTarget
import com.example.shiftword.domain.getMatchesWithPositions
import com.example.shiftword.domain.resolveCascade
import com.example.shiftword.model.Cell
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Level
import com.example.shiftword.model.Move
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Bridges GameEngine (the pure Phase 1-3 domain functions — Grid.apply, findMatchedWords,
 * resolveCascade) to Compose. GameEngine itself is unmodified by this phase; this class only
 * sequences calls to it and exposes the result as a StateFlow, per ARCHITECTURE.md §8.
 */
class GameViewModel(
    private val level: Level,
    private val rng: Random = Random.Default,
    // Which letters a cascade refills a cell with — must match the level's own language
    // (LanguageProfile.fillerPool), otherwise an English level would get Turkish letters
    // refilled into it after a cascade. Defaults to the original Turkish pool so existing
    // call sites/tests are unaffected.
    private val fillerPool: String = DEFAULT_FILLER_POOL,
    // Overridable so tests can run with no real-time delay while production keeps a felt pause.
    // Reduced Motion setting (GAME_DESIGN.md): when [reducedMotion] is true, [REDUCED_EXPLOSION_DELAY_MS]
    // is used instead, regardless of this value -- see processShiftedGrid.
    private val explosionDelayMs: Long = 350L,
    // Reduced Motion setting: off by default, matching SettingsRepository's column default. Also
    // threaded down to GridBoard by the caller (GameScreen/AppNavHost) for the animation-duration
    // side of this same setting -- this constructor param only controls the cascade *pacing*
    // (the felt pause between an explosion and its refill), which lives in this class rather than
    // GridBoard because it's a timing decision about game state (explodingCellIds), not a visual
    // animation-spec detail.
    private val reducedMotion: Boolean = false,
    // Defaults to silent — real sound is opt-in (app shell passes platformSoundEffects()), see
    // NoSoundEffects's doc comment for why the default must never touch platform audio APIs.
    private val soundEffects: SoundEffects = NoSoundEffects,
    // Persistence is intentionally not a direct GameViewModel dependency — the caller decides
    // what "level completed" means for storage (ProgressRepository.recordCompletion), keeping
    // this class testable without a database and reusable if that ever changes.
    private val onLevelCompleted: (stars: Int, movesUsed: Int) -> Unit = { _, _ -> },
    // Where requestHint's BFS actually runs — defaults to a real background dispatcher in
    // production (see requestHint's doc comment for why), but is injectable so tests can pass
    // the same TestDispatcher used for Main, keeping virtual-time control over it instead of
    // racing a real thread pool against testScheduler.advanceUntilIdle().
    private val hintDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // P3 real-device playtesting finding: platform sound (ToneGenerator.startTone(), see
    // SoundPlayer.android.kt) is a synchronous native call whose underlying audio HAL route
    // setup/teardown can block for hundreds of milliseconds on real hardware. Calling it inline
    // on processShiftedGrid's caller -- viewModelScope.launch defaults to Dispatchers.Main.immediate
    // -- blocked the main thread and caused measured, reproducible dropped frames during the
    // explosion/cascade animation (confirmed via dumpsys gfxinfo + logcat "Skipped N frames!"
    // Choreographer warnings on the physical device that also produced the P0 crash). Fixed by
    // dispatching sound calls onto their own dedicated single thread, separate from
    // [hintDispatcher]'s Dispatchers.Default pool (audio HAL calls shouldn't compete with hint's
    // BFS work for threads there). Defaults to [defaultSoundDispatcher] (shared across every
    // GameViewModel instance, not one-per-instance -- see its doc comment for why), injectable so
    // tests can pass the same TestDispatcher used for Main for deterministic, ordered assertions.
    private val soundDispatcher: CoroutineDispatcher = defaultSoundDispatcher,
    // Feature 3 (GAME_DESIGN.md §9c): the GLOBAL hint-credit pool's current value, read by the
    // caller from SettingsRepository at construction -- NOT reset per level (see hintCreditsRemaining
    // field doc comment on GameUiState). Defaults to effectively unlimited, matching hintCreditsRemaining's
    // own default, so existing call sites/tests are unaffected.
    private val initialHintCredits: Int = Int.MAX_VALUE,
    // Fired once a hint request is actually accepted (not blocked by isBusy/no-credits) --
    // persistence is intentionally not a direct GameViewModel dependency, same reasoning as
    // onLevelCompleted above. Caller (AppNavHost) wires this to SettingsRepository.consumeHintCredit().
    private val onHintUsed: () -> Unit = {},
    // Onboarding (GAME_DESIGN.md §9h): when true, the hint-nudge animation plays once,
    // unprompted, as soon as this GameViewModel is constructed -- see autoPlayOnboardingHint.
    // Defaults false so every existing call site/test is unaffected. The caller (AppNavHost)
    // derives this from isOnboardingLevel(hasSeenOnboarding, levelNumber), never level number
    // alone -- see that function's doc comment for why all onboarding surfaces share one gate.
    private val playOnboardingHintOnStart: Boolean = false,
    // Onboarding (GAME_DESIGN.md §9h): when true, movesSinceLastMatch reaching
    // [hintButtonCalloutThresholdMoves] triggers the one-time hint-button callout. Same
    // isOnboardingLevel-derived gate as [playOnboardingHintOnStart].
    private val hintButtonCalloutEligible: Boolean = false,
    private val hintButtonCalloutThresholdMoves: Int = 3,
) : ViewModel() {

    private var nextCellId = 0L

    private fun freshGrid(): Grid {
        val cells = level.initialCells.map { row -> row.map { ch -> Cell(ch, nextCellId++) } }
        return Grid(level.gridSize, cells)
    }

    private val _uiState = MutableStateFlow(
        GameUiState(
            grid = freshGrid(),
            targetWords = level.targetWords,
            foundWords = emptySet(),
            moveCount = 0,
            moveLimit = level.moveLimit,
            minMovesToSolve = level.minMovesToSolve,
            minMovesIsExact = level.minMovesIsExact,
            isWon = false,
            isLost = false,
            hintCreditsRemaining = initialHintCredits,
        ),
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Onboarding (GAME_DESIGN.md §9h): fires exactly once per instance, at construction, mirroring
    // how UNLIMITED_HINTS_FOR_TESTING/every other constructor-driven one-shot in this class works.
    // A fresh GameViewModel is created per level/replay attempt (AppNavHost's remember(currentLevel,
    // attempt)), so this naturally re-fires on a replay of level 1 taken before the player's first
    // win -- acceptable and intended, since hasSeenOnboarding (and therefore playOnboardingHintOnStart,
    // via isOnboardingLevel) only ever flips false->true on that first win, never back.
    init {
        if (playOnboardingHintOnStart) autoPlayOnboardingHint()
    }

    // P3 real-device playtesting finding: moving resolveCascade() off Main (see processShiftedGrid)
    // means it can now run on a genuinely multi-threaded dispatcher, not the always-single-
    // threaded Main.immediate it ran on before. Nothing previously stopped a second onMove()/
    // debugForceCompleteWord() call from landing while an earlier one's cascade was still
    // resolving -- neither this class nor GridBoard's drag gesture gated on explodingCellIds the
    // way the Hint button already does -- so two overlapping calls could reach resolveCascade at
    // the same time. That was harmless before (both would still serialize on Main), but would be
    // a genuine data race now: resolveCascade's nextId/refillLetter closures mutate the shared
    // nextCellId var and pull from the shared rng, neither of which is safe to touch from two
    // threads at once. Fixed at the source instead of adding synchronization to what they touch:
    // gate every entry point on explodingCellIds, so only one call can ever be past the
    // match-found point at a time, the same way the Hint button already gates on it.
    private fun isBusy(state: GameUiState) = state.isWon || state.isLost || state.explodingCellIds.isNotEmpty()

    fun onMove(move: Move) {
        val state = _uiState.value
        if (isBusy(state)) return
        viewModelScope.launch { processShiftedGrid(state, state.grid.apply(move)) }
    }

    /**
     * Debug/playtest-only: force the current grid to spell one remaining target word outright,
     * then run it through the exact same match/explosion/cascade path as a real move — this is
     * not a simulated stand-in, it exercises the production resolveCascade call. Gated by the
     * caller (only a debug-build UI surface should invoke this), never by GameViewModel itself.
     */
    fun debugForceCompleteWord() {
        val state = _uiState.value
        if (isBusy(state)) return
        val word = state.remainingTargets.firstOrNull { it.length == state.grid.size } ?: return

        val forcedCells = state.grid.cells.mapIndexed { r, row ->
            if (r == 0) word.mapIndexed { c, ch -> Cell(ch, state.grid.cells[0][c].id) } else row
        }
        val forcedGrid = Grid(state.grid.size, forcedCells)
        viewModelScope.launch { processShiftedGrid(state, forcedGrid) }
    }

    // Tracks the in-flight requestHint() coroutine, if any -- see requestHint's doc comment for
    // why a second, unrelated bug needed this beyond the grid-staleness check below.
    private var hintJob: Job? = null

    /**
     * Reuses the existing bounded BFS solver as-is; no separate hint-finding logic. Runs on
     * [hintDispatcher] (a real background dispatcher in production), not the Main dispatcher
     * [viewModelScope] otherwise uses -- an audit found BFS's own documented worst case (~3.5s,
     * ALGORITHM_VALIDATION.md Risk R4's timing table) would otherwise freeze the whole UI thread
     * for that long, since bfsMinMovesToAnyTarget never suspends internally. Also gated by the
     * caller (GameScreen disables the Hint button while explodingCellIds is non-empty) so a hint
     * can't be requested mid-cascade-animation.
     *
     * Real-device playtesting report (re-opened priority-1 finding): a hint could return a
     * stale suggestion that no longer corresponded to a valid move, specifically when hint usage
     * was interleaved with manual (non-hint) play -- a scenario the existing "hint after
     * applying the previous hint" regression test didn't cover, because that test always
     * `advanceUntilIdle()`s between actions, artificially serializing what real wall-clock
     * gameplay does not.
     *
     * The core bug: `state.grid`/`state.remainingTargets` are captured synchronously here, but
     * the BFS computation on [hintDispatcher] can take real, multi-second wall-clock time (R4's
     * ~3.5s worst case) -- nothing stops the player from making a manual move in that window (the
     * Hint button is only disabled during `explodingCellIds`, not during a pending hint
     * computation). The result, once ready, was written back unconditionally, describing a grid
     * that may no longer be current. Fixed by checking the grid is still exactly what it was when
     * the request started before writing `hintMove` -- if a move landed in the meantime, the
     * now-stale result is simply dropped rather than shown. This is the necessary and sufficient
     * fix: since BFS is a pure function of its captured grid/targets, two overlapping requests
     * that captured the SAME (unchanged) grid always compute the same result regardless of
     * completion order, so this guard alone closes every case where a stale write would actually
     * be observable.
     *
     * [hintJob] cancellation (tracked below) is a complementary hygiene fix, not an independently
     * load-bearing one for this particular bug: without it, tapping Hint again while a previous
     * request is still computing lets both run concurrently to completion, wasting CPU on a
     * result that (per the guard above) will just be dropped anyway if it arrives late. That waste
     * turned out to matter for a separate, more serious reason -- see [bfsMinMovesToAnyTarget]'s
     * [isActive] parameter, wired to this coroutine's cancellation state below: the same real-
     * device P0 investigation that reproduced this bug also found BFS itself had no cancellation
     * checkpoints, so an abandoned/superseded hint computation kept consuming memory to full
     * completion regardless of being cancelled -- a real contributing factor in an on-device
     * OutOfMemoryError crash (see [bfsMinMovesToAnyTarget]'s doc comment for the full analysis).
     */
    fun requestHint() {
        val state = _uiState.value
        if (isBusy(state)) return
        // Feature 3: gate on the GLOBAL credit pool. Consumed immediately on acceptance (not
        // conditioned on the async BFS result below, and not undone if that result later turns
        // out stale and gets dropped) -- the player spent a hint *attempt*, which is the
        // consequential/felt action, not an implementation detail of staleness plumbing.
        //
        // TEMPORARY (Task 3, playtesting build only): UNLIMITED_HINTS_FOR_TESTING bypasses this
        // gate so testers aren't blocked by the credit pool while trying other features. The
        // hintCreditsRemaining infrastructure below (SettingsRepository-backed) is untouched --
        // credits still decrement normally (and will go negative/display oddly while this flag is
        // on, which is fine for a testing build) -- this is a one-line revert: flip the constant
        // back to false once testing is done.
        if (!UNLIMITED_HINTS_FOR_TESTING && state.hintCreditsRemaining <= 0) return
        hintJob?.cancel()
        // Clear any previous hintMove synchronously, not just decrement credits: GridBoard's hint
        // animation (see its LaunchedEffect) triggers on hintMove transitioning from null to
        // non-null, so this guarantees a fresh transition -- and therefore a replayed animation --
        // even when the new request resolves to the exact same Move as the previous one (same
        // grid/targets -> same BFS result), which plain value-equality on hintMove would not
        // otherwise retrigger.
        // Onboarding: a real hint request always clears isOnboardingHint (this is no longer the
        // unprompted auto-played one) and dismisses the hint-button callout if it was showing --
        // the player just found and used the button, so there's nothing left to point out.
        _uiState.value = state.copy(
            hintCreditsRemaining = state.hintCreditsRemaining - 1,
            hintMove = null,
            isOnboardingHint = false,
            hintButtonCalloutVisible = false,
        )
        viewModelScope.launch(hintDispatcher) { onHintUsed() }
        hintJob = viewModelScope.launch {
            val result = withContext(hintDispatcher) {
                val scope = this
                bfsMinMovesToAnyTarget(state.grid, state.remainingTargets, isActive = { scope.isActive })
            }
            val current = _uiState.value
            if (current.grid !== state.grid) return@launch // grid changed while BFS ran -- stale, drop it
            _uiState.value = current.copy(hintMove = result?.path?.firstOrNull())
        }
    }

    /**
     * Onboarding (GAME_DESIGN.md §9h): shows the very first move to a brand-new player, the same
     * way requestHint() does -- reusing the identical BFS call and the identical hintMove
     * null->non-null transition GridBoard's animation keys on -- but as an unprompted, automatic
     * one-shot rather than a player-initiated request: no isBusy/credit gate (there's nothing to
     * be busy with yet, this only ever runs from init{}), no hintCreditsRemaining decrement, and
     * no onHintUsed() call, since spending a hint credit on onboarding would be indistinguishable
     * from a real hint the player never asked for. [GameUiState.isOnboardingHint] is set alongside
     * hintMove so GameScreen can show the onboarding swipe bubble instead of the regular tryHint
     * text for this specific occurrence.
     */
    private fun autoPlayOnboardingHint() {
        val state = _uiState.value
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            val result = withContext(hintDispatcher) {
                val scope = this
                bfsMinMovesToAnyTarget(state.grid, state.remainingTargets, isActive = { scope.isActive })
            }
            val current = _uiState.value
            if (current.grid !== state.grid) return@launch // grid changed while BFS ran -- stale, drop it
            _uiState.value = current.copy(hintMove = result?.path?.firstOrNull(), isOnboardingHint = true)
        }
    }

    // Onboarding (GAME_DESIGN.md §9h): guarantees the hint-button callout is triggered at most
    // once per GameViewModel instance -- a plain state-derived condition (movesSinceLastMatch >=
    // threshold) would otherwise re-arm itself on every subsequent no-match move past the
    // threshold, contradicting "only ever shown once."
    private var hintCalloutShownOnce = false

    private suspend fun processShiftedGrid(state: GameUiState, shifted: Grid) {
        // Fire-and-forget on soundDispatcher -- see its constructor doc comment. Dispatched
        // before any of this function's own suspension points (delay, etc.), so it's always the
        // first thing queued on soundDispatcher for this move, ahead of any cascade-step sounds
        // queued later in this same call.
        viewModelScope.launch(soundDispatcher) { soundEffects.playShift() }
        val remaining = state.remainingTargets
        val matches = getMatchesWithPositions(shifted, remaining).filter { it.first in remaining }

        if (matches.isEmpty()) {
            commit(state, shifted, state.foundWords)
            return
        }

        val explodingIds = matches.flatMap { (_, positions) -> positions.map { (r, c) -> shifted.cells[r][c].id } }.toSet()
        _uiState.value = state.copy(grid = shifted, explodingCellIds = explodingIds, hintMove = null, isOnboardingHint = false)
        delay(if (reducedMotion) REDUCED_EXPLOSION_DELAY_MS else explosionDelayMs)

        // P3 real-device playtesting finding: resolveCascade's exhaustive/random-sampling
        // reachability search (the R4-addendum machinery guaranteeing every remaining target
        // stays solvable through a cascade -- see Cascade.kt) is pure CPU work that was running
        // inline here, i.e. on whatever dispatcher this suspend function's caller used --
        // viewModelScope.launch defaults to Dispatchers.Main.immediate. Measured directly on a
        // physical device (timing probe around this call, correlated with dumpsys gfxinfo +
        // logcat "Skipped N frames!" Choreographer warnings): up to 1.6s blocking the main thread
        // on a single cascade, more than enough to explain the reported explosion/cascade
        // animation jank on its own -- a bigger contributor than the sound-effects blocking fixed
        // alongside this (see soundDispatcher). Fixed the same way hint's BFS already was: run it
        // on hintDispatcher instead of inline. Reusing hintDispatcher rather than adding a third
        // dedicated one is deliberate -- this is pure CPU work with no HAL/native blocking
        // concerns (unlike sound), and hint and cascade can never run concurrently in practice
        // (both are gated by isBusy(), which now includes explodingCellIds -- see its doc comment
        // for why that gating became load-bearing for correctness, not just UX, once this moved
        // off the always-single-threaded Main dispatcher).
        val cascade = withContext(hintDispatcher) {
            resolveCascade(
                grid = shifted,
                targetsRemaining = remaining,
                nextId = { nextCellId++ },
                refillLetter = { fillerPool.random(rng) },
                rng = rng,
            )
        }
        // Escalating cue per chain step, reusing resolveCascade's own CascadeStep.step —
        // no separate chain-length state kept here. Fire-and-forget on soundDispatcher (see its
        // doc comment): game state below never waits on these. Each step is launched from this
        // same calling coroutine in chainLog order, and soundDispatcher is single-threaded with
        // no suspension inside playCascadeStep itself, so the single thread's task queue runs
        // them strictly in submission order -- chain-step audio can never race or reorder.
        cascade.chainLog.forEach { step -> viewModelScope.launch(soundDispatcher) { soundEffects.playCascadeStep(step.step) } }
        val newlyFound = remaining - cascade.remainingTargets
        commit(state, cascade.grid, state.foundWords + newlyFound, newlyFound)
    }

    private suspend fun commit(previous: GameUiState, finalGrid: Grid, foundWords: Set<String>, newlyFound: Set<String> = emptySet()) {
        val newMoveCount = previous.moveCount + 1
        val isWon = foundWords.containsAll(previous.targetWords)
        val isLost = !isWon && newMoveCount >= previous.moveLimit
        // Feature 2 (GAME_DESIGN.md §9b): attribute every word found by this move -- including
        // any found incidentally as part of the same cascade -- to this same move count, since
        // that's the move count a player would actually see on screen when it happened.
        val newFoundAtMoveCount = previous.foundAtMoveCount + newlyFound.associateWith { newMoveCount }

        // Onboarding (GAME_DESIGN.md §9h): movesSinceLastMatch resets to 0 the moment this move
        // found any word (including incidentally, via cascade), otherwise increments -- drives the
        // hint-button callout, which is triggered at most once per instance (hintCalloutShownOnce)
        // regardless of how many further no-match moves follow.
        val newMovesSinceLastMatch = if (newlyFound.isNotEmpty()) 0 else previous.movesSinceLastMatch + 1
        val triggersCallout = hintButtonCalloutEligible &&
            !hintCalloutShownOnce &&
            !isWon && !isLost &&
            newMovesSinceLastMatch >= hintButtonCalloutThresholdMoves
        if (triggersCallout) hintCalloutShownOnce = true

        _uiState.value = previous.copy(
            grid = finalGrid,
            foundWords = foundWords,
            foundAtMoveCount = newFoundAtMoveCount,
            moveCount = newMoveCount,
            isWon = isWon,
            isLost = isLost,
            explodingCellIds = emptySet(),
            hintMove = null,
            isOnboardingHint = false,
            movesSinceLastMatch = newMovesSinceLastMatch,
            // Visible only for the single move that crossed the threshold -- the NEXT move (this
            // same commit() path, next time it runs) always overwrites it back to false via this
            // same assignment, since hintCalloutShownOnce prevents triggersCallout from ever being
            // true again. Dismissed earlier than that if the player taps Hint directly (see
            // requestHint's explicit hintButtonCalloutVisible = false).
            hintButtonCalloutVisible = triggersCallout,
        )
        // Win/loss sound: fired exactly at the transition (isBusy() already guarantees commit()
        // is never re-entered once isWon/isLost is true, so seeing either flag true here always
        // means it just became true this call, never a repeat). Same soundDispatcher threading
        // discipline as playShift/playCascadeStep -- see soundDispatcher's own doc comment for
        // why this must stay off both Main and hintDispatcher.
        if (isWon) viewModelScope.launch(soundDispatcher) { soundEffects.playLevelComplete() }
        if (isLost) viewModelScope.launch(soundDispatcher) { soundEffects.playGameOver() }

        if (isWon) {
            // P3 audit finding (third instance of the same pattern hint's BFS and cascade's
            // reachability search already hit): onLevelCompleted is a caller-supplied callback --
            // AppNavHost's implementation does two synchronous SQLDelight writes
            // (LevelRepository.insert, ProgressRepository.recordCompletion) -- and was being
            // invoked inline here, on whatever dispatcher processShiftedGrid's caller used
            // (Main.immediate by default). Fixed the same way: run it on hintDispatcher instead.
            withContext(hintDispatcher) {
                onLevelCompleted(starsFor(newMoveCount, level.minMovesToSolve, level.moveLimit), newMoveCount)
            }
        }
    }

    companion object {
        // Reduced Motion setting: the felt pause between an explosion and its cascade
        // resolving/refilling, used instead of [explosionDelayMs] when [reducedMotion] is true --
        // short-but-nonzero (not 0L) so the player can still register "something got cleared"
        // before the refill lands, matching GridBoard's REDUCED_EXPLODE_DURATION_MS reasoning
        // (near-instant, not literally instant).
        private const val REDUCED_EXPLOSION_DELAY_MS = 60L

        // TEMPORARY (Task 3): set back to false to restore normal hint-credit gating -- see
        // requestHint's doc comment at the check site for what this bypasses and why it's safe
        // (SettingsRepository's hintCredits infrastructure itself is untouched). While true, this
        // intentionally fails GameViewModelHintCreditsTest's
        // requestHintConsumesOneGlobalCreditPerAcceptedRequestAndBlocksAtZero (it asserts the
        // gate blocks requests at 0 credits, which is exactly what this flag bypasses) -- that is
        // expected, not a regression to chase; the test passes again the moment this flips back.
        private const val UNLIMITED_HINTS_FOR_TESTING = true // set false after test

        // Shared by every GameViewModel instance, not created per-instance: AppNavHost builds a
        // new GameViewModel on every level transition/replay (remember(currentLevel, attempt)),
        // and a per-instance dedicated thread would leak a thread on every one of those, since
        // CloseableCoroutineDispatcher needs an explicit close() this class has no hook to call.
        // One dedicated thread for the whole app's sound effects is also all that's ever needed:
        // moves/cascades are inherently sequential from a single player's perspective, so there's
        // no throughput reason to want more than one thread here.
        @OptIn(ExperimentalCoroutinesApi::class)
        private val defaultSoundDispatcher: CoroutineDispatcher by lazy { newSingleThreadContext("SoundEffects") }
    }
}
