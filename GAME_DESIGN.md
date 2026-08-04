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

**Wired into pack generation as of the difficulty-tiering pass** (`LevelPackGenerator.
DEFAULT_DIFFICULTY_TIERS`, `LevelRepository.seedPackIfNeeded`) — before this, every level in the
50-level pack used the same fixed parameters regardless of position; the curve below was design
intent that was never actually connected to the persisted-pack model (Phase 9e). Four tiers, each
escalating grid size, target word count, and move-limit tightness cumulatively:

| Levels | Grid size | Target words | scrambleMoves |
|---|---|---|---|
| 1–10 | 4×4 | 2 | 5 |
| 11–30 | 4×4 | 3 | 5 |
| 31–40 | 5×5 | 3 | 6 |
| 41–50 | 5×5 | 4 | 7 |

**Word overlap density does NOT increase monotonically across tiers — this is a correction to
this section's earlier (aspirational, never-measured) claim that density rises with every tier.**
Measured real-scale intersection rates (`GeneratorMetricsTest`, both curated dictionaries):

| Tier | TR | EN |
|---|---|---|
| 11–30 (4×4, 3 words) | 30.4% | 29.4% |
| 31–40 (5×5, 3 words) | 42.2% | 55.2% |
| 41–50 (5×5, 4 words) | 10.0% | 20.6% |

The 41–50 drop is judged an inherent geometric ceiling — 4 target words competing for a 5×5
grid's 10 rows/columns leaves mathematically less room per word than the 3-word tier, not a
fixable placement-algorithm bug — and was shipped as-is rather than investing in unscoped,
uncertain-payoff pool-selection biasing (see `ALGORITHM_VALIDATION.md`'s R4 addendum and
`GeneratorMetricsTest`'s dedicated 5×5/4-word guards, calibrated to this tier's own lower measured
reality rather than the ~25-45% band the other guards use). Difficulty at 41–50 is still real and
perceptible — it comes from grid size, word count, and move-limit tightness, just not from rising
crossword density on top of those.

**Letter pool** (common → rarer letters, e.g. Ç, Ğ, Ş, Ü) remains a **deferred** lever — no
frequency/rarity-aware pool filtering exists yet; the tiers above scale grid size, word count, and
move-limit tightness only.

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

## 9f. Reduced Motion (added post-launch)

A new Settings toggle (`SettingsRepository.isReducedMotionEnabled`/`setReducedMotionEnabled`,
persisted the same read-then-`INSERT OR REPLACE` way as every other setting — see Settings.sq),
**off by default** so the existing animated experience stays the default for existing users. This
is an accessibility/performance option, not a difficulty lever like Feature 1B's win highlight —
it changes how much motion the player sees, not what information is available to them.

When ON, four animation surfaces get meaningfully less motion (not just faster — see each site's
own doc comment in `GridBoard.kt`/`GameViewModel.kt` for the exact treatment):

- **Ghost-preview fade-in (Feature 1A):** normally fades in over the first cell-width of drag
  travel; with reduced motion, appears at full opacity immediately once a drag starts on that
  axis, skipping the fade entirely.
- **Win-highlight (Feature 1B), if also enabled:** already an instant state-driven color swap
  (background/border flip the moment `wouldWin` becomes true), not a duration-based animation —
  reduced motion has nothing further to do here, it was never "more motion" than a state change
  to begin with.
- **Hint-nudge animation (§7's hint presentation):** the automatic shift-and-back nudge runs at a
  much shorter tween duration (`REDUCED_HINT_NUDGE_DURATION_MS`, ~60ms vs. the normal ~260ms per
  direction) instead of being skipped outright — kept non-zero so the suggested row/column is
  still visibly identified, just briefly, rather than disappearing as a silent instant jump.
- **Explosion/gravity/cascade animation:** the per-cell scale/alpha "pop" (`GridBoard.kt`) and the
  cascade's own felt pause between an explosion and its refill (`GameViewModel.processShiftedGrid`)
  both drop to a short, non-zero duration (~60ms) instead of their normal ~300-350ms. Non-zero
  for the same reason as the hint nudge above: an instant (0ms) transition risks Compose skipping
  the animation callback on some frame timings, and — separately — a player still needs to
  perceive "this got cleared, then this got refilled" as two distinct moments, not one
  indistinguishable flash.
- **Tile snap-back spring (drag release):** normally a bouncy spring
  (`Spring.DampingRatioMediumBouncy`); with reduced motion, a stiff non-bouncy spring instead, so
  a released tile settles into place quickly without any visible overshoot/wobble.

Not touched by this setting: the splash screen's own fixed-duration display (not an animation to
begin with) and screen-to-screen navigation transitions (Navigation Compose's own defaults,
outside this app's animation code).

## 9g. Difficulty-Tiered Pack Regeneration & Progress Reset (added post-launch)

**Finding:** a real-device playtest report that "difficulty doesn't increase across the pack"
led to the investigation documented in §5 above — confirmed that `LevelPackGenerator`/
`LevelRepository.seedPackIfNeeded` had never actually been wired to vary generation parameters by
level number; every one of the 50 levels used the same fixed grid size/word count/scramble
regardless of position. §5's difficulty curve is now wired in via
`LevelPackGenerator.DEFAULT_DIFFICULTY_TIERS`.

**Progress wipe (deliberate, one-time):** regenerating the pack with new per-tier parameters
changes every level's content (grid layout, target words, move limit) except level identity
(number). Existing per-level progress (`progress` table: stars, best-moves) and the derived
global aggregates (`ProgressRepository.totalWordsFound()`/`currentDayStreak()`, both computed
live from `progress` + `level` — see `ARCHITECTURE.md` §9, no separate stored columns to wipe
independently) are wiped alongside the level table itself, migrated via `4.sqm`. **Decision:**
this is pre-launch — there is no real user data to preserve — and a stale star rating recorded
against a puzzle that no longer exists in that form (different layout/target words under the same
level number) would be actively misleading to keep around rather than merely stale. A fresh,
consistent 0-stars/level-1-unlocked start is judged strictly better than reconciling or
preserving orphaned progress against regenerated content. This reasoning is one-time, tied to
this specific regeneration — it does not establish a standing policy of wiping progress on future
content changes; see `4.sqm`'s own comment and `ARCHITECTURE.md` §9 for the migration mechanics.

**Addendum — a third migration in the same category (5.sqm, §9h):** level 1's onboarding-tuned
regeneration (carved out of the original 1-10 tier into its own single-level range with
`scrambleMoves=2` instead of 5 — see §9h) changes level 1's actual puzzle content the same way the
difficulty-tiering pass above changed every level's content: same level *number*, different grid
layout and move limit underneath it. `5.sqm` wipes `level`/`progress` again, for the identical
reason this section already gives — pre-launch, no real user data to preserve, and a kept progress
row would misdescribe regenerated content rather than merely go stale. This is now the **third**
migration in this project's "pre-launch progress reset" pattern:

1. `2.sqm` (Level Select) — the one exception: **kept**, not wiped. Old ad-hoc rows predated the
   pack model entirely and were still valid content, just missing a `language` tag, so tagging
   them `'tr'` was correct rather than destructive.
2. `4.sqm` (difficulty-tiering) — **wiped**. The pack's content changed under the same level
   numbers, so kept progress would misdescribe it.
3. `5.sqm` (level 1's onboarding scramble) — **wiped**, same reasoning as `4.sqm`, applied to a
   single level's content change rather than the whole pack.

The distinction that actually matters, restated for a future reader who might reach for "wipe" or
"keep" without rederiving it: wipe when the level *content* referenced by an existing progress row
changes underneath the same identity; keep (and just tag/backfill) when the row's content is still
valid and only its *schema* changed. Each of these three migrations independently arrived at the
correct side of that distinction — this note exists so the next one doesn't have to rediscover it
from three separate `.sqm` comments.

## 9h. First-Time Onboarding (added post-launch)

A one-time, first-install-only sequence for a brand-new player, built entirely on existing
systems (settings persistence, the hint-nudge animation, the win-highlight setting, the existing
inline win-state block) rather than any new onboarding-specific infrastructure.

**Gate: `hasSeenOnboarding`.** A persisted boolean (`SettingsRepository`, `Settings.sq`/`6.sqm`),
same read-then-`INSERT OR REPLACE` pattern as every other setting, defaulting false. Not
re-triggerable from Settings — there is no "replay onboarding" option, and no setter exists to
flip it back to false once true. Every onboarding surface below is derived from a single pure
function, `isOnboardingLevel(hasSeenOnboarding, levelNumber) = !hasSeenOnboarding && levelNumber
== 1` (`game/OnboardingLogic.kt`) — kept standalone and unit-tested (`OnboardingLogicTest`) for the
same reason `buildLevelSelectEntries` was: this is exactly the kind of state-transition condition
this project has gotten wrong before via untested ad-hoc reasoning, and four independent copies of
it at four call sites could drift apart in a way one shared function can't.

**Level 1's own content — tighter scramble, not a separate generation path.** Investigated before
building anything else (see §9g's addendum above for the migration this required): measured
directly (`MoveLimitCalibrationTest`), the original tier-1 parameters (`scrambleMoves=5`, 2 words,
4×4) required ~4.9 real moves on average (1-9 range, 500 trials) under optimal play —
indistinguishable in difficulty from an ordinary early-game puzzle, not a "close to solved
already" teaching moment for a player who has never performed the drag-to-shift gesture before.
Level 1 was carved out of the original 1-10 tier into its own single-level `DifficultyTier` entry
with `scrambleMoves=2` (`LevelPackGenerator.DEFAULT_DIFFICULTY_TIERS`), measuring ~2.8 moves on
average (1-6 range). Deliberately **not** a separate onboarding-only generation path: level 1 is a
single, fixed-seed, persisted puzzle generated once for the whole pack like every other level, so
there's no need to hedge against a wide real-world distribution the way a per-install-random
tier's average would — and keeping level 1 permanently the easiest level in the pack (onboarding
or not) is a reasonable standing design decision on its own, not a special case that needs
unwinding after a player's first session.

**Step 3 — auto-played hint nudge.** `GameViewModel` gains a `playOnboardingHintOnStart`
constructor flag; when true (`AppNavHost` passes `isOnboardingLevel(...)`), `init{}` calls
`autoPlayOnboardingHint()` — the same `bfsMinMovesToAnyTarget` call and the same `hintMove`
null→non-null transition `GridBoard`'s existing nudge animation (§9d) already keys on, just
triggered automatically instead of by a player tapping Hint. Deliberately does **not** go through
`requestHint()`'s credit-gate/decrement/`onHintUsed()` path — spending a hint credit on something
the player never asked for would be indistinguishable from a real hint they didn't request.
`GameUiState.isOnboardingHint` is set alongside `hintMove` so `GameScreen` can show a short
instructional text bubble (`onboardingSwipeHint`, reusing the exact same bottom-of-board `Text`
slot the normal `tryHint` message already occupies — no new UI surface) instead of the regular
hint text for this one occurrence. Cleared back to false the moment a real move commits or a real
hint is requested, so it can never bleed into a later, genuinely player-requested hint.

**Step 4 — forced win highlight for level 1 only.** `AppNavHost` computes `winHighlightEnabled =
settingsRepository.isWinHighlightEnabled() || isOnboardingLevel(...)` — an in-memory `||` applied
only to the value threaded to `GameScreen`/`GridBoard`; `setWinHighlightEnabled` is never called,
so the player's real persisted preference is untouched and takes over unmodified from level 2
onward (or immediately, if they already had it on).

**Step 5 — contextual hint-button callout.** `GameViewModel` tracks
`movesSinceLastMatch` (reset to 0 whenever a move finds any word, including incidentally via
cascade) and exposes `hintButtonCalloutVisible`, gated by a `hintButtonCalloutEligible` flag (same
`isOnboardingLevel`-derived source as step 3) and a threshold (`hintButtonCalloutThresholdMoves`,
default 3 consecutive no-match moves). A private `hintCalloutShownOnce` guard means it fires at
most once per `GameViewModel` instance regardless of how much further no-match play follows —
"only ever shown once" is enforced structurally, not by a soft re-check. Visible for exactly the
move that crossed the threshold, dismissed by the next move or immediately by an actual `Hint` tap
(`requestHint` explicitly clears it — the player found the button, there's nothing left to point
at). Rendered as the existing `Pill` composable (same tokens as every other chip on the screen)
placed directly above the Hint button — no new visual language, no coordinate-overlay machinery.

**Step 6 — first-win explanation, and the flag flip.** `GameScreen`'s existing inline win-state
block (§7a/§7b — never a separate route) gains one conditional `Text` between the score label and
the action buttons, shown when `isOnboardingLevel` (computed once per `GAMEPLAY` composable entry,
so it stays correctly true for this specific win screen's whole lifetime even after the flag
flips underneath it — see below). The flag itself flips inside the *existing*
`onLevelCompleted` callback `AppNavHost` already wires to `GameViewModel` — no new callback was
added: `if (!settingsRepository.isHasSeenOnboarding()) settingsRepository.setHasSeenOnboarding(true)`,
right after the existing `progressRepository.recordCompletion(...)` call. No explicit level-number
check is needed there: Level Select's unlock logic (§9e) keeps every level but 1 locked until it
has a completion record, so the *first* completion `onLevelCompleted` is ever called for is, by
construction, always level 1's, while `hasSeenOnboarding` is still false.

**Why one shared gate is safe for all four steps.** Because `isOnboardingLevel` is re-derived
fresh (`remember { settingsRepository.isHasSeenOnboarding() }`) each time the `GAMEPLAY`
composable is newly entered, and because `hasSeenOnboarding` only ever transitions false→true (via
step 6, never back), every one of steps 3-5 naturally stops firing forever the moment the player
completes level 1 for the first time — there's no separate "have I shown this specific thing
before" flag needed for the hint nudge or the win-highlight override, only for the callout (which
needs "at most once *within* a single still-onboarding session," a strictly narrower guarantee
`hintCalloutShownOnce` provides locally). Replaying level 1 before ever winning it (e.g. quitting
back to Level Select and re-entering) is expected to re-show steps 3-5 — `hasSeenOnboarding` is
still false, so `isOnboardingLevel` is still true, and there is nothing else it should mean for a
player who hasn't finished onboarding yet.

**Localization and design tokens.** All onboarding text (`onboardingSwipeHint`,
`onboardingHintButtonCallout`, `onboardingWinExplanation`) follows `UiStrings`' existing TR/EN
field-pair convention (`UiStrings.kt`) — independently phrased per language, not a single string
parameterized by a noun. Every onboarding UI element reuses existing composables/tokens verbatim
(`Pill`, `Text` with `MaterialTheme.typography.bodySmall`/`TextPrimary`, the existing hint-text
slot) — no new colors, shapes, or components were introduced for this feature.

**Testing.** `OnboardingLogicTest` covers the pure gate function directly.
`GameViewModelOnboardingTest` drives steps 3 and 5 through the real `GameViewModel` (not a mock):
the auto-played hint fires without consuming a credit or calling `onHintUsed` when the flag is
true and never fires when false; `isOnboardingHint` clears on the next real move or hint request;
the callout fires exactly once at the threshold, never fires when ineligible, and is dismissed by
either the next move or a real hint request; a match resets the no-match streak so the callout
doesn't fire early. `LevelPackGeneratorTierTest` guards the tier-config split itself (level 1's
`scrambleMoves=2`, levels 2-10 unchanged, every level 1-50 covered by exactly one tier).
`SettingsRepositoryTest` covers `hasSeenOnboarding`'s persistence/no-clobber behavior and the
`5.sqm`/`6.sqm` migration path from a real pre-existing v4 database.

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
