# Game Design Document — Word Shift / Kelime Kaydırma

## 1. Core Mechanic

The playing field is an `N × N` grid of letters (`N = 4` for early levels,
`N = 5` for advanced levels). The player drags a finger across any row or
column to shift it cyclically by one or more cells:

- Shifting a row **right**: the last cell wraps to the front.
- Shifting a row **left**: the first cell wraps to the back.
- Shifting a column **down/up** behaves symmetrically.

This is mechanically identical to turning a face of a Rubik's cube, applied
to a flat grid instead of a 3D solid. It is the single mechanical hook that
differentiates this game from every tile-swap word game on the market.

## 2. Word Detection

After every completed shift, **all rows and all columns** are checked
against the level's target word list (not the entire dictionary — see
`ARCHITECTURE.md` for why this scoping matters for both gameplay pacing and
performance). A shift can validly trigger:

- Zero matches (nothing happens, move is simply spent)
- One match (single word explosion)
- Two or more simultaneous matches (a single shift completing both a row and
  a column at once) — **confirmed to occur and handled correctly** during
  prototype validation; this is a designed "big play" moment, not an edge
  case to suppress.

## 3. Explosion + Cascade (Candy Crush Loop)

1. Matched cells flash/scale-pulse, then clear.
2. Remaining letters in each affected column fall downward (gravity).
3. Newly opened cells at the top are refilled with new letters.
4. The grid is re-checked for new matches created by the refill.
5. Repeat until no new matches are found (a hard safety cap on chain length
   exists at the engine level to guarantee termination — see
   `ALGORITHM_VALIDATION.md`, Risk R3).

Each cascade step should feel like an escalating "combo" — increasing
score multiplier per chain step is a natural fit here (not yet finalized,
see open design questions below).

## 4. Level Structure

- Each level defines: grid size, 2–3 target words, and a **move limit**.
- The move limit is derived from the *minimum number of shifts required to
  solve the level*, plus a small buffer (default: +3), so that a skilled
  player has room to make mistakes but cannot brute-force an oversized grid
  with unlimited random shifting.
- A level is won when all target words have been found within the move
  limit; lost (or downgraded to fewer stars) if the limit is exhausted first.

## 5. Difficulty Progression

| Lever | Early levels | Later levels |
|---|---|---|
| Grid size | 4×4 | 5×5 |
| Target word count | 2 | 3+ |
| Move limit tightness | Generous buffer | Buffer shrinks toward optimal |
| Word overlap in grid | Minimal intersection | Dense, crossword-style intersecting placement |
| Letter pool | Common, high-frequency letters | Rarer letters (Ç, Ğ, Ş, Ü) introduced |

## 6. Scoring & Progression

**Star rating — decided in Phase 5** (`game/StarRating.kt`), based on
moves-used vs. move-limit, with `buffer = moveLimit - minMovesToSolve`:

- **3★** — `movesUsed <= minMovesToSolve` (optimal or better)
- **2★** — used at most half the buffer beyond optimal (rounded up, so a
  buffer of 1 still yields a real 2★ band)
- **1★** — anything else that still won within the move limit
- A `buffer == 0` level collapses the 2★ band (only 3★/1★ reachable) —
  accepted as a degenerate case rather than special-cased separately.

**Interaction with R4 (see `ALGORITHM_VALIDATION.md`):** when a level's
`minMovesIsExact` is `false`, `minMovesToSolve` is the structural upper
bound (scramble length) rather than the true minimum — which is always
`>=` the true optimal by construction. This means the 3★ threshold can
never be unfairly strict in the non-exact case; at worst it's more
generous than a fully-optimal threshold would be. This is intentional and
safe, not a bug — the post-level efficiency message (below) is what
carries the "is this actually proven-optimal" distinction to the player,
not the star count.

**Post-level efficiency feedback — decided in Phase 5**
(`game/EfficiencyFeedback.kt`): shows "Optimal: N moves — you used M" only
when `minMovesIsExact` is true; otherwise "You used M moves" with no
optimality claim, so the game never asserts a precision it can't back up.

**Re-confirmed during the cross-subsystem audit**
(`StarRatingTest.threeStarsCanBeAwardedForNonOptimalPlayWhenMinMovesIsExactIsFalse`):
a player can earn 3★ ("optimal!") while genuinely not having played the
true optimal move count, whenever `minMovesIsExact = false`. The star
count and the efficiency message can therefore send a mixed signal in
that case (neutral text, maximum stars) — explicitly reviewed and left
as-is: the mismatch is minor (stars are still a generous, never-unfair
reward), fixing it would mean either downgrading a legitimate win's stars
or complicating `starsFor`'s contract for a rare fallback case, and the
efficiency message already carries the "is this actually proven-optimal"
distinction correctly. Not revisited unless real playtesting feedback
says otherwise.

**Progress stats are global, not per-language** (decided during the
cross-subsystem audit): `Level.sq`/`Progress.sq` never got a language
column when Phase 9 added English support (only `Word.sq`/`Settings.sq`
did) — words-found and day-streak stats are shared across whichever
language(s) a player has used, not tracked separately per language.
Reviewed explicitly and kept this way: a player's cumulative progress
shouldn't reset or fork just because they switched the language toggle,
and per-language stats aren't something either language's design calls
for. If a future feature genuinely needs per-language progress (e.g. a
"words found in English" stat), that's a new, deliberate schema change,
not a bug fix.

**Still open (not yet decided, not blockers):**
- Score bonus for cascade chain length
- Possible "efficiency" leaderboard (fewest moves used across all players
  for a given level) — would require a shared/online component, which
  conflicts with the offline-first design goal unless made optional

## 7. Power-Ups / Economy (Brainstormed, Not Committed)

- **Letter swap** — replace one letter with a hint-selected alternative
- **Extra move** — +1 to the move limit for the current level
- **Reveal** — briefly highlight the first letter of an unfound target word

These map naturally to a soft-currency economy but should be designed after
core loop feel is validated with real playtesting, not before.

## 8. Additional Feature Ideas (Backlog, Prioritized Loosely)

1. **Daily puzzle mode** — one shared puzzle per day, Wordle-style, for
   social/viral pull. Does not require an online backend if generated
   deterministically from the date as a seed.
2. **Diagonal word detection** — for 5×5+ advanced levels only, as an
   additional difficulty lever (not part of the MVP core loop).
3. **Move-efficiency feedback** — "Optimal solution: 4 moves. You used 6."
   Reuses the BFS solver already validated in the prototype (see
   `ARCHITECTURE.md`, hint system).
4. **Sound design** — mechanical "click" on shift, escalating chime per
   cascade step. Disproportionately important to game feel relative to
   implementation cost.
5. **Accessibility** — match/combo feedback must not rely on color alone
   (shape/brightness cues as well) for colorblind players.

## 9d. Hint Presentation: Animation, Not Text (added post-launch)

Requesting a hint now *shows* the suggested move rather than only describing it. On acceptance,
the suggested row/column briefly, automatically plays the same shift nudge a real finger drag
would (`GridBoard.kt`'s `hintMove` parameter): it slides out toward the suggested direction using
the identical `graphicsLayer`-based `Animatable` real drags use — same lavender/green highlight,
same ghost wrap-tile preview (§9a) — then eases back to rest, without ever calling `onMove` or
touching game state (move count, credits already decremented separately on request acceptance,
grid). The existing text hint (`strings.tryHint`, e.g. "2. satır sola") is kept alongside it, not
replaced — the animation is the primary way a player reads the suggestion now, the text remains a
supplementary/accessibility fallback for anyone who prefers to read it.

Tapping Hint again always replays the animation, even when the newly-computed move happens to be
identical to the previous one (same grid/targets → same BFS result): `GameViewModel.requestHint`
clears `hintMove` to `null` synchronously on every accepted request (before the async BFS result
arrives), so `GridBoard`'s animation trigger — keyed on `hintMove` transitioning from `null` to
non-null — always sees a fresh transition to key off, which plain value-equality on the `Move`
itself would not have produced.

**Concurrent-input handling:** if a real finger drag starts while the hint animation is still
playing, the drag takes over cleanly and immediately — the hint's animation coroutine is
explicitly cancelled (not merely out-rendered), and the move that eventually commits is exactly
the real drag's, never a blend of the hint's suggested axis/direction and the real one. This is
the same class of concurrent-input hazard this project has hit before (stale hint results racing
manual play, §7's BFS dispatcher fix; overlapping cascade calls, `GameViewModel.isBusy`) and is
covered by an explicit regression test (`GridBoardHintDragInterruptionTest`), not just inline
reasoning, given that history.

## 9e. Level Select & Level Identity (added post-launch)

**Step 4a investigation finding (blocking, resolved):** before this change, levels had no stable
identity. Every level-advance called `generateRandomLevel()` fresh, assigning `id =
Random.nextInt(1, Int.MAX_VALUE)` — a new random puzzle every time, never the same "level 12"
twice. `LevelPackGenerator.kt` (Phase 6) already produced sequentially-numbered level packs, but
was wired only into authoring/curation tooling, never the running app. `Progress.sq` could record
"this random `levelId` scored N stars" but had no concept of level *number* or *sequence* to build
a Level Select screen against, and a `level` row was only ever persisted on completion, so an
unfinished level didn't exist in the database at all.

**Resolution:** ad-hoc procedural generation is replaced entirely by a persisted, per-language
level pack — **50 levels** per language (`LEVEL_PACK_SIZE`), seeded once via the existing,
unmodified `generateLevel`/`generateLevelPack` pipeline (this changes level *identity*, not the
generation algorithm) with a **fixed seed**, not `Random.Default` — the same pack content is
produced on every fresh install/reseed, which actually makes "level 12" a stable, reproducible
*puzzle*, not merely a stable identity pointing at whatever happened to generate first. Both
languages' packs are seeded eagerly at app start (`AppNavHost`), analogous to
`DictionaryRepository.seedIfNeeded()`. 50 was chosen to comfortably cover this document's intended
difficulty curve without over-building for a larger number now — expanding it later is a one-line
constant change, not a redesign.

Endless/ad-hoc procedural play is explicitly **deferred, not deleted** — it could become a future
mode (natural fit for the Daily Puzzle backlog item, §8.1: a deterministic date-seeded puzzle is
the same mechanism as the pack's fixed-seed generation, just seeded differently), but isn't built
now. Nothing here forecloses it architecturally: `generateLevel` itself is completely unchanged.

**Important distinction — two separate scoping decisions, do not conflate them:**
- **Unchanged, from earlier:** `ProgressRepository.totalWordsFound()`/`currentDayStreak()` (the
  Main Menu's aggregate stats) remain **global, unpartitioned by language** — summed across
  whatever the player has completed in either language, exactly as already decided.
- **New, from this change:** level *content* and *per-level* progress (which level a player has
  reached, star rating per level) are scoped by **`(level_id, language)`** — a Turkish level 7 and
  an English level 7 are different puzzles that happen to share a number, and `Level.sq`/
  `Progress.sq` both gained a `language` column and a composite primary key so they can coexist
  (see `ARCHITECTURE.md` §9 and `2.sqm`'s migration note for the full schema change and how
  pre-existing ad-hoc data was handled — kept, tagged `'tr'`, deterministically non-colliding with
  the pack's own IDs but not with each other in the structurally-impossible sense; see that note
  for the precise, bounded residual risk).

**Unlock logic:** every level up to and including the furthest one the player has a completion
record for is unlocked, plus exactly one more (the next level to try) — level 1 is always unlocked
even with zero progress. Pure function (`buildLevelSelectEntries`, `LevelSelectLogicTest`), not
inlined into the screen, given this project's history of getting state-transition logic like this
wrong via untested ad-hoc reasoning (stale hint results, replay bugs).

**Star display source:** read directly from `progress.stars` (already computed by `starsFor` at
completion time and persisted, per §9b) — Level Select never recomputes a rating, only displays
what was recorded.

**Switching language** shows that language's own independent pack and progression — Turkish
level 1-50 and English level 1-50 never merge or convert into each other, per the scoping decision
above.

**Addendum — found on-device (emulator, then confirmed on physical hardware with 3 rows):** the
kept-not-deleted legacy ad-hoc progress row(s) from before this change (see the migration note
above) initially broke `buildLevelSelectEntries`'s "furthest reached" calculation — a legacy row's
huge random `levelId` (e.g. `2034700723`) became the `maxOrNull()` of the progress map and
unlocked the ENTIRE 50-level pack on first install over any pre-existing test/legacy data. Fixed
by bounding that calculation to `1..packSize` before taking the max, so out-of-range legacy keys
are ignored regardless of how many exist or how large they are. Verified against a crafted
pre-pack-model database with three distinct legacy rows migrated on real hardware (SM-A115F), and
covered by `LevelSelectLogicTest`'s single- and multi-legacy-row cases.

## 9. Turkish-Language-Specific Design Notes

- Dictionary must be curated (not raw TDK dump) to exclude words a casual
  player would perceive as "unfair" (archaic, ultra-technical, or offensive
  terms) — see `ARCHITECTURE.md` for the import/validation pipeline this
  curation step feeds into.
- Letter frequency in filler generation should be weighted toward real
  Turkish letter frequency, not uniform random, so the grid *feels* like
  Turkish text even before any word is found.
- Case handling (İ/I, ı/i) is a correctness requirement, not a polish item —
  documented in depth in `ALGORITHM_VALIDATION.md` (Risk R1), because a
  silent mismatch here means a word the player typed correctly in their head
  will never be recognized as complete.
