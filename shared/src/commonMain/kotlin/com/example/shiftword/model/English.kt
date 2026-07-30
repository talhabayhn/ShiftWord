package com.example.shiftword.model

/**
 * English has no Turkish-I-style locale case-folding hazard, but case conversion is still
 * done via an explicit ASCII map rather than `String.uppercase()`/`.lowercase()` — consistency
 * with [Turkish]'s approach, and it sidesteps relying on any platform's default locale
 * behavior at all (see ALGORITHM_VALIDATION.md Risk R1).
 */
object English : LanguageProfile {
    override val code: String = "en"
    override val alphabetUpper: Set<Char> = ('A'..'Z').toSet()
    override val fillerPool: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val upperMap: Map<Char, Char> = ('a'..'z').zip('A'..'Z').toMap()
    private val lowerMap: Map<Char, Char> = ('A'..'Z').zip('a'..'z').toMap()

    override fun upper(s: String): String = s.map { ch -> upperMap[ch] ?: ch }.joinToString("")
    override fun lower(s: String): String = s.map { ch -> lowerMap[ch] ?: ch }.joinToString("")
}
