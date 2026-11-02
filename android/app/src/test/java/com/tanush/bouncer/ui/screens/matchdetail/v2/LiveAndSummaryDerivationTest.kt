package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.Innings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The current stand on Live, and the top performers on Summary, read off one innings. */
class LiveAndSummaryDerivationTest {

    private fun innings(
        score: String = "141/3",
        overs: String = "16.3",
        fow: List<FallOfWicket> = emptyList(),
        batsmen: List<BatsmanStats> = emptyList(),
        bowlers: List<BowlerStats> = emptyList(),
    ) = Innings(
        teamName = "India",
        score = score,
        overs = overs,
        batsmen = batsmen,
        bowlers = bowlers,
        fallOfWickets = fow,
        extras = "",
        total = score,
    )

    @Test
    fun `the stand is runs and legal balls since the last wicket, extras included`() {
        val inn = innings(fow = listOf(FallOfWicket("40", "5.2", "A", 1), FallOfWicket("98", "12.1", "B", 2)))
        // 141 - 98 runs; 16.3 (99 balls) - 12.1 (73 balls).
        assertEquals(43 to 26, currentPartnership(inn))
    }

    @Test
    fun `a scorecard's own score form reads too`() {
        val inn = innings(score = "156-4 f/o", overs = "54 Ov", fow = listOf(FallOfWicket("120", "40.2", "A", 4)))
        assertEquals(36 to 82, currentPartnership(inn))
    }

    @Test
    fun `the last wicket is the one at the highest score, numbered or not`() {
        val inn = innings(fow = listOf(FallOfWicket("40", "5.2", "A"), FallOfWicket("98-2", "12.1", "B")))
        assertEquals(43 to 26, currentPartnership(inn))
    }

    @Test
    fun `with no wicket down the stand is the whole innings`() {
        assertEquals(141 to 99, currentPartnership(innings()))
    }

    @Test
    fun `a card behind its own total, or overs that are not overs, gives no stand`() {
        assertNull(currentPartnership(innings(fow = listOf(FallOfWicket("150", "17.1", "A", 1)))))
        assertNull(currentPartnership(innings(overs = "16.7")))
        assertNull(currentPartnership(innings(score = "")))
    }

    private fun bat(name: String, runs: String, balls: String) = BatsmanStats(name, "c X b Y", runs, balls, "0", "0", "0")
    private fun bowl(name: String, wickets: String, runs: String) = BowlerStats(name, "4", "0", runs, wickets, "0", "0", "0")

    @Test
    fun `top performers are the two highest scores and the two best figures`() {
        val inn = innings(
            batsmen = listOf(bat("A", "12", "10"), bat("B", "64", "40"), bat("C", "64", "38"), bat("D", "0", "3")),
            bowlers = listOf(bowl("P", "1", "20"), bowl("Q", "3", "31"), bowl("R", "3", "24"), bowl("S", "0", "10")),
        )
        val (batters, bowlers) = topPerformers(inn)
        // Level on runs, the quicker innings ranks first; level on wickets, the cheaper figures.
        assertEquals(listOf("C", "B"), batters.map { it.name })
        assertEquals(listOf("R", "Q"), bowlers.map { it.name })
    }

    @Test
    fun `nobody who did not score or take a wicket is a top performer`() {
        val (batters, bowlers) = topPerformers(innings(batsmen = listOf(bat("D", "0", "3")), bowlers = listOf(bowl("S", "0", "10"))))
        assertEquals(emptyList<BatsmanStats>(), batters)
        assertEquals(emptyList<BowlerStats>(), bowlers)
    }
}
