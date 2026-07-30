package com.example.shiftword.tools

import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import com.example.shiftword.domain.generateLevel
import kotlin.random.Random
import kotlin.test.Test

/**
 * Re-verification of level generation / R2 intersection rate / R4 BFS depth-cap behavior
 * against the real, larger (1,041-word) dictionary from Phase 8 — not assumed to match the
 * Phase 6 measurements taken at 112 words, per the explicit ask to actually check. Run:
 *   ./gradlew :shared:testAndroidHostTest --tests "com.example.shiftword.tools.ExpandedDictionaryGeneratorReportTool"
 */
class ExpandedDictionaryGeneratorReportTool {

    @Test
    fun reportAtRealDictionaryScale() {
        val fourLetterWords = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 4 }
        val fiveLetterWords = CURATED_DICTIONARY_SEED_WORDS.filter { it.length == 5 }
        println("[expanded-dict] four-letter pool size: ${fourLetterWords.size}, five-letter pool size: ${fiveLetterWords.size}")

        // Success rate + non-exact flagging at real scale, larger trial count than the
        // standard LevelPackGeneratorReportTool run (30/20) for real statistical confidence.
        val fourByFour = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS,
            gridSize = 4,
            wordsPerLevel = 3,
            count = 300,
            scrambleMoves = 5,
            rng = Random(1),
        )
        val fiveByFive = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS,
            gridSize = 5,
            wordsPerLevel = 3,
            count = 300,
            scrambleMoves = 6,
            rng = Random(2),
        )
        printReport("4x4 (n=300, real dictionary)", fourByFour)
        printReport("5x5 (n=300, real dictionary)", fiveByFive)

        // Harder scramble, still at real dictionary scale.
        val harder = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS,
            gridSize = 4,
            wordsPerLevel = 3,
            count = 300,
            scrambleMoves = 40,
            rng = Random(3),
        )
        printReport("4x4 (n=300, scramble=40, real dictionary)", harder)

        // R2 intersection rate at real dictionary scale.
        reportIntersectionRate("4x4", 4, fourLetterWords, 500, Random(10))
        reportIntersectionRate("5x5", 5, fiveLetterWords, 500, Random(11))
    }

    private fun printReport(label: String, report: LevelPackReport) {
        println("[expanded-dict] === $label ===")
        println("[expanded-dict] requested=${report.requested} generated=${report.successCount} failedAttempts=${report.failedAttempts}")
        println("[expanded-dict] success rate: ${report.successCount * 100.0 / report.requested}%")
        if (report.nonExactEntries.isEmpty()) {
            println("[expanded-dict] all ${report.successCount} levels have an exact (BFS-proven) minMovesToSolve")
        } else {
            println("[expanded-dict] ${report.nonExactEntries.size} level(s) flagged minMovesIsExact=false:")
            for (entry in report.nonExactEntries) {
                println("[expanded-dict]   id=${entry.index} targets=${entry.targetWords} moveLimit=${entry.level.moveLimit}")
            }
        }
    }

    private fun reportIntersectionRate(label: String, size: Int, pool: List<String>, trials: Int, rng: Random) {
        var withIntersection = 0
        var generated = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(3)
            val result = com.example.shiftword.domain.generateSolvedGrid(
                size, targets, com.example.shiftword.domain.DEFAULT_FILLER_POOL, targets.toSet(), rng,
            )
            if (result != null) {
                generated++
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println("[expanded-dict] $label intersection rate (pool=${pool.size} words, $trials trials, $generated generated): $withIntersection/$generated (${if (generated > 0) withIntersection * 100.0 / generated else 0.0}%)")
    }
}
