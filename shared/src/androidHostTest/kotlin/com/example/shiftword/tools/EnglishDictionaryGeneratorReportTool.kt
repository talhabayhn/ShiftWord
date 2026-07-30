package com.example.shiftword.tools

import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS_EN
import com.example.shiftword.model.English
import kotlin.random.Random
import kotlin.test.Test

/**
 * Phase 9 end-to-end verification for the English word pool -- mirrors
 * ExpandedDictionaryGeneratorReportTool (Phase 8's Turkish real-scale check): does level
 * generation actually work at real dictionary scale in English, and does R4's BFS depth-cap
 * (minMovesIsExact) still never fire false with an entirely different alphabet/filler pool
 * (English.fillerPool has no Turkish letters at all)? Run:
 *   ./gradlew :shared:testAndroidHostTest --tests "com.example.shiftword.tools.EnglishDictionaryGeneratorReportTool"
 */
class EnglishDictionaryGeneratorReportTool {

    @Test
    fun reportForEnglishDictionary() {
        val fourLetterWords = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 4 }
        val fiveLetterWords = CURATED_DICTIONARY_SEED_WORDS_EN.filter { it.length == 5 }
        println("[english-dict] four-letter pool size: ${fourLetterWords.size}, five-letter pool size: ${fiveLetterWords.size}")

        val fourByFour = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS_EN,
            gridSize = 4,
            wordsPerLevel = 3,
            count = 300,
            scrambleMoves = 5,
            rng = Random(1),
            fillerPool = English.fillerPool,
        )
        val fiveByFive = generateLevelPack(
            wordPool = CURATED_DICTIONARY_SEED_WORDS_EN,
            gridSize = 5,
            wordsPerLevel = 3,
            count = 300,
            scrambleMoves = 6,
            rng = Random(2),
            fillerPool = English.fillerPool,
        )
        printReport("4x4 (n=300, English dictionary)", fourByFour)
        printReport("5x5 (n=300, English dictionary)", fiveByFive)

        reportIntersectionRate("4x4", 4, fourLetterWords, 500, Random(10))
        reportIntersectionRate("5x5", 5, fiveLetterWords, 500, Random(11))
    }

    private fun printReport(label: String, report: LevelPackReport) {
        println("[english-dict] === $label ===")
        println("[english-dict] requested=${report.requested} generated=${report.successCount} failedAttempts=${report.failedAttempts}")
        println("[english-dict] success rate: ${report.successCount * 100.0 / report.requested}%")
        if (report.nonExactEntries.isEmpty()) {
            println("[english-dict] all ${report.successCount} levels have an exact (BFS-proven) minMovesToSolve")
        } else {
            println("[english-dict] ${report.nonExactEntries.size} level(s) flagged minMovesIsExact=false:")
            for (entry in report.nonExactEntries) {
                println("[english-dict]   id=${entry.index} targets=${entry.targetWords} moveLimit=${entry.level.moveLimit}")
            }
        }
    }

    private fun reportIntersectionRate(label: String, size: Int, pool: List<String>, trials: Int, rng: Random) {
        var withIntersection = 0
        var generated = 0
        repeat(trials) {
            val targets = pool.shuffled(rng).take(3)
            val result = com.example.shiftword.domain.generateSolvedGrid(
                size, targets, English.fillerPool, targets.toSet(), rng,
            )
            if (result != null) {
                generated++
                val (_, placements) = result
                if (placements.sumOf { it.intersections } > 0) withIntersection++
            }
        }
        println("[english-dict] $label intersection rate (pool=${pool.size} words, $trials trials, $generated generated): $withIntersection/$generated (${if (generated > 0) withIntersection * 100.0 / generated else 0.0}%)")
    }
}
