# Word Shift / Kelime Kaydırma

A Rubik's-cube-inspired word puzzle game where players shift grid rows and
columns to form valid Turkish words, built with Kotlin Multiplatform (KMP)
and Compose Multiplatform (CMP) for iOS and Android from a single codebase.

## Core Concept

A 4×4 or 5×5 grid of letters is manipulated not by tapping/swapping tiles,
but by **shifting entire rows or columns cyclically** (toroidal / wrap-around
shift — the letter pushed off one edge reappears on the opposite edge, exactly
like a face turn on a Rubik's cube). This adds a spatial-reasoning layer on
top of vocabulary knowledge that most word games lack.

When a shift causes a row or column to spell a valid target word, that word
"explodes," the cleared cells are refilled via gravity (Candy Crush–style
cascade), and — if the refill creates a new valid word — the chain continues
automatically.

## Status

**Phases 0–7 complete.** The algorithmic foundation was validated
risk-first, in Python, before any Kotlin was written (see
[`ALGORITHM_VALIDATION.md`](./ALGORITHM_VALIDATION.md) — seven risks
identified and closed, including one confirmed crash caught before it
could reach production). That foundation was then ported to Kotlin
phase-by-phase per [`IMPLEMENTATION_ROADMAP.md`](./IMPLEMENTATION_ROADMAP.md),
with each phase's exit criteria verified — including on-device manual
verification, not just unit tests — before the next phase began.

What exists now: a working KMP/CMP app (Android verified on-device; iOS
targets build) with the full core loop — shift, match, explosion/cascade,
move-limit win/loss, star rating, hint system, sound, a curated
(hand-typed, validator-gated) starter Turkish dictionary, a finalized
visual design applied throughout (see `ARCHITECTURE.md` §7a), and a real
navigation flow (splash → main menu → gameplay ↔ settings, with
persisted stats — see `ARCHITECTURE.md` §7b).

**Status update (Phase 8):** the two dictionary-related items below are
now resolved — see [`DICTIONARY_SOURCING.md`](./DICTIONARY_SOURCING.md).
The curated dictionary is now 1,041 words (Apache 2.0-licensed source,
Zemberek-NLP), and R4's structural-solvability guarantee was re-verified
at real scale (900 generated levels, 100% exact) rather than remaining an
open question.

**Status update (Phase 9/10):** English-language support is now
implemented and verified (1,073-word curated dictionary, MIT-licensed
source — see `DICTIONARY_SOURCING.md`), and a full cross-subsystem audit
was completed following the R2/R3 finding below. That audit caught and
fixed one significant additional gap: **`DictionaryRepository` (with its
R1 validator gate) was never actually wired into the live app** — the
shipped app read word pools directly from in-memory constants, meaning
the validator and persistence layer were exercised only by tests, not by
real gameplay. This is now fixed; the app reads from the DB-backed
repository, confirmed on-device via direct SQLite inspection.

**Open follow-up work:**
- Daily puzzle mode (`GAME_DESIGN.md` §8) remains backlog, not started.
- A regression guard for R2's intersection rate at real dictionary scale
  doesn't exist yet (only a small-pool guard does) — see
  `ALGORITHM_VALIDATION.md` R2.
- See `TESTING_GAPS.md` for a durable list of known automated-test
  coverage gaps (no Compose UI test infrastructure, untested drag-gesture
  math, and others) — none are known active bugs, but they're absence of
  coverage worth closing eventually.

**Post-launch playtesting finding (resolved):** real-device testing
uncovered a serious R2/R3 interaction — cascade refills could silently
break the solvability of a still-remaining, intersecting target word
(reproduced at 77.6% of levels before the fix). This has been **fully
closed to 0/3000**, not downgraded to an accepted residual rate — see the
R4 addendum in `ALGORITHM_VALIDATION.md` for the full three-iteration
closure process. Also fixed in the same pass: a language-independent
orientation lock (portrait-only), confirmation that "Tekrar Oyna" was
never actually broken (it was misdiagnosed due to this same bug making
already-broken levels look unchanged on replay), a Hint-button freeze risk
(BFS now runs off the main dispatcher, button disabled mid-cascade), and a
`GameViewModel` lifecycle leak (navigating away mid-cascade no longer lets
an abandoned level silently complete in the background).

The reference implementation of every core algorithm still lives in
[`word_shift_prototype_v2.py`](./word_shift_prototype_v2.py) — a Python
prototype used purely to validate logic cheaply, not a code artifact meant
to ship. It remains the source of truth for the domain layer's intended
behavior.

## Documents in This Project

| Document | Purpose |
|---|---|
| `README.md` | This file — orientation |
| `GAME_DESIGN.md` | Mechanics, level structure, scoring, UX, feature backlog |
| `ARCHITECTURE.md` | KMP/CMP module layout, data models, algorithms, persistence |
| `ALGORITHM_VALIDATION.md` | Risk log — what was tested, what broke, how it was fixed |
| `IMPLEMENTATION_ROADMAP.md` | Phased plan for porting validated logic into Kotlin |
| `DICTIONARY_SOURCING.md` | Turkish AND English word list sources, licenses, curation pipelines, and real-scale generator re-verification for both |
| `TESTING_GAPS.md` | Known, documented gaps in automated test coverage — not active bugs, absence of coverage |
| `word_shift_prototype_v2.py` | Executable reference implementation of all core algorithms |

## Tech Stack

- **Kotlin Multiplatform (KMP)** — shared domain/data logic (`shared` module)
- **Compose Multiplatform (CMP)** — shared UI + animation layer (`composeApp` module)
- **SQLDelight** — offline embedded database (dictionary, levels, progress)
- **Target platforms** — iOS, Android (single shared codebase)

## Design Principles Carried Into Implementation

1. **Solvability is structural, not searched for.** Levels are generated by
   scrambling a known-solved grid using invertible moves, so every level is
   provably solvable by construction — no exhaustive search is required to
   *guarantee* this (see `ALGORITHM_VALIDATION.md`, Risk R4).
2. **Turkish text correctness is a first-class concern.** All case
   conversion uses explicit character maps, never locale-dependent
   `uppercase()`/`lowercase()`. Dictionary import requires passing a
   validator before any word enters the database.
3. **Every generated level is provably valid before it ships to a player** —
   no accidental extra words, no unreachable states, no unbounded generation
   loops.
