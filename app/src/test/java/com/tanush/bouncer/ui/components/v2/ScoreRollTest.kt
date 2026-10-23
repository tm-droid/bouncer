package com.tanush.bouncer.ui.components.v2

import com.tanush.bouncer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreRollTest {

    @Test
    fun `only the changed tail rolls`() {
        assertEquals(ScoreRollSplit("1", "87/4", "91/4"), scoreRollSplit("187/4", "191/4"))
        assertEquals(ScoreRollSplit("187/", "4", "5"), scoreRollSplit("187/4", "187/5"))
    }

    @Test
    fun `equal scores roll nothing`() {
        assertEquals(ScoreRollSplit("187/4", "", ""), scoreRollSplit("187/4", "187/4"))
    }

    @Test
    fun `a length change rolls from the first difference`() {
        assertEquals(ScoreRollSplit("", "99/2", "101/2"), scoreRollSplit("99/2", "101/2"))
        assertEquals(ScoreRollSplit("1", "", "0"), scoreRollSplit("1", "10"))
    }

    @Test
    fun `sentence for a side batting`() {
        assertEquals(
            ScoreSentenceSpec(R.string.a11y_score_for, listOf("India", "187", "4", "32.1")),
            scoreSentenceSpec("India", 187, 4, "32.1"),
        )
    }

    @Test
    fun `ten wickets is all out and carries no wicket count`() {
        assertEquals(
            ScoreSentenceSpec(R.string.a11y_score_all_out, listOf("India", "240", "49.2")),
            scoreSentenceSpec("India", 240, 10, "49.2"),
        )
        assertEquals(
            ScoreSentenceSpec(R.string.a11y_score_all_out_no_overs, listOf("India", "240")),
            scoreSentenceSpec("India", 240, 10, null),
        )
    }

    @Test
    fun `no runs means yet to bat`() {
        assertEquals(
            ScoreSentenceSpec(R.string.a11y_yet_to_bat, listOf("India")),
            scoreSentenceSpec("India", null, null, null),
        )
    }

    @Test
    fun `blank overs use the sentence without overs`() {
        assertEquals(
            ScoreSentenceSpec(R.string.a11y_score_for_no_overs, listOf("India", "0", "0")),
            scoreSentenceSpec("India", 0, 0, " "),
        )
    }

    @Test
    fun `a delivery appended or slid in is an arrival`() {
        assertTrue(ballArrived(listOf("1", "0"), listOf("1", "0", "4")))
        val full = List(12) { "1" }
        assertTrue(ballArrived(full, full.drop(1) + "W"))
        assertTrue(ballArrived(List(6) { "1" }, listOf("0")))
    }

    @Test
    fun `opening, repeats and corrections are not arrivals`() {
        assertFalse(ballArrived(listOf("1", "0"), listOf("1", "0")))
        assertFalse(ballArrived(emptyList(), emptyList()))
        assertFalse(ballArrived(listOf("1", "0"), listOf("1", "4")))
    }
}
