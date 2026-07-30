# Technical Architecture — Word Shift / Kelime Kaydırma

## 1. Module Structure (Kotlin Multiplatform)

```
WordShift/
├── composeApp/                 # UI layer — Compose Multiplatform
│   ├── commonMain/
│   │   ├── ui/                 # Screens, composables
│   │   ├── navigation/         # Screen navigation
│   │   └── animation/          # Grid animation controllers
│   ├── androidMain/             # Android entry point, platform glue
│   └── iosMain/                 # iOS entry point, platform glue
├── shared/                      # Platform-independent core
│   ├── commonMain/
│   │   ├── domain/              # Game rules, use cases, engine
│   │   ├── data/                # Repositories, DB access, dictionary import
│   │   └── model/                # Grid, Cell, Move, Level, GameState
│   └── sqldelight/               # SQLDelight schema + generated DB API
└── iosApp/                       # Thin Swift entry point only
```

> **Implementation note (as of Phase 1):** the actual repo currently uses the
> Android-Studio-generated module name `androidApp` rather than `composeApp`
> — the rename/split into a dedicated Compose-only module is deliberately
> deferred to Phase 4 (see `IMPLEMENTATION_ROADMAP.md`), not an oversight.
> The SQLDelight schema also lives at the standard
> `shared/src/commonMain/sqldelight/` path (the Gradle-plugin convention)
> rather than the `shared/sqldelight/` shorthand shown above — functionally
> identical, this doc used the shorthand for readability. Update this
> diagram once Phase 4 actually performs the module split.

**Principle:** everything that is not literally a UI concern lives in
`shared/commonMain`, as pure Kotlin with zero platform dependencies. This is
what makes the module unit-testable on the JVM in milliseconds without an
emulator/simulator, and it is the module that receives the ported logic from
`word_shift_prototype_v2.py`.

## 2. Core Data Model

```kotlin
data class Cell(val letter: Char, val id: Long) // stable id survives shifts,
                                                   // required for correct
                                                   // Compose animation diffing

data class Grid(
    val size: Int,
    val cells: List<List<Cell>> // [row][col]
)

sealed interface Axis { data object Row : Axis; data object Col : Axis }

data class Move(
    val axis: Axis,
    val index: Int,
    val forward: Boolean // row: true=right, false=left
                          // col: true=down,  false=up
)

data class Level(
    val id: Int,
    val gridSize: Int,
    val initialCells: List<List<Char>>,
    val targetWords: List<String>,
    val moveLimit: Int,
    val minMovesToSolve: Int,
    val minMovesIsExact: Boolean // false = structural upper bound only,
                                   // see Risk R4 in ALGORITHM_VALIDATION.md
)
```

`Cell.id` is not cosmetic — without a stable identity that travels with the
letter through shifts, explosions, and gravity, Compose's animation system
cannot distinguish "this tile moved" from "this tile was destroyed and a new
one appeared," and cascade animations will visibly stutter or jump.

## 3. Grid Shift Engine (Toroidal Shift)

Pure, immutable transformation — every move returns a new `Grid`:

```kotlin
fun Grid.apply(move: Move): Grid {
    val newCells = cells.map { it.toMutableList() }.toMutableList()
    when (move.axis) {
        Axis.Row -> {
            val row = newCells[move.index]
            newCells[move.index] = if (move.forward)
                (listOf(row.last()) + row.dropLast(1)).toMutableList()
            else
                (row.drop(1) + row.first()).toMutableList()
        }
        Axis.Col -> {
            val col = (0 until size).map { newCells[it][move.index] }
            val shifted = if (move.forward)
                listOf(col.last()) + col.dropLast(1)
            else
                col.drop(1) + col.first()
            shifted.forEachIndexed { r, cell -> newCells[r][move.index] = cell }
        }
    }
    return Grid(size, newCells)
}
```

Immutability is what makes undo/redo free (retain a `List<Grid>` history) and
what makes the BFS solver's visited-state tracking correct (structural
equality on `Grid` must be well-defined — see below).

## 4. Word Detection

Checked against **only the level's target word set**, not the full
dictionary — this is a deliberate scoping decision validated in the
prototype:

```kotlin
fun Grid.allCandidateStrings(): List<String> =
    rowsAsStrings() + colsAsStrings()

fun findMatchedWords(grid: Grid, targets: Set<String>): List<String> =
    grid.allCandidateStrings().filter { it in targets }
```

Scoping to `targets` (rather than the full ~20k+ word dictionary) matters
for two independent reasons:
1. **Gameplay** — a full-dictionary check would explode grid substrings the
   level designer never intended as "the" solution, undermining the designed
   move-limit/difficulty curve.
2. **Performance** — target-set lookup is a tiny hash-set membership check,
   independent of total dictionary size (confirmed at 3,000-word scale in
   the prototype: ~2ms per full level generation, dictionary size was not
   the bottleneck).

## 5. Explosion + Cascade Resolution

```kotlin
fun explodeAndRefill(grid: Grid, cleared: Set<Pair<Int,Int>>, refill: () -> Char): Grid
fun resolveCascade(
    grid: Grid,
    targetsRemaining: Set<String>,
    maxChainSteps: Int = 10 // hard safety cap — see Risk R3
): CascadeResult
```

`maxChainSteps` is a deliberate, tested safety bound. It is not expected to
be hit in normal play; it exists to guarantee termination under any refill
sequence, since refilled letters are randomized and could theoretically
re-trigger matches indefinitely in a pathological case.

## 6. Level Generation (Backward Generation)

This is the highest-risk component in the entire codebase and the one that
received the most validation. Full detail and the specific failures found
during testing are documented in `ALGORITHM_VALIDATION.md` — summary of the
resulting design:

1. **Crossword-style greedy placement** — target words are placed into the
   grid as full rows or full columns, preferring placements that intersect
   already-placed words at matching letters, falling back to a
   non-intersecting row/column when no intersection is possible.
2. **Filler generation + accidental-word rejection** — empty cells are
   filled randomly, then the resulting "solved" grid is checked to ensure
   the filler didn't accidentally spell an *extra* target word; if it did,
   the attempt is discarded and retried.
3. **Scrambling via invertible moves** — the solved grid is scrambled by
   applying `N` random moves. Because every move has a defined inverse,
   **the scrambled grid is solvable in at most `N` moves by construction** —
   this is a structural guarantee, not something that needs to be proven by
   search.
4. **BFS as an optional refinement, not a gate** — a bounded BFS attempts to
   find the *true minimum* move count (for a tighter, better-feeling move
   limit). It runs under a hard depth cap; if it doesn't finish within that
   cap, generation does **not** fail — it falls back to the structural
   upper bound (`N`) as `minMovesToSolve` and flags `minMovesIsExact = false`.
   This split exists because a naive "BFS must succeed" design was tested
   and found to hang for 50+ seconds and exhaust memory under adversarial
   inputs — see Risk R4.

## 7. Solver (BFS) — Dual Purpose

The same bounded-BFS routine used for level-generation refinement doubles as
the foundation for the in-game **hint system** ("show me a valid next
move") and the **post-level efficiency feedback** ("optimal solution: 4
moves, you used 6"). It should be implemented once in `shared/domain` and
reused by both features rather than duplicated.

```kotlin
fun bfsMinMovesToAnyTarget(
    start: Grid,
    targets: Set<String>,
    maxDepth: Int = 5 // hard cap — see Risk R4 for why this specific bound
): SolverResult?
```

## 7a. Design Tokens (extracted from final mockups)

Exact values measured by pixel-sampling and shape-boundary detection
against the finalized Canva mockups (`design/mockups/`), cross-confirmed
across multiple independent occurrences per color — not estimated from
the verbal design brief that preceded them.

| Token | Hex | Notes |
|---|---|---|
| `CreamBackground` | `#FAF7F2` | Inferred from 3 independent cream-button fills (mockup page background itself is transparent PNG) |
| `DustyLavender` | `#C4B8D9` | Primary action fill |
| `SageGreen` | `#A8C4A0` | Secondary action / found-word chips |
| `WarmSand` | `#E7DCC8` | Tile/card border stroke |
| `SoftCoral` | `#E8A598` | Hint button, filled stars |
| `TextPrimary` | `#4A4453` | Consistent across every mockup |
| `LavenderTileTint` | `#E8E2F0` | Highlighted-tile fill (distinct from border) |
| `StarEmpty` | `#D4D0CC` | Unearned-star outline |

**Tiles and the level-complete card are plain white (`#FFFFFF`), not
cream** — cream is reserved for the page background and cream/tertiary
buttons specifically. This deviates from this document's earlier draft
wording ("warm cream base" for tiles) — the mockups are the source of
truth, not the earlier verbal brief.

Shape tokens (measured, not estimated): `TileCornerRadius = 8.dp`,
`CardCornerRadius = 24.dp`, buttons use a true pill/stadium shape
(`RoundedCornerShape(percent = 50)`, not a fixed dp value).

Typography: Comfortaa (headings/UI labels) and Poppins Regular/Medium/
SemiBold/Bold (body + letter tiles), both OFL-licensed, bundled via
Compose Multiplatform's `composeResources` `Font()` API. License files
under `design/fonts_license/`.

> **Gap flagged during this pass, not yet resolved:** `main_menu.png` and
> `splash_screen.png` have no corresponding screen in the codebase —
> there is currently no navigation/menu system; Phase 4 built gameplay
> directly as the app's only screen. The level-complete mockup's content
> exists only as an inline win-state block inside `GameScreen`, not a
> dedicated navigable screen. This is genuinely unbuilt scope, not a
> styling gap — see `IMPLEMENTATION_ROADMAP.md` Phase 7.

## 7b. Navigation & Screens — Implementation Notes (Phase 7)

**Navigation library:** `org.jetbrains.androidx.navigation:navigation-compose`
— the officially JetBrains-published KMP port of Jetpack Navigation
Compose, not a third-party library. Same sourcing pattern already
established for `androidx.lifecycle-viewmodel-compose` since Phase 4.

**Flow:** splash → menu → gameplay (gameplay pops back to menu via a real
"← Menü" button); menu ↔ settings. Splash auto-navigates after 1.2s and is
popped inclusive, so back-navigation can never return to it.

**Level Complete stays an inline `GameScreen` state block, not a separate
route.** Reasoning: its content is 100% derived from the same
`GameViewModel` already on screen (a separate destination would need
awkward nav-argument passing or ViewModel-sharing for no real benefit);
"Replay"/"Next Level" are both just "swap which `Level` this same screen
shows," not navigation concerns; and an inline overlay avoids the
back-stack ambiguity a dedicated route would introduce (what would "back"
from a solved level even pop to?).

**Main menu stats are read from real persisted data, not placeholders:**
`ProgressRepository.totalWordsFound()` (sums target-word counts of
completed levels by joining progress+level, not a separate drift-prone
counter) and `currentDayStreak()` (consecutive days ending at the most
recent completion, counts today before you've played — matches common
streak UX). `GameViewModel` is wired to persistence via an
`onLevelCompleted` callback, not a direct repository dependency, so the
ViewModel stays testable without a database.

**Settings:** a real sound on/off toggle, persisted via a dedicated
settings SQLDelight table + `SettingsRepository` (not session-only).

**Daily Puzzle button** exists per the mockup but is an intentional no-op
— remains backlog per the Phase 6 decision (`GAME_DESIGN.md` §8), not
silently half-wired.

## 8. State Management Flow

```
UI (Compose)
   ↓ drag gesture → committed Move
ViewModel (shared, androidx.lifecycle KMP ViewModel)
   ↓
GameEngine (pure domain logic: applyMove → findMatchedWords → resolveCascade)
   ↓
StateFlow<GameUiState> → UI recomposition → animation triggers
```

`GameEngine` must remain side-effect-free and platform-independent — this is
what allows the entire gameplay loop to be tested on the JVM without
touching Compose or a device/simulator.

## 9. Persistence Layer (SQLDelight)

| Data | Schema sketch |
|---|---|
| Dictionary | `words(word TEXT PRIMARY KEY, length INTEGER)`, indexed on `length` |
| Levels | `levels(id INTEGER PRIMARY KEY, grid_size INTEGER, target_words TEXT, move_limit INTEGER, min_moves INTEGER, min_moves_exact INTEGER)` |
| Progress | `progress(level_id INTEGER, stars INTEGER, best_moves INTEGER, completed_at INTEGER)` |
| Settings | key-value, via `multiplatform-settings` (not SQLDelight — simpler fit) |

> **Implementation note (as of Phase 3):** the sketch above is snake_case;
> the actual `.sq` schemas (`Word.sq`, `Level.sq`, `Progress.sq`) use
> camelCase column names instead (`gridSize`, `targetWords`, `moveLimit`,
> `minMovesToSolve`, `minMovesIsExact`, `levelId`, `bestMoves`,
> `completedAt`) — SQLDelight generates Kotlin data class properties 1:1
> from column names with no auto-camelCasing, so snake_case columns would
> produce awkward `target_words`-style Kotlin identifiers throughout the
> codebase. Two further deviations:
> - `level` gained an `initialCells TEXT AS List<List<Char>> NOT NULL`
>   column, absent from the sketch above. Without it a persisted level
>   can't be reconstructed for replay — target words and a move limit
>   alone don't reproduce the actual starting letter grid.
> - `targetWords`/`initialCells` are typed via SQLDelight's
>   `AS <KotlinType>` column-type syntax with hand-written `ColumnAdapter`s
>   (`StringListAdapter`, `CellGridAdapter` in `data/DbAdapters.kt`) rather
>   than being stored/parsed as raw strings by callers — this keeps
>   repository code working with typed `List<String>` / `List<List<Char>>`
>   directly instead of ad-hoc string-splitting at every call site.
> - `progress`'s upsert uses `INSERT OR REPLACE`, not
>   `ON CONFLICT ... DO UPDATE` — the project's default SQLDelight dialect
>   (SQLite 3.18) doesn't support `ON CONFLICT DO UPDATE`, and since the
>   whole row is replaced anyway (no partial-column update needed),
>   `INSERT OR REPLACE` is the simpler, equally-correct choice.
>
> **`Cell.id` is intentionally NOT persisted.** `initialCells` stores
> `List<List<Char>>` only — letters, no ids. Ids are assigned fresh
> whenever a `Level` is loaded into a live `Grid` for gameplay. This is a
> deliberate design decision, not an omission: `Cell.id` exists solely as
> a runtime animation-diffing concern (§2) for Compose to distinguish "this
> tile moved" from "this tile was replaced" within a single play session —
> it has no meaning across app restarts and storing it would be dead
> weight.

Dictionary is seeded from a bundled asset on first launch. **Every word must
pass the validation pipeline described in `ALGORITHM_VALIDATION.md` (Risk
R1) before being inserted** — this should be enforced at the import script
level, not trusted to be already-clean source data.

## 10. Testing Strategy

- **`shared` module: JVM unit tests.** Grid shift correctness, word
  detection, cascade termination, and level-generator determinism (same
  seed → same level) are all testable without an emulator — this is the
  primary regression safety net and should be built out before UI work
  begins, directly porting the test cases already proven in
  `word_shift_prototype_v2.py`.
- **Generator self-validation as an ongoing CI check** — the BFS
  solver doubles as an automated auditor: any newly generated static level
  set should be run through it (within the bounded depth) as a sanity check
  before shipping, the same way it was used during prototype validation.
- **UI/animation testing** — deferred to manual + snapshot testing once the
  domain layer is stable; not a blocker for starting domain-layer
  development.
