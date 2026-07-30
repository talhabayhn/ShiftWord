# Implementation Roadmap

This roadmap sequences the port of validated logic (see
`ALGORITHM_VALIDATION.md`) from the Python prototype into the Kotlin
Multiplatform codebase described in `ARCHITECTURE.md`. Each phase is scoped
to be independently testable on the JVM before moving to the next.

## Phase 0 — Project Scaffold
- Initialize KMP project (`shared`, `composeApp`, `iosApp` modules)
- Wire up SQLDelight in `shared`
- Confirm a trivial shared function is callable from both an Android and iOS
  target (smoke test for the multiplatform setup itself, before any game
  logic exists)

## Phase 1 — Domain Core (highest priority: 1:1 port from prototype)
Direct Kotlin port of `word_shift_prototype_v2.py`, preserving behavior
exactly, with JVM unit tests mirroring the prototype's test cases:
- `Cell`, `Grid`, `Move`, `Axis` data model
- Grid shift engine (`Grid.apply(move)`)
- Word detection (`findMatchedWords`)
- Explosion + cascade (`explodeAndRefill`, `resolveCascade`, with the
  `maxChainSteps` safety cap carried over unchanged)
- BFS solver **with the hard depth cap (5) carried over unchanged** — do
  not re-tune this without re-running the timing validation from
  `ALGORITHM_VALIDATION.md` R4 first
- **Level generator (`buildCrosswordLayout`, `generateSolvedGrid`,
  `scramble`, `generateLevel`)** — originally scoped for Phase 2, pulled
  forward into Phase 1 because the required test list ("level generation
  success rate," "adversarial impossible input") can't be written without
  it. Phase 2 is scaled back accordingly — see below.

**Exit criteria:** all behaviors validated in the Python prototype (normal
generation, multi-word simultaneous match, adversarial impossible input,
BFS depth-cap fallback) have an equivalent passing JVM unit test in Kotlin.

## Phase 2 — Level Generator Hardening
Since the generator's first implementation moved into Phase 1, this phase
is now refinement rather than first implementation:
- Expand test coverage beyond the Phase 1 baseline (larger trial counts,
  5×5 grid scenarios, dictionary-scale performance re-check per R6)
- Revisit crossword-intersection rate (R2) with a larger real dictionary
  once available, and tune if the intersection rate seen in the prototype
  doesn't hold up
- Any generator behavior differences found between the Python prototype and
  the Kotlin port get resolved here before persistence work begins

**Exit criteria:** generator produces valid, solvable levels at the same
success rate observed in the prototype; adversarial inputs fail cleanly
(`Level?` / `Result`, never a crash).

## Phase 3 — Persistence
- SQLDelight schema for dictionary, levels, progress (per `ARCHITECTURE.md`
  §9)
- Dictionary import pipeline **with the R1 validator wired in as a hard
  gate** — no word reaches the table without passing it
- Seed a real (curated, not raw-dump) Turkish word list

## Phase 4 — UI + Animation (Compose Multiplatform)
- Grid rendering with stable `Cell.id`-based keys
- Drag-to-shift gesture handling with elastic/snap behavior
- Explosion animation (scale/fade) + gravity-drop animation (spring)
- Wire UI to `GameEngine` via `ViewModel` + `StateFlow`, per the state flow
  in `ARCHITECTURE.md` §8

## Phase 5 — Game Loop Features
- Move counter / move-limit enforcement, win/loss states
- Star rating, scoring, combo feedback (finalize open design questions from
  `GAME_DESIGN.md` §6 here, informed by early playtesting)
- Hint system, reusing the Phase 1 BFS solver

## Phase 6 — Polish & Content
- Level pack authoring/curation workflow
- Sound design
- Accessibility pass (colorblind-safe match feedback)
- Daily puzzle mode (if pursued — see `GAME_DESIGN.md` §8)

## Phase 7 — Navigation & Screens *(added: discovered during the design
   application pass, not originally scoped)*

Phase 4 built gameplay directly as the app's only screen — there is
currently no navigation system, no main menu, no separate level-complete
screen. The finalized visual design includes mockups for a main menu and
splash screen that have no corresponding real screen to apply them to
(see `ARCHITECTURE.md` §7a). This phase closes that gap:

- Add a navigation framework (whatever this project's CMP setup
  idiomatically supports — check before choosing) with at least: splash
  → main menu → gameplay flow
- Build a real Main Menu screen per `main_menu.png`, wired to actual data
  (words-found count, day streak) rather than the mockup's placeholder
  numbers
- Build a real Splash Screen per `splash_screen.png` as the app's launch
  screen
- Decide whether Level Complete becomes a dedicated navigable screen or
  remains an inline `GameScreen` state block (current implementation) —
  this is a real design decision, not just a refactor; consider how
  "Replay" / "Next Level" navigation should behave in each case before
  choosing
- Settings screen: currently referenced by the main menu mockup's
  "Ayarlar" button with no defined content — scope this minimally (sound
  toggle, at minimum) rather than leaving it as a dead button

---

**Sequencing rationale:** Phases 1–2 are pure Kotlin, testable without any
UI or device, and carry the highest-risk logic (per the validation report).
They are deliberately front-loaded so that if any subtle behavior
difference between Python and Kotlin surfaces during the port, it's caught
before UI work depends on it.
