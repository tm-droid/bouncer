package com.tanush.bouncer.utils

import com.tanush.bouncer.utils.CommentaryEventType.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentaryParserTest {

    @Test fun `wicket keywords detected`() {
        assertEquals(WICKET, parseCommentaryType("He's caught at mid-on!", null))
        assertEquals(WICKET, parseCommentaryType("BOWLED him clean!", null))
        assertEquals(WICKET, parseCommentaryType("LBW given!", null))
        assertEquals(WICKET, parseCommentaryType("Run out! Direct hit", null))
        assertEquals(WICKET, parseCommentaryType("Stumped by the keeper", null))
    }

    @Test fun `six by keyword`() =
        assertEquals(SIX, parseCommentaryType("That's a massive SIX over long-on!", null))

    @Test fun `six by runs param`() =
        assertEquals(SIX, parseCommentaryType("Over the ropes", 6))

    @Test fun `four by keyword`() =
        assertEquals(FOUR, parseCommentaryType("FOUR! Through the covers", null))

    @Test fun `four by runs param`() =
        assertEquals(FOUR, parseCommentaryType("Plays to the boundary", 4))

    @Test fun `dot ball by zero runs`() =
        assertEquals(DOT_BALL, parseCommentaryType("Defended back", 0))

    @Test fun `dot ball by keyword`() =
        assertEquals(DOT_BALL, parseCommentaryType("Dot ball, well bowled", null))

    @Test fun `run values map to correct types`() {
        assertEquals(SINGLE, parseCommentaryType("Pushes for one", 1))
        assertEquals(DOUBLE, parseCommentaryType("Runs hard for two", 2))
        assertEquals(THREE, parseCommentaryType("Three to fine leg", 3))
    }

    @Test fun `innings end requires both conditions`() {
        assertEquals(INNINGS_END, parseCommentaryType("End of innings!", null))
        assertEquals(INNINGS_END, parseCommentaryType("India all out for 245", null))
        // "innings" alone is not enough
        assertEquals(REGULAR, parseCommentaryType("First innings batting", null))
    }

    @Test fun `milestone keywords detected`() {
        assertEquals(MILESTONE, parseCommentaryType("That's his century!", null))
        assertEquals(MILESTONE, parseCommentaryType("Reaches his fifty", null))
    }

    @Test fun `wicket priority over milestone when text contains both`() {
        assertEquals(WICKET, parseCommentaryType("Out for 99, just short of his hundred", null))
    }

    @Test fun `regular fallback when nothing matches`() =
        assertEquals(REGULAR, parseCommentaryType("Good length delivery defended", null))

    @Test fun `wicket with zero runs is not misclassified as dot ball`() {
        assertEquals(WICKET, parseCommentaryType("Caught at slip!", 0))
        assertEquals(WICKET, parseCommentaryType("BOWLED him! runs=0", 0))
    }
}
