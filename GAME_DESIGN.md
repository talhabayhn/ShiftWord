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

## 9a. Drag-Time Assist Features (added post-launch)

**Ghost preview — default-on, not a difficulty lever.** During a row/column drag, the letter
that will wrap in from the opposite edge on release now fades into view early (alpha tied to
drag progress over the first cell-width of travel, `GridBoard.kt`), instead of only appearing
once the drag completes and the grid re-renders. This is deliberately always-on: it reveals no
information the wrap-around mechanic doesn't already make computable by the player themself (the
wrapping letter is always the one currently at the opposite end of the row/column being dragged)
— it only removes an artificial "pop-in" lag, matching the graphicsLayer-based animation approach
already established (`perf/graphicslayer-animation-jank`). Because only one shift is ever
committed per gesture regardless of drag distance (`Move` carries no magnitude), only the single
upcoming wrap letter is shown, not a simulation of further wraps beyond it.

**Win highlight — opt-in, off by default.** A separate, explicit Settings toggle
("Kazanan Hamle Vurgusu" / "Winning Move Highlight") that, while dragging, highlights the
row/column being dragged (a color shift, `LavenderTileTint`/`DustyLavender` -> `SageGreen`) if
releasing right now would complete a target word. Unlike the ghost preview, this genuinely
reduces the game's core spatial-reasoning challenge — it tells the player "stop here" instead of
requiring them to read the letters and judge it themselves — so it is treated as a real
accessibility/assist option, not a default. Persisted via `SettingsRepository`
(`winHighlightEnabled`, defaults to `false`), same read-then-`INSERT OR REPLACE` pattern as
sound/language.

## 9b. Score System (added post-launch, independent of stars)

A second, continuous scoring signal alongside (not replacing) the existing discrete star rating
(`StarRating.kt`, unchanged). Where stars are based on total moves used vs. the level's move
limit, score is based on the specific move count at which *each* target word individually
completed:

```
pointsForWord(moveAtCompletion, moveLimit) = round(100 * (1 - moveAtCompletion / moveLimit)^2)
```

clamped so the ratio never leaves `[0, 1]` (a word found exactly at the move limit scores 0, not
negative; a word found before move 0 — not reachable in practice — would score the full 100).
The quadratic term rewards early completion more steeply than a linear formula would, so finding
a word well ahead of the limit is worth disproportionately more than one found just barely in
time. Total level score is the sum across all target words. This is order-independent by
construction: the formula only depends on the move count a word completed at, never on which
target it was Nth to complete, consistent with this project's existing order-invariance
guarantees (`ALGORITHM_VALIDATION.md` R4 addendum, `CascadeIntersectionGuaranteeTest`). Shown on
the Level Complete screen alongside the star rating and efficiency message, not replacing either.

## 9c. Hint Economy (added post-launch)

Hints are no longer unlimited. A global (not per-level, not per-language) credit pool, starting
at 3, persisted via `SettingsRepository` (`hintCredits`). Spending credits in one level or
starting a new level does not refill them — only a genuine cold start (app process restart) does,
wired once at `AppNavHost`'s initial composition, deliberately not on every menu visit or level
transition. This is an intentionally blunt first pass: no in-app-purchase path to buy more
credits exists yet (`GameScreen`'s exhausted-state has a stubbed `onExhausted` hook and a TODO for
that future phase), and the reset trigger is process-restart rather than a real daily/timed
refill, both left as deliberately deferred scope rather than half-built.

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
