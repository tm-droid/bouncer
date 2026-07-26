package com.tanush.bouncer.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Table-driven coverage for match state derivation.
 *
 * Every case marked REGRESSION is one the previous first-match-wins `contains` chain got wrong.
 */
class MatchStatusClassifierTest {

    private fun assertSignal(expected: StatusSignal?, status: String) =
        assertEquals("status=\"$status\"", expected, MatchStatusClassifier.classify(status))

    // ── Terminal ──

    @Test
    fun `result strings are terminal`() {
        listOf(
            "India won by 5 wickets",
            "England won by 7 wkts",
            "Australia won by an innings and 20 runs", // REGRESSION: "innings" used to win
            "Match drawn",
            "Match drawn, England 2nd innings 340/6", // REGRESSION: "innings" used to win
            "Match tied",
            "No result",
            "Match abandoned due to rain", // REGRESSION: "rain" is in-progress, but terminal outranks
            "India beat Pakistan by 6 wickets",
            "Complete",
            "South Africa won the series 2-1",
        ).forEach { assertSignal(StatusSignal.TERMINAL, it) }
    }

    @Test
    fun `terminal outranks in-progress wherever it appears`() {
        // The whole point of precedence over position: result prose routinely quotes live prose.
        assertSignal(StatusSignal.TERMINAL, "Live - India won by 5 wickets")
        assertSignal(StatusSignal.TERMINAL, "Day 5: Stumps. Match drawn")
        assertSignal(StatusSignal.TERMINAL, "India needed 12 more runs. England won by 11 runs")
    }

    // ── In progress ──

    @Test
    fun `toss strings are in progress, not complete`() {
        // REGRESSION: bare "won" made all of these read Completed. Note "elected to" was not even
        // in the old Live branch — only Cricbuzz's "opted to" was — so Cricinfo phrasing was worst hit.
        listOf(
            "India won the toss and elected to bat",
            "India won the toss and opted to bowl",
            "England won the toss and chose to field",
            "Toss delayed due to wet outfield",
        ).forEach { assertSignal(StatusSignal.IN_PROGRESS, it) }
    }

    @Test
    fun `live play strings are in progress`() {
        listOf(
            "Live",
            "In Progress",
            "India need 45 runs in 30 balls",
            "India require 45 runs",
            "England 120/3 (15.2 Ov), 2nd innings",
            "Day 3: Session 2",
            "Rain delay",
            "Bad light stopped play",
            "Innings Break",
            "Tea",
            "Lunch",
            "Drinks",
            "Stumps",
            "Strategic Timeout",
        ).forEach { assertSignal(StatusSignal.IN_PROGRESS, it) }
    }

    @Test
    fun `past-tense chase prose alone is in progress but loses to a result`() {
        // REGRESSION: "India needed 12 more runs" used to read Live purely on "need". It still
        // classifies in-progress on its own — correctly, since that is also live chase prose —
        // but any accompanying result marker overrides it.
        assertSignal(StatusSignal.IN_PROGRESS, "India needed 12 more runs")
        assertSignal(StatusSignal.TERMINAL, "India needed 12 more runs, England won by 11 runs")
    }

    // ── Pre-match ──

    @Test
    fun `pre-match strings are pre-match`() {
        listOf("Preview", "Upcoming", "Scheduled", "Match starts at 19:30", "Yet to begin")
            .forEach { assertSignal(StatusSignal.PRE_MATCH, it) }
    }

    // ── Word boundaries: the substring class of bug ──

    @Test
    fun `word boundaries prevent substring false positives`() {
        assertNull("'team' must not match 'tea'", MatchStatusClassifier.classify("Teams announced"))
        assertNull("'training' must not match 'rain'", MatchStatusClassifier.classify("Training cancelled"))
        assertNull("'Bahrain' must not match 'rain'", MatchStatusClassifier.classify("Bahrain venue"))
        assertNull("'drawing' must not match 'draw'", MatchStatusClassifier.classify("Crowd drawing in"))
        assertNull("'Wonderers' must not match 'won'", MatchStatusClassifier.classify("At the Wonderers"))
    }

    // ── Unknown is surfaced, never guessed ──

    @Test
    fun `blank and unrecognised statuses return null`() {
        assertNull(MatchStatusClassifier.classify(null))
        assertNull(MatchStatusClassifier.classify(""))
        assertNull(MatchStatusClassifier.classify("   "))
        assertNull(MatchStatusClassifier.classify("Zorbing championship"))
    }

    @Test
    fun `classification is case insensitive`() {
        assertSignal(StatusSignal.TERMINAL, "INDIA WON BY 5 WICKETS")
        assertSignal(StatusSignal.IN_PROGRESS, "LIVE")
        assertSignal(StatusSignal.PRE_MATCH, "PREVIEW")
    }

    // ── MatchStatus mapping ──

    @Test
    fun `MatchStatus maps signals and preserves blank-is-upcoming`() {
        assertEquals(MatchStatus.Completed, MatchStatus.from("India won by 5 wickets"))
        assertEquals(MatchStatus.Live, MatchStatus.from("India need 45 runs"))
        assertEquals(MatchStatus.Upcoming, MatchStatus.from("Preview"))
        assertEquals(MatchStatus.Upcoming, MatchStatus.from(""))
        assertTrue(MatchStatus.from("Zorbing championship") is MatchStatus.Unknown)
    }

    // ── The two derivations that must never disagree ──

    @Test
    fun `MatchEntity computeIsLive agrees with CricketMatch isLive`() {
        val cases = listOf(
            "Live" to "India need 45 runs",
            "Live" to "India won by 5 wickets", // stale status field alongside a real result
            "Complete" to "India won by 5 wickets",
            "Preview" to "",
            "" to "",
            "Match drawn" to "England 2nd innings 340/6",
            "Live" to "Rain delay",
            "Zorbing" to "championship",
        )
        cases.forEach { (status, currentStatus) ->
            val model = match(status, currentStatus).isLive
            val entity = com.tanush.bouncer.data.local.entities.MatchEntity
                .computeIsLive(status, currentStatus)
            assertEquals(
                "status=\"$status\" currentStatus=\"$currentStatus\"",
                model,
                entity,
            )
        }
    }

    @Test
    fun `a result in either status field wins over a stale Live in the other`() {
        // REGRESSION: matchStatus used to force Live whenever the raw status field alone read Live.
        assertTrue(match(status = "Live", currentStatus = "India won by 5 wickets").isCompleted)
        assertTrue(match(status = "Live", currentStatus = "Match drawn").isCompleted)
    }

    private fun match(status: String, currentStatus: String) = CricketMatch(
        id = "1",
        seriesId = "s1",
        matchTitle = "A vs B",
        matchType = "T20",
        status = status,
        teams = emptyList(),
        venue = "Venue",
        date = "2026-07-25",
        matchTime = "19:30",
        score = null,
        currentStatus = currentStatus,
        info = "",
    )
}
