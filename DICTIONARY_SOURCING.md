# Dictionary Sourcing — Turkish Word List (Phase 8)

## Source

**Zemberek-NLP**, licensed under the **Apache License 2.0** (verified by
fetching the actual `LICENSE` file content directly, not by trusting
GitHub API metadata, which reported the license ambiguously).

Six other candidate word-list repositories were checked and rejected for
lacking a clear license — Zemberek-NLP was the only source that cleared
this bar.

Inputs used:
- `master-dictionary.dict` — 28,882 lemma entries
- `first-10K` — a frequency-ranked corpus list, used as the sole
  commonness signal for curation (see pipeline below)

## Pipeline

| Stage | Count | What happened |
|---|---|---|
| Raw source | 28,882 | All lemma entries |
| Alphabet/length filter | 6,505 | Kept only 4- and 5-letter words using valid Turkish alphabet characters |
| Function-word exclusion | 6,470 | Removed 35 pure function words |
| Frequency-confirmed only | 1,065 | **Words with no frequency signal in `first-10K` were excluded, not guessed at** — ~5,405 words were dropped here for lack of evidence of commonness, not assumed to be common |
| Content curation | 1,042 | Excluded 23 words that were frequency-confirmed but heavy/violent/offensive-adjacent (e.g. İDAM, TERÖR, KATİL, BOMBA, ŞEHİT) — judged against the game's calm/chill tone (`GAME_DESIGN.md`) |
| Manual review | 1,041 | Removed one inflected form (ADAMI) caught on final manual pass |

**Final: 1,041 words** (373 four-letter + 668 five-letter, 0 duplicates),
replacing the 112-word placeholder (`CURATED_DICTIONARY_SEED_WORDS`).

## Validator Results

All 1,041 words pass `validateDictionaryWord` (Turkish.kt, R1) with **0
rejections** — expected, since the pipeline above already filtered by
alphabet before validation.

To confirm the validator gate is doing real work (not just rubber-stamping
pre-cleaned input), it was also run against genuinely invalid raw-source
words and correctly rejected 6 Ottoman-circumflex words: ÂCİZ, ÂDET, ÂLEM,
AŞARÎ, ALENÎ, AHDÎ — these fail the alphabet check as intended.

## Generator/BFS Re-Verification at Real Scale

This directly answers the open question flagged in `README.md`'s Phase
6/7 status: *"at 112 words, `minMovesIsExact` never fired `false`... may
not hold at real-dictionary scale."*

Tested: 900 generated levels (3 × 300-trial batches) plus 500-trial
intersection checks per grid size, against the full 1,041-word dictionary.

- **100% generation success, every level exact** (`minMovesIsExact` never
  fired `false`) at 4×4, 5×5, and an aggressive `scramble=40` stress test.
  The structural-guarantee finding from `ALGORITHM_VALIDATION.md` R4
  holds at real dictionary scale, not just at 112 words.
- **R2 intersection rate at real scale:** 30.4% (4×4, 373-word pool) and
  42.2% (5×5, 668-word pool) — lower than the small-pool Phase 2 figures
  (53.0%/65.4%), which is the expected effect of a lexically diverse pool
  reducing the odds of letter overlap between 3 randomly sampled targets.
  Still well above the pre-R2-fix ~20% baseline. See
  `ALGORITHM_VALIDATION.md` R2 for the updated figures.

---

# Dictionary Sourcing — English Word List (Phase 9)

## Source

**hermitdave/FrequencyWords**, licensed under the **MIT License**
(Copyright (c) 2016 Hermit Dave) — verified by fetching the actual
`LICENSE` file text directly, the same standard as the Turkish sourcing
above, not just trusting GitHub's API license field.

- Repo: `https://github.com/hermitdave/FrequencyWords`
- File used: `content/2018/en/en_50k.txt` — the 50,000 most frequent
  English tokens (rank = line number) from an OpenSubtitles-derived
  corpus. This source already is a frequency list, unlike Zemberek's
  lemma dictionary which needed a separate frequency file cross-referenced
  against it.

`first20hours/google-10000-english` was checked first and **rejected**
despite being a well-known, MIT-license-tagged-on-GitHub source: its
actual `LICENSE.md` text says *"I do not recommend using this data for
commercial purposes without licensing it from the Linguistic Data
Consortium"* — an ambiguous, non-clear license by this project's own
standard, caught only by reading the real file rather than trusting the
GitHub API's badge.

`dwyl/english-words` (Unlicense / public domain, genuinely clear) was also
considered, but it has no frequency signal at all — just an unranked
~370k-word list — so it couldn't support the same frequency-based
curation approach used for Turkish.

`LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words` (CC-BY-4.0)
was used as a filtering aid during curation (cross-referenced
programmatically to catch profanity), not redistributed.

## Pipeline

| Stage | Count | Description |
|---|---|---|
| Raw frequency list | 50,000 | All tokens in `en_50k.txt` |
| Alphabet + length filter | 10,790 | 4- or 5-letter words, pure alphabetic (no apostrophes/numbers — this also excludes contractions like `don't`) |
| Top-3,000-frequency cutoff | 1,238 | Kept only tokens ranked in the top 3,000 most frequent overall — a real, disclosed frequency cutoff, not a per-word guess at commonness |
| Exclude profanity | 1,227 | 11 words matched against the LDNOOBW bad-words list |
| Exclude heavy/violent/offensive-adjacent words | 1,200 | 27 words manually excluded (see below) |
| Exclude personal/place names | 1,089 | 111 words manually excluded — see "Known limitation" below |
| Exclude contraction/eye-dialect artifacts | 1,073 | 16 words excluded (see below) |

**Final count: 1,073 words** (518 four-letter + 555 five-letter, 0
duplicates).

**Curation — excluded heavy/violent/offensive-adjacent words (27):**
KILL, GUNS, BOMB, JAIL, HELL, DAMN, CRAP, PISS, BURY, DEATH, BLOOD, SHOOT,
CRIME, KNIFE, DEVIL, CURSE, SCREW, NAKED, GRAVE, WOUND, ARMED, SLAVE,
CRUEL, KILLS, DEMON, FLESH, DRUGS

**Curation — excluded contraction fragments / eye-dialect artifacts (16):**
The source tokenizes contractions by stripping the apostrophe as a
separate token from the source corpus itself (confirmed by checking the
raw file: `doesn`, `wasn`, `haven`, etc. appear as their own
frequency-ranked entries, distinct from `doesn't`-with-punctuation which
the alphabet filter already excludes). These aren't real standalone
English words and would confuse players if shipped as target words:
DOESN, HAVEN, WEREN, MUSTN, AREN, DIDN, HADN, HASN, WASN (contraction
fragments) and GOIN, DOIN, GONNA, WANNA, GOTTA, OUTTA, KINDA (informal
eye-dialect spellings).

**Known limitation — personal and place names (111 excluded):** Unlike
Zemberek's POS-tagged Turkish lemma dictionary, this source is raw
subtitle-dialogue frequency data — it inevitably surfaces character names
(JOHN, MARY, DAVID, SARAH, ...) and place names (PARIS, JAPAN, ROME,
VEGAS, ...) at high frequency rank, since dialogue is full of character
names. These aren't general vocabulary words, so 111 were manually
excluded on review. This was a manual pass, not a POS-tagged filter, so
some may remain undetected — a real limitation of this source, disclosed
rather than silently accepted. Conversely, a handful of words that are
common nouns and incidentally also names (GRACE, GRANT, PENNY, CAROL,
TEDDY, MASON, ROBIN, KITTY, SAINT, MADAM, CHUCK, FRANK, MARK, BILL, DUKE,
JUNE) were deliberately kept since they're legitimate standalone
vocabulary words.

## Generator/BFS Verification (English)

Mirrors the Turkish Phase 8 re-verification exactly
(`EnglishDictionaryGeneratorReportTool`): 300-trial batches at 4×4 and
5×5, plus 500-trial intersection checks per grid size, run against the
real 1,073-word English dictionary and `English.fillerPool` (a-z, no
Turkish letters).

- **100% generation success, every level exact** (`minMovesIsExact` never
  fired `false`) at both grid sizes — confirms R4's structural-solvability
  guarantee holds with an entirely different alphabet, not just Turkish's.
- **R2 intersection rate:** 29.4% (4×4, 518-word pool) and 55.2% (5×5,
  555-word pool) — both comfortably above the pre-R2-fix ~20% baseline,
  in the same range as the Turkish real-scale figures.
