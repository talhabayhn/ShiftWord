package com.example.shiftword.ui

import com.example.shiftword.model.Axis
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Priority-2 UX finding: raw 0-indexed technical phrasing ("Satır 0 geri" / "Row 0 backward")
 * read like debug output rather than a suggestion a casual player intuitively parses. Fixed by
 * switching to 1-indexed ordinal position plus a plain directional word (left/right/up/down)
 * instead of the internal forward/backward convention Move.forward encodes. This locks in the
 * exact requested phrasing so it can't silently regress back to the old technical format.
 */
class UiStringsTest {

    @Test
    fun turkishHintPhrasingIsOneIndexedOrdinalWithIntuitiveDirectionWords() {
        assertEquals("1. satır sağa", TurkishStrings.tryHint(Axis.Row, 0, true))
        assertEquals("1. satır sola", TurkishStrings.tryHint(Axis.Row, 0, false))
        assertEquals("4. sütun aşağı", TurkishStrings.tryHint(Axis.Col, 3, true))
        assertEquals("4. sütun yukarı", TurkishStrings.tryHint(Axis.Col, 3, false))
    }

    @Test
    fun englishHintPhrasingIsOneIndexedWithIntuitiveDirectionWords() {
        assertEquals("Row 1 right", EnglishStrings.tryHint(Axis.Row, 0, true))
        assertEquals("Row 1 left", EnglishStrings.tryHint(Axis.Row, 0, false))
        assertEquals("Col 4 down", EnglishStrings.tryHint(Axis.Col, 3, true))
        assertEquals("Col 4 up", EnglishStrings.tryHint(Axis.Col, 3, false))
    }
}
