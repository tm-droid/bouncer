package com.tanush.bouncer.services

import com.tanush.bouncer.services.CommentaryScorer.CommentaryType.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentaryScorerTest {

    private val scorer = CommentaryScorer()

    @Test fun `blank input is UNKNOWN`() {
        assertEquals(UNKNOWN, scorer.categorizeCommentary(""))
        assertEquals(UNKNOWN, scorer.categorizeCommentary("   "))
    }

    @Test fun `pre-match keywords are PRE_MATCH`() {
        assertEquals(PRE_MATCH, scorer.categorizeCommentary("Toss: India won the toss and elected to bat"))
        assertEquals(PRE_MATCH, scorer.categorizeCommentary("Pitch report: dry surface, will assist spinners"))
        assertEquals(PRE_MATCH, scorer.categorizeCommentary("Playing XI announced for the match"))
    }

    @Test fun `post-match keywords are POST_MATCH`() {
        assertEquals(POST_MATCH, scorer.categorizeCommentary("Man of the match: Rohit Sharma"))
        assertEquals(POST_MATCH, scorer.categorizeCommentary("Post-match interview with the captain"))
        assertEquals(POST_MATCH, scorer.categorizeCommentary("Trophy presentation ceremony begins"))
    }

    @Test fun `match break keywords are MATCH_BREAK`() {
        assertEquals(MATCH_BREAK, scorer.categorizeCommentary("Innings break — teams are changing"))
        assertEquals(MATCH_BREAK, scorer.categorizeCommentary("Drinks break after 10 overs"))
        assertEquals(MATCH_BREAK, scorer.categorizeCommentary("Rain delay, covers on the pitch"))
    }

    @Test fun `ball-by-ball commentary is BALL_BY_BALL`() {
        assertEquals(BALL_BY_BALL, scorer.categorizeCommentary("18.5: Bumrah to Kohli, dot ball"))
        assertEquals(BALL_BY_BALL, scorer.categorizeCommentary("Bowled through the gate! Clean hit"))
        assertEquals(BALL_BY_BALL, scorer.categorizeCommentary("Wide down the leg side"))
    }

    @Test fun `unrecognised content is UNKNOWN`() {
        assertEquals(UNKNOWN, scorer.categorizeCommentary("Random text with no cricket keywords"))
    }

    @Test fun `ball number prefix is recognised as ball-by-ball`() {
        assertEquals(true, scorer.isBallByBallCommentary("18.5: Bumrah to Kohli, 1 run"))
    }

    @Test fun `non-ball keyword suppresses ball-by-ball`() {
        assertEquals(false, scorer.isBallByBallCommentary("Toss: India won"))
        assertEquals(false, scorer.isBallByBallCommentary("Pitch report: dry surface"))
    }

    @Test fun `ball indicator keyword recognises ball-by-ball`() {
        assertEquals(true, scorer.isBallByBallCommentary("Bowled him! Clean delivery"))
        assertEquals(true, scorer.isBallByBallCommentary("Appeal for LBW, given out!"))
    }

    @Test fun `blank input is not ball-by-ball`() {
        assertEquals(false, scorer.isBallByBallCommentary(""))
        assertEquals(false, scorer.isBallByBallCommentary("   "))
    }
}
