# Sound Sourcing

Lighter-weight than `DICTIONARY_SOURCING.md` — these files aren't redistributed from a
third-party repo with its own license terms to verify, so there's no license-clearing pipeline
to document. This just records what's actually known about where they came from.

## Files

Located at `sounds/` (project root). Bundled into the app as Compose Multiplatform resources
under `shared/src/commonMain/composeResources/files/`, so both Android and iOS load them through
the same `Res.readBytes(...)` API (see `SoundPlayer.kt`'s doc comment).

| File | Action | Format |
|---|---|---|
| `slide.mp3` | Every shift/move | MPEG layer III, 256 kbps, 32 kHz, joint stereo |
| `succesfull_slide.mp3` | Word match / explosion (cascade step) | MPEG layer III, 256 kbps, 48 kHz, joint stereo |
| `level_complete.mp3` | Level won (`isWon` transitions to true) | MPEG layer III, 256 kbps, 32 kHz, mono |
| `gameover_pastel.mp3` | Level lost (`isLost` transitions to true) | MPEG layer III, 56 kbps, 44.1 kHz, mono |

(`succesfull_slide.mp3`'s spelling is kept as-is — that's the filename as provided, not a typo
introduced here.)

## Provenance

User-provided. A mix of AI-generated audio and free samples downloaded from free sound-effect
websites. No specific source URLs were tracked at the time of download, and no per-file
attribution is available. Unlike `DICTIONARY_SOURCING.md`'s Turkish/English word lists (each
sourced from a specific, license-verified GitHub repo with its `LICENSE` file read directly
before use), these four files have no equivalent chain of custody to verify or cite — this is
disclosed plainly rather than implying a verification pass that didn't happen.

If per-file source/license documentation becomes available later (or these are replaced with
files that have it), update this table rather than treating the current state as settled.

## What replaced

Both platforms previously synthesized shift/cascade-step tones rather than playing real audio:
Android via `ToneGenerator` (DTMF tones for cascade steps, a "prop beep" for shift), iOS via
`AudioServicesPlaySystemSound` (canned system sound clips — a documented lesser-fidelity
approximation, since iOS's `ToneGenerator`-equivalent has no pitch parameter). Neither platform
had any sound at all for level-complete/game-over before this — `SoundEffects` only had
`playShift()`/`playCascadeStep(step)`, no win/loss hook existed to call into.
