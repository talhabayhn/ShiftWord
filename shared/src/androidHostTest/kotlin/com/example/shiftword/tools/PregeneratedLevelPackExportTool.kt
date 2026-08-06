package com.example.shiftword.tools

import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS
import com.example.shiftword.data.CURATED_DICTIONARY_SEED_WORDS_EN
import com.example.shiftword.model.English
import com.example.shiftword.model.Level
import com.example.shiftword.model.Turkish
import kotlin.random.Random
import kotlin.test.Test

/**
 * One-shot authoring tool (item 7, first-launch freeze fix): dumps the deterministic, fixed-seed
 * 100-level pack (both languages) as literal Kotlin source, to be pasted into
 * `PregeneratedLevelPack.kt` and bundled directly into commonMain. This replaces on-device
 * generation at first launch (which was blocking the main thread for 25-48s, confirmed via
 * real-device profiling -- see GAME_DESIGN.md/README) with a near-instant DB insert of
 * already-known data. Re-run this and re-paste only if DEFAULT_DIFFICULTY_TIERS or the curated
 * dictionaries change -- LevelPackGeneratorTierTest/GeneratorMetricsTest/MoveLimitCalibrationTest
 * already guard that the generation parameters behind this dump stay valid.
 */
class PregeneratedLevelPackExportTool {

    private fun Level.toKotlin(): String {
        val cells = initialCells.joinToString(",") { row -> "\"" + row.joinToString("") + "\"" }
        val words = targetWords.joinToString(",") { "\"$it\"" }
        return "L($id,$gridSize,listOf($cells),listOf($words),$moveLimit,$minMovesToSolve,$minMovesIsExact,\"$language\")"
    }

    @Test
    fun exportBothLanguagePacks() {
        val seed = 20260101L
        val trReport = generateTieredLevelPack(CURATED_DICTIONARY_SEED_WORDS, DEFAULT_DIFFICULTY_TIERS, Random(seed), language = Turkish.code)
        val enReport = generateTieredLevelPack(CURATED_DICTIONARY_SEED_WORDS_EN, DEFAULT_DIFFICULTY_TIERS, Random(seed), language = English.code)

        println("=====PREGEN_TR_START=====")
        trReport.entries.sortedBy { it.index }.forEach { println(it.level.toKotlin()) }
        println("=====PREGEN_TR_END=====")
        println("=====PREGEN_EN_START=====")
        enReport.entries.sortedBy { it.index }.forEach { println(it.level.toKotlin()) }
        println("=====PREGEN_EN_END=====")
    }
}
