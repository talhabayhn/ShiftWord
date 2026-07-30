# Testing Gaps — Known, Documented, Not Yet Closed

> **Note on this file's provenance:** this canonical copy was reconstructed
> from a summary of the actual `TESTING_GAPS.md` committed to the repo
> during the cross-subsystem audit (Phase 9/10), not from the file's
> literal text — the literal text wasn't shared in the conversation this
> copy was built from. If the real file differs in wording, the repo's
> version is the source of truth; treat this as a faithful-to-content but
> not necessarily word-for-word reconstruction.

Explicit gaps identified against `ALGORITHM_VALIDATION.md` and beyond,
captured durably rather than left to be forgotten. None of these are
believed to be active bugs — they are absence of coverage, flagged so a
future change in these areas isn't unknowingly shipped without a
regression test.

1. **No Compose UI test infrastructure exists at all** (no `androidTest`,
   no semantics tests) — `AppNavHost`, `GameScreen`, `MainMenuScreen`,
   `SettingsScreen`, `GridBoard` are entirely unverified by automation,
   only by manual/on-device inspection. This is exactly where the
   language/filler-pool bug lived, and is a standing risk for similar
   wiring mistakes.
2. **`GridBoard`'s drag-gesture → `Move` conversion is untested** — axis
   selection, direction sign, and step-rounding logic have no coverage;
   only the resulting `Move`/`Grid.apply` are tested.
3. **No English-equivalent of `TurkishTest.kt`** — case-folding/round-trip
   edge cases for the English `LanguageProfile` aren't directly
   unit-tested (only indirectly via the full EN dictionary validation).
4. **`LanguageProfiles.forCode` mapping has no dedicated test.**
5. **`SettingsRepository` has zero tests** — the read-then-INSERT OR
   REPLACE pattern built specifically to avoid one setting clobbering the
   other (sound vs. language) has no regression test proving that
   invariant.
6. **`Word.sq`'s TR+EN coexistence `(word, language)` key is untested** —
   no test imports both languages into the same DB and confirms no
   collision/cross-contamination.
7. **Hint's on-device worst-case BFS latency is unmeasured** — the fix for
   4a (moving the BFS off `Dispatchers.Main`) is unit-tested for
   correctness, but the actual worst-case wall-clock latency a player
   would feel on a real device hasn't been measured post-fix.
8. **5x5 `MoveLimitCalibrationTest` runs at only 25 trials (vs. this
   project's usual 300-3000)** due to OOM constraints even with a 3g
   test-JVM heap (`shared/build.gradle.kts`) — see the R4 addendum in
   `ALGORITHM_VALIDATION.md`. A real failure rate of a few percent could
   still pass a 0/25 result undetected. Revisit if a lower-memory
   simulation approach is found later.
