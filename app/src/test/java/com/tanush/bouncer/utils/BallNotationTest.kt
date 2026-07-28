package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BallNotationTest {

    // --- over 99 ------------------------------------------------------------

    @Test
    fun `a ball number past over 99 is recognised`() {
        // Six of the seven patterns this replaces used `\d{1,2}`. A Test innings routinely passes
        // 100 overs, so anchored variants failed to match at all and unanchored ones read "104.3"
        // as ball 4.3 — silently attributing a delivery to the wrong over.
        assertEquals("104.3", ballNumberOf("104.3 Bumrah to Root, no run"))
        assertEquals("100.1", ballNumberOf("100.1 Ashwin to Smith, defended"))
        assertEquals("249.6", ballNumberOf("249.6 last ball of a long innings"))
        assertTrue(startsWithBallNumber("104.3 Bumrah to Root"))
    }

    @Test
    fun `ordinary ball numbers still work`() {
        assertEquals("0.1", ballNumberOf("0.1 Starc to Rohit, no run"))
        assertEquals("18.5", ballNumberOf("18.5 Bumrah to Maxwell, FOUR"))
        assertEquals("49.6", ballNumberOf("49.6 final ball"))
    }

    @Test
    fun `leading whitespace is tolerated`() {
        assertEquals("18.5", ballNumberOf("   18.5 Bumrah to Maxwell"))
        assertTrue(startsWithBallNumber("\t18.5 something"))
    }

    // --- anchoring ----------------------------------------------------------

    @Test
    fun `a figure inside the line is not a ball number`() {
        // The JS BALL_RE and ballNumberLoosePattern were unanchored, so this scored as ball 45.2 —
        // and in match-sync that wrote a commentary row keyed on a ball never bowled.
        assertNull(ballNumberOf("Scoring at 45.2 runs per over"))
        assertFalse(startsWithBallNumber("Scoring at 45.2 runs per over"))
        assertNull(ballNumberOf("Required rate is 9.4 an over"))
        assertNull(ballNumberOf("End of over 18.5 was eventful"))
    }

    @Test
    fun `non-ball lines yield null`() {
        assertNull(ballNumberOf(""))
        assertNull(ballNumberOf("Drinks!"))
        assertNull(ballNumberOf("Teams:"))
        assertNull(ballNumberOf("18 Bumrah to Maxwell"))
        assertNull(ballNumberOf(".5 Bumrah"))
    }

    @Test
    fun `four digits before the point is not a ball number`() {
        assertNull(ballNumberOf("2024.5 was a good year"))
    }

    // --- ballDigitOf --------------------------------------------------------

    @Test
    fun `every ball digit is exact across a long innings`() {
        for (over in 0..249) {
            for (ball in 1..6) {
                val notation = "$over.$ball".toDouble()
                assertEquals("$notation", ball, ballDigitOf(notation))
            }
        }
    }

    @Test
    fun `the truncating form was in fact already exact`() {
        // Recorded deliberately. The audit claimed `(n * 10).toInt() % 10` yields 4 for 18.5 on
        // IEEE-754 grounds. It does not: 18.5 is exactly representable and 18.5 * 10 is exactly
        // 185.0. There was no bug to fix, and this test stops the false claim being re-filed.
        assertEquals(185.0, 18.5 * 10, 0.0)
        val misreads = (0..249).flatMap { over -> (1..6).map { ball -> "$over.$ball".toDouble() to ball } }
            .filter { (notation, ball) -> ((notation * 10).toInt() % 10) != ball }
        assertTrue("the truncating form misread: $misreads", misreads.isEmpty())
    }

    @Test
    fun `rounding survives arithmetic drift where truncating does not`() {
        // The one case where rounding earns its keep: a ballNumber that arrives having been computed
        // rather than parsed.
        val drifted = 18.5 - 1e-12
        assertEquals(5, ballDigitOf(drifted))
        assertEquals(4, (drifted * 10).toInt() % 10)
    }

    @Test
    fun `a whole over has ball digit zero`() {
        assertEquals(0, ballDigitOf(18.0))
        assertEquals(0, ballDigitOf(0.0))
    }
}
