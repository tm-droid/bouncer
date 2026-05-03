package com.tanush.bouncer.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentaryFusionServiceTest {

    @Test
    fun `chooseDirectCommentary returns richer snippet when sources only differ by formatting`() {
        val concise = "18.5: Bumrah to Root, FOUR!"
        val richer = "18.5 Bumrah to Root, FOUR! Driven crisply through cover for four."

        assertEquals(richer, CommentaryFusionService.chooseDirectCommentary(concise, richer))
    }

    @Test
    fun `chooseDirectCommentary keeps single source flow on blank secondary snippet`() {
        assertNull(
            CommentaryFusionService.chooseDirectCommentary(
                "18.5: Bumrah to Root, FOUR!",
                ""
            )
        )
    }

    @Test
    fun `chooseDirectCommentary does not merge different balls`() {
        assertNull(
            CommentaryFusionService.chooseDirectCommentary(
                "18.5: Bumrah to Root, FOUR!",
                "18.6: Bumrah to Root, FOUR! Back-to-back boundaries."
            )
        )
    }

    @Test
    fun `upsertLatestBallLine replaces shorter version of same ball and respects max size`() {
        val lines = mutableListOf(
            "18.4: Bumrah to Root, dot ball.",
            "18.5: Bumrah to Root, FOUR!"
        )

        CommentaryLineUtils.upsertLatestBallLine(
            lines,
            "18.5: Bumrah to Root, FOUR! Driven crisply through cover for four.",
            maxSize = 2
        )
        CommentaryLineUtils.upsertLatestBallLine(
            lines,
            "18.6: Bumrah to Root, single taken.",
            maxSize = 2
        )

        assertEquals(
            listOf(
                "18.5: Bumrah to Root, FOUR! Driven crisply through cover for four.",
                "18.6: Bumrah to Root, single taken."
            ),
            lines
        )
    }
}
