# Algorithm Validation Report

## Purpose

Before writing any Kotlin/KMP code, the core algorithms this game depends on
were prototyped in Python and **deliberately stress-tested to find failure
modes**, not just to demonstrate the happy path. This document is the record
of that process: what was tested, what broke, and what design decision fixed
it. It exists so that anyone porting this logic to Kotlin understands *why*
the architecture has the specific safety valves it has — several of them
exist because something genuinely crashed during validation, not out of
theoretical caution.

Reference implementation: `word_shift_prototype_v2.py` (included in this
project). Every claim below is reproducible by running that file.

## Why Prototype in Python First

The riskiest parts of this project are not the UI or the animations — CMP's
animation APIs are well-understood and low-risk. The riskiest part is
**whether the level generator can be trusted to only ever produce solvable,
fair puzzles**, because that guarantee is the entire foundation the game
sits on. Testing that logic in Python (fast iteration, no build step) before
committing to Kotlin syntax let failures surface cheaply.

## Risk Log

### R1 — Turkish Character / Dictionary Integrity

**Risk:** Kotlin has a well-known "Turkish locale bug" where
`String.uppercase()` / `.lowercase()` behave differently under a Turkish
locale (`i` ↔ `İ`, `ı` ↔ `I`). A silent mismatch here means a word the
dictionary "contains" doesn't actually match what a shift produces on
screen.

**What happened during testing:** An early sample dictionary was built by
writing ASCII source words (e.g. `"kapi"`) and auto-converting to uppercase.
This silently produced **`"KAPİ"`** (dotted İ) instead of the correct
**`"KAPI"`** (dotless I, for *kapı* — door). The conversion code was
correct; the input data was wrong, and nothing errored.

**Resolution:**
- Explicit character maps (`TR_UPPER_MAP` / `TR_LOWER_MAP`) replace all
  locale-dependent case conversion — never rely on default `uppercase()`.
- A two-part **import-time validator** was added:
  1. *Alphabet check* — every character must be in the valid Turkish
     uppercase set.
  2. *Round-trip check* — `upper(lower(word)) == word`, which catches
     encoding/mapping inconsistencies.
- **Known limitation, stated explicitly:** the validator cannot catch
  semantic typos like `kapi` vs `kapı` — both are syntactically valid. The
  only real fix is sourcing dictionary data directly in correct Unicode
  from a trustworthy source (e.g. an official TDK word list), never via
  ASCII transliteration + auto-uppercasing.

**Carries into Kotlin as:** a mandatory validation step in the dictionary
import pipeline (`ARCHITECTURE.md`, §9) — no word reaches the database
without passing it.

---

### R2 — Non-Intersecting Word Placement

**Risk:** The first version of the level generator placed each target word
in its own dedicated row, with zero intersections between words. This
produces grids that don't feel like organic crosswords and reduces the
richness of the puzzle.

**Resolution:** Replaced with a greedy placement algorithm that, for each
target word, evaluates every available row and column, scores each option
by how many letters it would intersect with already-placed words, and
prefers the highest-intersection option that doesn't produce a letter
conflict. Falls back to a non-intersecting placement only when no
intersecting option exists.

**Initial validated result (Python prototype):** across repeated trials, a
meaningful fraction of generated levels contained at least one genuine word
intersection (roughly a fifth to a third of trials, depending on word
pool), up from zero in the v1 approach — flagged at the time as an
incremental improvement with room for further work, not a fully closed
risk.

**Follow-up improvement (Kotlin port, Phase 2):** the deferred idea —
trying multiple word orderings before committing to a placement, since
greedy placement order affects how many intersections are found — was
implemented: each generation attempt now shuffles target-word order before
running the greedy placement and keeps the best-scoring valid layout
instead of the first one. Re-measured directly:

| Scenario | Before | After |
|---|---|---|
| 12-word pool, 4×4 | 20.6% | 53.0% |
| 30-word pool, 4×4 | 14.8% | 47.2% |
| 15-word pool, 5×5 | — | 65.4% |

No timing regression from this change. A regression-guard assertion
(≥35% intersection rate) was added to the Kotlin test suite so this can't
silently decay in future changes.

**Status:** resolved, not just improved — closing out the "future
enhancement" framing this risk originally carried.

**Re-verified at real dictionary scale (Phase 8, 1,041-word curated
Turkish dictionary — see `DICTIONARY_SOURCING.md`):**

| Scenario | Small-pool (Phase 2) | Real-scale (Phase 8) |
|---|---|---|
| 4×4 (373-word pool) | 53.0% | 30.4% |
| 5×5 (668-word pool) | 65.4% | 42.2% |

The drop is expected, not a regression: a lexically diverse real dictionary
naturally reduces the odds that 3 randomly sampled target words share
letters, compared to the small hand-picked pools used during initial
validation. Both real-scale figures remain well above the pre-R2-fix
~20% baseline, so the fix is still doing real work. **Open item:** the
existing regression-guard assertion (≥35%) was written against the
small-pool figures and does not currently have an equivalent guard tuned
for the real-scale dictionary — the 4×4 real-scale result (30.4%) would
actually fail that threshold if the guard were pointed at the real
dictionary. Worth adding a second, realistic-scale regression guard rather
than relying solely on the small-pool one.

---

### R3 — Cascade / Chain-Reaction Was Never Implemented or Tested

**Risk:** The explosion → gravity → refill → re-check loop existed only as
pseudocode in early architecture discussion. Two specific dangers: (a) an
incorrect implementation could silently miscount or misplace cleared cells,
and (b) an unbounded chain — refilled letters accidentally re-triggering a
match — could loop indefinitely.

**Resolution:**
- Implemented `explode_and_refill` (clear matched cells → gravity-compact
  each column → fill new letters at the top) and `resolve_cascade` (repeats
  this until no new target-word matches are found).
- **A hard `max_chain_steps` safety cap was added** regardless of whether
  runaway chains are likely in practice — cheap insurance against a
  category of bug (infinite loop) that is expensive to debug once shipped.
- Explicitly tested against a real BFS-derived solution path (not just a
  hand-crafted example) to confirm the mechanic works under actual gameplay
  conditions, not only in isolation.
- **Also tested and confirmed:** a single shift can complete a row and a
  column simultaneously. This is handled correctly by `resolve_cascade`
  clearing the union of both matched cell sets in one step — this was
  verified by deliberately searching for and finding such a scenario, not
  assumed to work.

---

### R4 — BFS Solver Performance (Confirmed Crash)

**Risk:** The generator used a BFS solver both to confirm a level's
solvability and to compute the exact minimum move count. Search branching
factor is `4 × grid_size` (16 for a 4×4 grid); the concern was whether this
scales safely as required search depth grows.

**What happened during testing:** Initial "successful" performance tests
were misleading — every test case happened to find a match within 1–4
moves, so worst-case behavior was never exercised. A deliberate adversarial
test (target words that can never match) forced the BFS to exhaust its
search depth:

| Depth | Time |
|---|---|
| 3 | 0.02s |
| 4 | 0.2s |
| 5 | 3.5s |
| 6 | **50+ seconds, then killed by the OS (out of memory)** |

This is a confirmed, reproducible crash — not a theoretical concern.

**Resolution — architectural change, not just a bigger timeout:**
- Solvability is **not** BFS-dependent. Because scrambling is done via
  invertible moves, the scrambled grid is *structurally* guaranteed
  solvable in at most the scramble length — this was true all along and
  means BFS was never actually load-bearing for correctness.
- BFS was demoted to an **optional refinement**: it attempts to find a
  tighter/exact move count under a **hard depth cap (default 5)**, chosen
  directly from the timing table above (depth 5 is the last safe value
  before the observed blow-up).
- If BFS doesn't resolve within the cap, generation does **not** fail or
  retry — it falls back to the structural upper bound as `minMovesToSolve`
  and marks the result `minMovesIsExact = false`, so downstream code (e.g.
  a "such an efficient solution!" UI message) can distinguish an exact
  result from an approximate one.
- Re-tested after the fix against both normal and adversarial inputs: a
  scenario that previously would have hung/crashed now completes 10 level
  generations in 0.14 seconds total.

**Carries into Kotlin as:** the BFS solver implementation must ship with the
depth cap as a non-negotiable constant, not a tunable that could be
loosened without re-validating against the same timing table.

**Addendum — R2/R3 interaction found in real-device playtesting:** R4's
guarantee was always about the level's starting scrambled grid — nothing
in its original design or testing considered what happens to the OTHER
target words' solvability once a cascade (R3) fires mid-game. Real-device
playtesting surfaced exactly that gap: R2 deliberately places target words
so they intersect (share a cell) to raise the crossword-feel intersection
rate; R3's cascade explosion clears a matched word's entire row/column and
refills it with brand-new random letters — including, potentially, the
shared cell a DIFFERENT, still-remaining target depends on. A naive refill
has no obligation to restore a letter that target still needs, silently
voiding the "every generated level is solvable" guarantee for whichever
targets are found later (and, from the player's side, made it look like
target words had to be found in a specific order — whichever order
happened to avoid destroying an intersecting word's needed letter).

Reproduced empirically (simulating repeated force-completions through the
real production cascade path across 2,000 generated levels, before any
fix): 1552/2000 (77.6%) of levels had at least one remaining target left
with insufficient letters at some point — letters that shifts can never
restore, since row/column shifts only permute existing letters, they never
create or destroy them.

Closing this took three iterations, kept here because the first two looked
like progress but weren't the actual fix:
1. Retry the refill with fresh random letters (whole filler alphabet)
   until the letter-count is right, up to 50 tries. Only got the failure
   rate down to 25% — the odds of independently drawing several specific
   letters together are too low for blind full-alphabet retrying to close
   the gap reliably.
2. Compute the exact letter deficiency and force those specific letters
   into the refill (deterministic, not probabilistic). This closed the
   letter-count case completely (0/3000, a true necessary-condition
   guarantee, not an empirical one) but left a harder residual: letters
   all present, yet not confirmed reachable within `BFS_HARD_DEPTH_CAP` by
   any sequence of shifts. A bounded random-retry (15 tries, full filler
   alphabet) checking BFS reachability got this down from 1.05% (21/2000)
   to 0.1% (1/1000) — real progress, but R4's promise is "structural, by
   construction," not "very likely," so a nonzero rate was treated as an
   unresolved regression, not shipped as a permanent soft-guard threshold.
3. Restrict the search to only the letters that could possibly matter
   (the remaining targets' own letters — any other letter is
   interchangeable filler that can't itself complete a target, so this
   restriction is completeness-preserving, not a shortcut) and
   exhaustively search that scoped space (not sample it) when quick random
   sampling doesn't hit immediately, guaranteed to find a working refill
   if one exists within the space searched. This closed the residual to
   0/3000 — re-verified at the same trial count used to measure the
   original 77.6% figure.

**Status: fully closed, not downgraded.** `CascadeResult` now reports
`hadUnconfirmedArrangement` whenever even the exhaustive search fails, so
this is asserted on directly in `CascadeIntersectionGuaranteeTest`
(`cascadesNeverFallBackToAnUnconfirmedArrangement`, hard `== 0` assertion)
rather than inferred from a soft rate. R4's "structural, by construction"
guarantee now holds for every target word throughout an entire playthrough,
not just for reaching the first one from the level's starting grid.

**Clarification — the guarantee is point-in-time, not standing:** surfaced
while bringing `GameViewModelFullPlaythroughStressTest`'s English trial
counts up to match Turkish's exactly (closing a language-parity gap where
English had previously run at half the trial count with no documented
reason). `resolveCascade`'s BFS-reachability guarantee is certified only
AT THE MOMENT a cascade resolves — it is not a standing promise across
further moves applied afterward. A path computed toward one target can go
stale mid-execution if an incidental match (a different target completed
along the way, via the same or a chained cascade) changes the grid before
the path finishes; the stale remainder can then wander outside a later
target's depth-5-reachable window even though that target's letters are
still fully present (letter-sufficiency is necessary, not sufficient, for
reachability — see the `BFS_HARD_DEPTH_CAP` note above).

This is not a production defect: the actual hint and move-application code
always re-derives its path against the CURRENT grid on every request, it
never executes a precomputed plan blindly. It only surfaced because an
earlier version of the test harness did the opposite — planning a full
path once, then applying every step in it regardless of intervening state
changes. Confirmed by direct reproduction (i=106, EN seed 20000106,
order=[LIKE, HEAR, IDEA]): IDEA was completed incidentally while the test
was still executing a stale path toward LIKE, and the leftover steps of
that path then wandered the grid outside HEAR's reachable window even
though H/E/A/R were all still on the board. Fixed in the test itself: each
target is now retried with a freshly recomputed path whenever the
remaining-targets set changes underneath it, rather than abandoning the
target or continuing to execute the stale plan — mirroring what a real
hint request would actually do.

**Addendum — `moveLimit` was calibrated against reaching ONE target, not
completing the level** (priority-2 real-device playtesting finding):
`GAME_DESIGN.md` §4 defines the move limit as a small buffer over "the
minimum number of shifts required to solve the level" — i.e. to complete
every target word. The original `generateLevel` implementation instead
set `moveLimit = bfsMinMovesToAnyTarget(...).minMoves + buffer`, and
`bfsMinMovesToAnyTarget` returns as soon as it finds a path to whichever
target is nearest to the scrambled starting grid — the distance to one
word, not the cost of winning. This was never validated against the real
win condition (all 3 targets found).

Manual playtesting reported the move limit as inconsistent and often too
low, with some levels genuinely needing 25-30+ moves to finish all 3
words — far outside the ~10-12 move range `GAME_DESIGN.md` intends.
Measured directly (`MoveLimitCalibrationTest`, simulating an optimal,
immediately re-planned playthrough — same "re-plan on staleness"
discipline as the point-in-time clarification above — against 500 real
generated levels per language at 4×4): `moveLimit` averaged ~5.0-5.1 moves
while actually completing all 3 targets averaged ~6.7-6.8 — 76-78% of
levels needed MORE real moves than the limit allowed, even under perfect
play. This is a real production gap, not a test-harness artifact (unlike
the point-in-time clarification above) — confirmed by an independent
on-device manual replay of the hint flow, which also incidentally
confirmed the hint system itself is NOT stale (see that clarification).

**Fix, and why it isn't an exact recomputation:** the natural fix — plan
each target in turn from the current grid and sum the real moves needed,
same as the measurement above — was tried directly inside `generateLevel`
and crashed with `OutOfMemoryError` at 5×5 scale (branching factor 20,
`BFS_HARD_DEPTH_CAP=5`) over a 300-trial batch. A cheaper variant (summing
independent single-target BFS distances from the pristine grid, no
cascade simulation) hit the same wall for the same underlying reason: a
BFS query for one specific target is far more likely than the original
nearest-of-N query to have to exhaust its entire depth-5 search space
before concluding a target is unreachable, and at 5×5's larger branching
factor that exhaustion is what blew up memory — the same class of failure
Risk R4 itself was originally about, reached through a different
multiplier (repeated calls instead of one deep one). Even the measurement
test hit this: `MoveLimitCalibrationTest`'s 5×5 variants (which chain the
real `resolveCascade`, unlike production) needed the test JVM's heap
raised to 3g (`shared/build.gradle.kts`) and a much smaller trial count
(25, not 500) to run at all.

The shipped fix instead credits a fixed, empirically-derived constant per
target beyond the nearest one — zero extra BFS calls, same generation-time
cost as the original single-target refinement. Because BFS at 5×5 is
costlier and `scrambleMoves` runs longer by default (6 vs 5), a single
constant tuned only against 4×4 data (a rounded-up ~3 moves/extra target)
under-covered 5×5: re-measured with 25-trial 5×5 batches, it left 1/25
levels (4%) in both languages still exceeding their move limit under
optimal play. `additionalTargetMovesEstimate(size)` (`LevelGenerator.kt`)
now returns 4/target at size ≥ 5 and 3/target below that, closing it back
to 0/25 in both languages at 5×5 while keeping 4×4 at 0/500. Re-measured
`moveLimit` now averages ~11.0-11.1 (4×4) and ~13.0 (5×5) — both land in
or near `GAME_DESIGN`'s intended ~10-12 range, and
`exceedsMoveLimitCount == 0` is asserted as a hard regression guard in
`MoveLimitCalibrationTest`, per grid size, not just an aggregate figure.

> **Known limitation — 5×5 trial count is 25, not this project's usual
> 300-3000.** `0/25` does not carry the same statistical confidence as
> this project's other hard-zero guards; a real failure rate of a few
> percent could still produce a clean 0/25 result. **Open question, not
> yet resolved:** whether 25 is a genuine ceiling under the current OOM
> constraint (3g test-JVM heap) or whether a larger count is achievable
> with further heap increases, less retained state per trial, or
> batching/GC between trials — and whether this limitation is tracked in
> `TESTING_GAPS.md`. Do not treat this as settled until confirmed.

A second, independent bug surfaced while re-measuring 5×5: running the
full suite immediately after the 5×5 fix above, the 4×4 Turkish variant
(not the 5×5 tests being changed) flaked to `exceedsMoveLimitCount = 1/500`
on the SAME seeds that had, moments earlier in an isolated run, measured
exactly 0/500 — i.e. the "deterministic, same seed" measurement wasn't
actually deterministic. Root cause: `resolveCascade`'s random-sampling
phase (`Cascade.kt`) chose its candidate refill letters via
`candidates.random()` — the bare stdlib extension, which draws from
`Random.Default` — instead of the seeded `rng` every caller already
threads through `refillLetter`. Two calls given "the same seed" could
therefore still resolve differently between runs, because the sampling
order behind that seed was never actually tied to it. `resolveCascade` now
takes an explicit `rng: Random = Random.Default` parameter and uses it for
that draw; every call site that already had a seeded `rng` in scope
(`GameViewModel`, `CascadeTest`, `CascadeIntersectionGuaranteeTest`,
`MoveLimitCalibrationTest`) now passes it through. Re-ran the
previously-flaky test twice more after the fix with identical seeds:
byte-identical results both times. This was an existing bug independent
of the `moveLimit` calibration work — it just happened to be caught by
this investigation's regression test being sensitive enough to notice
non-reproducibility that earlier, coarser-grained tests (which only
assert hard `== 0` on structural properties like "no unconfirmed
arrangement," not on move counts) weren't positioned to catch.

**Honest tradeoff — `minMovesIsExact` is now essentially always `false`
for real (multi-target) levels.** The credited per-target estimate is not
a BFS-proven optimum for completing the whole level, so `generateLevel`
now only reports `minMovesIsExact = true` when there's nothing to estimate
(a single-target level). This is a correction, not a new regression: the
previous value was already claiming an "exact"/"optimal" result it had
never actually validated against the real win condition — it was exact
for reaching one word, then silently presented as if it described
completing the level. `EfficiencyFeedback.kt`'s own doc comment already
warns against asserting a precision the game can't back up; this fix makes
the code live up to that for the case it was previously getting wrong, at
the cost of the "Optimal: N moves" message no longer appearing for
ordinary 3-word levels. `StarRating`'s 3★ threshold
(`movesUsed <= minMovesToSolve`) is unaffected by the exactness flag and
becomes more fairly achievable now that `minMovesToSolve` reflects
completing all 3 targets instead of just the nearest one.

**Addendum — levels-41-50 difficulty tier (5×5, 4 target words) confirmed against this rule, not
a new exception to it:** the difficulty-tiering pass (`GAME_DESIGN.md` §5) added a 4-target-word
combo that hadn't been generated/measured before. Measuring it (`LevelPackGeneratorReportTool`)
found `minMovesIsExact = false` for all 10/10 generated levels in both languages. This is not a
new finding or a regression — `additionalTargets == 0` in `generateLevel` above already guarantees
`minMovesIsExact` can only ever be `true` for a single-target level, so *any* level with 2+
targets (this project's entire pack, every tier including the existing 1-30/31-40 ones) was
already `minMovesIsExact = false` by construction before this tier existed. The 41-50 tier simply
confirms the same already-established rule holds at a target count (4) that hadn't been exercised
before, with no new failure mode: `moveLimit` for this tier is still the same safe, never-unfairly-
strict structural upper bound (`GAME_DESIGN.md`'s star-rating doc), and `MoveLimitCalibrationTest`'s
dedicated 5×5/4-word variants confirm `exceedsMoveLimitCount == 0/25` in both languages under
simulated optimal play.

**Addendum — production OOM crash during hint requests (real-device
manual playtesting):** `OutOfMemoryError` observed on a physical device
(Samsung SM-A115F) inside a routine `requestHint()` call
(`bfsMinMovesToAnyTarget` → `findMatchedWords` → `Grid.colsAsStrings`,
crashing on an ordinary `joinToString` allocation), with the heap already
at `target footprint 201326592` (~192MB) and <1% free — meaning the
adversarial BFS call was not itself unusually large, it was the tipping
point of an already-exhausted heap. Two distinct, real problems were
found and fixed:

1. **BFS's memory footprint was never validated on real mobile
   hardware**, only for time on desktop JVM (the timing table earlier in
   this section). Each visited node stored a full live `Grid` object plus
   a growing `List<Move>` path copy; at `BFS_HARD_DEPTH_CAP=5` with
   branching 16-20, the genuinely-unreachable-target case (which this
   section already documents can legitimately occur mid-play) explores up
   to ~1M-3.2M states. Fixed (`Solver.kt`): replaced per-node
   `Grid`+growing-path storage with a compact `letterKey()` (no per-cell
   ids) plus parent-pointer path reconstruction, done once, only for the
   winning state.
2. **BFS had zero cancellation checkpoints** — an abandoned computation
   (player navigates away, or a newer hint request supersedes an older
   one) kept running to full completion regardless, directly matching the
   `Cancelling` coroutine state visible in the crash's stack trace. Fixed:
   added an `isActive` check on each loop iteration, wired from
   `requestHint()`'s coroutine cancellation state.

**Verification, on the same physical device that produced the original
crash** (not just the emulator — an initial emulator-only adversarial
test showed a misleading ~338MB spike at cycle 10 that did not reproduce
on real hardware with dense per-cycle sampling, and is treated as an
artifact of the x86_64 emulator's GC/ART behavior, not a property of the
algorithm): 30 consecutive adversarial hint cycles (each forcing a full
depth-5 exhaustive search via a guaranteed-unreachable target), sampled
every single cycle via `dumpsys meminfo`:

| Metric | Result |
|---|---|
| TOTAL PSS range across 30 cycles | 116.4–121.5 MB (tight ~5MB band) |
| Java Heap PSS range | 31.5–37.0 KB→MB range, no drift |
| First-10 vs. last-10 cycle average (TOTAL PSS) | ~3% difference — within noise |
| Crashes / FATAL EXCEPTION / OOM in logcat | 0/30 |
| Peak observed, vs. original crash's ~192MB threshold | ~121MB peak — well below the original failure point |

No monotonic growth across cycles, no crash, and peak memory stayed
comfortably below the threshold that caused the original failure. This is
the test that most directly matters — same hardware, same failure mode,
sustained repetition — and it closes P0.

---

### R5 — Adversarial / Impossible Target Word Sets

**Risk:** What happens when level design (or a bug) requests more target
words than the grid has rows/columns, or a word set with no viable
placement?

**Validated result:** the generator returns a clean failure (`null`/`None`)
within ~0.17 seconds — no crash, no hang, no partial/corrupt level object.
Confirmed for both "too many words for grid size" and "zero shared letters
between words" (the latter still succeeds, since non-intersecting placement
is a valid fallback — only genuinely impossible inputs fail, and they fail
safely).

**Carries into Kotlin as:** level generation should be treated as a
fallible operation (returns `Level?` / a `Result` type) at the API level,
never assumed to always succeed — this should be reflected in the Kotlin
function signature from the start, not retrofitted later.

---

### R6 — Dictionary Scale Performance

**Risk:** All prior testing used a ~27-word sample dictionary. Real Turkish
dictionary import would be orders of magnitude larger; unclear whether
lookup or generation performance would degrade.

**Validated result (Python, reproducible directly from
`word_shift_prototype_v2.py`):** simulated at 3,000 words, 30 full level
generations completed in ~49ms total (~1.7ms/level) — dictionary size was
not a measurable bottleneck, because target-word lookups are small hash-set
operations independent of total dictionary size (see `ARCHITECTURE.md`,
§4).

> **Correction:** an earlier version of this document cited a "64ms /
> 2.1ms per level" figure for this test that was measured in an ad-hoc
> shell command during development but never committed into the prototype
> file itself — breaking this document's stated promise that every claim
> is reproducible by running the file. The R6 test block has since been
> added directly to `word_shift_prototype_v2.py`; the numbers above are
> what that committed block actually produces.

**Validated result (Kotlin port):** re-measured independently against the
ported implementation — 3,000 synthetic words validated in ~13–16ms, 30
`generateLevel` calls in ~44–49ms (~1.5ms/level). Consistent with the
Python baseline. Also structurally guaranteed, not just empirically fast:
`findMatchedWords` only ever checks the small per-level target set, never
the full dictionary (`ARCHITECTURE.md` §4), so this result cannot regress
as the dictionary grows.

**Conclusion:** no architectural change needed here; flagged as validated
rather than "not a risk," since it was measured (twice, independently, in
both languages) rather than assumed.

## Summary Table

| # | Risk | Outcome |
|---|---|---|
| R1 | Turkish character/dictionary integrity | Fixed — explicit maps + import validator; Kotlin port confirmed locale-independent (full codebase grep, no default-locale APIs used) |
| R2 | Non-intersecting word placement | **Fully resolved in Phase 2** — shuffled-ordering + best-scoring layout raised intersection rate from ~15–20% to 47–65% depending on scenario |
| R3 | Cascade/chain-reaction untested | Implemented + tested, incl. simultaneous multi-word match (hand-constructed deterministic test, not RNG-dependent) |
| R4 | BFS solver performance | **Confirmed crash, fixed** — structural guarantee + hard depth cap, clamp verified un-bypassable via explicit test. Addendum: an R2/R3 interaction (cascade refill breaking an intersecting, still-remaining target) reproduced at 77.6% in real-device playtesting — fully closed to 0/3000 via a scoped exhaustive search, not a soft-guarded residual |
| R5 | Adversarial/impossible inputs | Confirmed safe, controlled failure |
| R6 | Dictionary scale performance | Confirmed safe at 3,000-word scale in both Python and Kotlin independently |

## What Is Explicitly *Not* Covered Here

Game-feel tuning (move-limit buffer size, filler-letter frequency weighting,
scoring/combo multipliers, difficulty pacing across levels) is intentionally
left as an implementation/playtesting concern, not an algorithmic
correctness risk. These are covered as open design questions in
`GAME_DESIGN.md` rather than in this document.
