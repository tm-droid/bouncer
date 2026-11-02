package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.OverPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverWormTest {

    private fun p(innings: Int, over: Int, runs: Int, score: Int, team: String) =
        OverPoint(innings = innings, over = over, runs = runs, score = score, wickets = 0, team = team)

    @Test
    fun `both innings are plotted, each labelled by the side the data names`() {
        val worm = checkNotNull(
            overWormOf(
                listOf(
                    p(1, 1, 4, 4, "IND"), p(1, 2, 6, 10, "IND"),
                    p(2, 1, 9, 9, "WI"),
                ),
            ),
        )
        assertEquals("IND", worm.team1Name)
        assertEquals("WI", worm.team2Name)
        assertEquals(listOf(4, 10), worm.team1Scores)
        assertEquals(listOf(9), worm.team2Scores)
        assertEquals(2, worm.maxOver)
        assertEquals(listOf(4 to 9, 6 to null), worm.perOver.map { it.team1Runs to it.team2Runs })
    }

    @Test
    fun `a missing over ends the line there - no flat segment is invented`() {
        val worm = checkNotNull(overWormOf(listOf(p(1, 1, 4, 4, "A"), p(1, 3, 2, 12, "A"))))
        assertEquals(listOf(4), worm.team1Scores)
        assertEquals("the manhattan still shows every over it has", listOf(1, 3), worm.perOver.map { it.over })
    }

    @Test
    fun `no first-innings over one, no worm`() {
        assertNull(overWormOf(emptyList()))
        assertNull(overWormOf(listOf(p(1, 2, 3, 3, "A"))))
    }
}
