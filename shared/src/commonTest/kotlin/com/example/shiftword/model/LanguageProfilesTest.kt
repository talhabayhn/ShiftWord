package com.example.shiftword.model

import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Closes a TESTING_GAPS.md item: LanguageProfiles.forCode's mapping (used by
 * DictionaryRepository, AppNavHost, and GameViewModel's fillerPool selection) was previously
 * only exercised transitively through those call sites, never directly.
 */
class LanguageProfilesTest {

    @Test
    fun turkishCodeResolvesToTheTurkishProfile() {
        assertSame(Turkish, LanguageProfiles.forCode("tr"))
    }

    @Test
    fun englishCodeResolvesToTheEnglishProfile() {
        assertSame(English, LanguageProfiles.forCode("en"))
    }

    @Test
    fun anUnrecognizedCodeFallsBackToTurkishRatherThanThrowing() {
        // Turkish is the app's original, still-default language -- an unrecognized/corrupted
        // persisted value (e.g. from a future language that got removed, or bad data) should
        // degrade to the known-safe default, not crash.
        assertSame(Turkish, LanguageProfiles.forCode("unknown-language-code"))
        assertSame(Turkish, LanguageProfiles.forCode(""))
    }
}
