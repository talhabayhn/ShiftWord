package com.example.shiftword.tools

import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Level-pack authoring/curation tool (IMPLEMENTATION_ROADMAP.md Phase 6). Run it with:
 *   ./gradlew :shared:testAndroidHostTest --tests "com.example.shiftword.tools.LevelPackGeneratorReportTool" --info
 * The full report prints to stdout (captured in the JUnit XML system-out either way). This is
 * intentionally a runnable test rather than a separate application module/target — it reuses
 * the exact JVM classpath already proven for domain-layer testing, no new build infrastructure
 * needed for what is fundamentally a batch report over existing pure functions.
 */
class LevelPackGeneratorReportTool {

    @Test
    fun generateAndReportLevelPack() {
        val seed = 20260101L
        val fourByFour = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS,
            gridSize = 4,
            wordsPerLevel = 3,
            count = 30,
            scrambleMoves = 5,
            rng = Random(seed),
        )
        val fiveByFive = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS,
            gridSize = 5,
            wordsPerLevel = 3,
            count = 20,
            scrambleMoves = 6,
            rng = Random(seed + 1),
        )

        printReport("4x4", fourByFour)
        printReport("5x5", fiveByFive)

        assertTrue(fourByFour.successCount >= fourByFour.requested * 9 / 10, "4x4 success rate too low: ${fourByFour.successCount}/${fourByFour.requested}")
        assertTrue(fiveByFive.successCount >= fiveByFive.requested * 9 / 10, "5x5 success rate too low: ${fiveByFive.successCount}/${fiveByFive.requested}")

        // Empirical finding worth recording rather than forcing: even scramble=40 (8x the
        // hard BFS depth cap) still resolved exactly every time in this run — with 3 target
        // words crossword-placed as full rows/columns, many different short move sequences
        // can reconstruct one of them, so BFS_HARD_DEPTH_CAP=5 is rarely actually exhausted in
        // practice. See LevelPackGeneratorReportTest for a direct, decoupled test of the
        // nonExactEntries reporting path itself (not dependent on real data ever triggering it).
        val harderBatch = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS,
            gridSize = 4,
            wordsPerLevel = 3,
            count = 30,
            scrambleMoves = 40,
            rng = Random(seed + 2),
        )
        printReport("4x4 (harder scramble=40, empirically still all-exact)", harderBatch)
    }

    private fun printReport(label: String, report: LevelPackReport) {
        println("[level-pack] === $label ===")
        println("[level-pack] requested=${report.requested} generated=${report.successCount} failedAttempts=${report.failedAttempts}")
        println("[level-pack] success rate: ${report.successCount * 100.0 / report.requested}%")
        if (report.nonExactEntries.isEmpty()) {
            println("[level-pack] all ${report.successCount} levels have an exact (BFS-proven) minMovesToSolve")
        } else {
            println("[level-pack] ${report.nonExactEntries.size} level(s) flagged minMovesIsExact=false — review before shipping:")
            for (entry in report.nonExactEntries) {
                println(
                    "[level-pack]   id=${entry.index} targets=${entry.targetWords} " +
                        "moveLimit=${entry.level.moveLimit} (structural upper bound, not BFS-proven optimal)",
                )
            }
        }
        println()
    }
}
