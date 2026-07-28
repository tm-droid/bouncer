package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the three parseScore defects: validity keyed on a stray separator, the "Ov" strip
 * mangling a spelled-out "Overs", and Australian wickets-first notation.
 */
class ScoreParsingTest {

    // --- overs extraction ---------------------------------------------------

    @Test
    fun `spelled-out Overs is not mangled`() {
        // `.replace("Ov","").replace("ov","")` turned "(12.4 Overs)" into "12.4 ers".
        assertEquals("12.4", extractOvers("199/7 (12.4 Overs)"))
        assertEquals("12.4", extractOvers("199/7 (12.4 Ov)"))
        assertEquals("12.4", extractOvers("199/7 (12.4 ov)"))
        assertEquals("12.4", extractOvers("199/7 (12.4)"))
        assertEquals("20", extractOvers("199/7 (20)"))
    }

    @Test
    fun `overs outside parentheses still parse`() {
        assertEquals("19.5", extractOvers("199/7 19.5 Ov"))
    }

    @Test
    fun `no overs token yields empty`() {
        assertEquals("", extractOvers("199/7"))
        assertEquals("", extractOvers("Not-Started"))
    }

    // --- runs / wickets -----------------------------------------------------

    @Test
    fun `standard slash notation`() {
        assertEquals(199 to 7, parseRunsAndWickets("199/7"))
    }

    @Test
    fun `hyphen notation, runs first`() {
        assertEquals(150 to 5, parseRunsAndWickets("150-5"))
    }

    @Test
    fun `Australian wickets-first notation is read correctly`() {
        // "1-120" is 120 runs for 1 wicket. Splitting positionally gave runs=1, wickets=120.
        assertEquals(120 to 1, parseRunsAndWickets("1-120"))
        assertEquals(340 to 6, parseRunsAndWickets("6-340"))
    }

    @Test
    fun `wickets above ten are rejected rather than accepted`() {
        assertNull(parseRunsAndWickets("120-140"))
    }

    @Test
    fun `runs only`() {
        assertEquals(199 to 0, parseRunsAndWickets("199"))
    }

    @Test
    fun `zero for one is a real score`() {
        assertEquals(0 to 1, parseRunsAndWickets("0/1"))
    }

    @Test
    fun `non-scores are rejected`() {
        assertNull(parseRunsAndWickets("Not-Started"))
        assertNull(parseRunsAndWickets("TBD"))
        assertNull(parseRunsAndWickets(""))
        assertNull(parseRunsAndWickets("Yet to bat"))
    }

    // --- parseScore end to end ---------------------------------------------

    @Test
    fun `Not-Started does not render as a score`() {
        // The old validity test was `contains("/") || contains("-")`, so the hyphen in
        // "Not-Started" made it a valid 0/0 and the UI displayed "0/0".
        val info = parseScore("Not-Started")
        assertFalse(info.isValid)
        assertEquals(0, info.runs)
        assertEquals(0, info.wickets)
    }

    @Test
    fun `a full score parses every part`() {
        val info = parseScore("199/7 (12.4 Overs)")
        assertTrue(info.isValid)
        assertEquals(199, info.runs)
        assertEquals(7, info.wickets)
        assertEquals("12.4", info.overs)
    }

    @Test
    fun `Australian notation parses end to end`() {
        val info = parseScore("1-120 (15.2 Ov)")
        assertTrue(info.isValid)
        assertEquals(120, info.runs)
        assertEquals(1, info.wickets)
        assertEquals("15.2", info.overs)
    }

    @Test
    fun `placeholders stay invalid`() {
        for (placeholder in listOf("Data Not Found", "-", "TBD", "", null)) {
            assertFalse("'$placeholder' must not be valid", parseScore(placeholder).isValid)
        }
    }

    @Test
    fun `second innings ampersand prefix is stripped`() {
        val info = parseScore("&amp; 123/4")
        assertTrue(info.isValid)
        assertEquals(123, info.runs)
        assertEquals(4, info.wickets)
    }

    @Test
    fun `parseRuns and parseWickets agree with parseScore`() {
        assertEquals(199, parseRuns("199/7 (20 Ov)"))
        assertEquals(7, parseWickets("199/7 (20 Ov)"))
        assertEquals(0, parseRuns("Not-Started"))
        assertEquals(0, parseWickets("Not-Started"))
    }
}
