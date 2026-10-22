package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.COMMENTARY
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.GRAPHS
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.HIGHLIGHTS
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.INFO
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.LIVE
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.SCORECARD
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.SQUADS
import com.tanush.bouncer.ui.screens.matchdetail.v2.MatchTab.SUMMARY
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which tabs a match shows in each state, which one it opens on, and where a selection lands when the set changes. */
class MatchTabsTest {

    private val everything = MatchTabContent(scorecard = true, commentary = true, graphs = true, squads = true, highlights = true)
    private val nothing = MatchTabContent(scorecard = false, commentary = false, graphs = false, squads = false, highlights = false)

    @Test
    fun `a fixture opens on Info, and has no scorecard, graphs or highlights to offer`() {
        assertEquals(listOf(INFO, SQUADS, COMMENTARY), matchTabsFor(MatchPhase.UPCOMING, everything))
        assertEquals(listOf(INFO), matchTabsFor(MatchPhase.UPCOMING, nothing))
    }

    @Test
    fun `a match in play opens on Live and keeps Scorecard and Commentary before anything arrives`() {
        assertEquals(
            listOf(LIVE, SCORECARD, COMMENTARY, GRAPHS, SQUADS, INFO, HIGHLIGHTS),
            matchTabsFor(MatchPhase.IN_PLAY, everything),
        )
        // Scorecard's empty state is where the retry is, so it stays.
        assertEquals(listOf(LIVE, SCORECARD, COMMENTARY, INFO), matchTabsFor(MatchPhase.IN_PLAY, nothing))
    }

    @Test
    fun `a finished match opens on Summary, with highlights ahead of the team sheets`() {
        assertEquals(
            listOf(SUMMARY, SCORECARD, COMMENTARY, GRAPHS, HIGHLIGHTS, SQUADS, INFO),
            matchTabsFor(MatchPhase.FINISHED, everything),
        )
        // An abandoned match: nothing was bowled, so nothing but the summary and the facts.
        assertEquals(listOf(SUMMARY, INFO), matchTabsFor(MatchPhase.FINISHED, nothing))
    }

    @Test
    fun `the first tab is the default in every state`() {
        MatchPhase.entries.forEach { phase ->
            listOf(everything, nothing).forEach { content ->
                assertEquals(0, pageFor(matchTabsFor(phase, content), selected = null))
            }
        }
    }

    @Test
    fun `a selection survives the tab set changing around it`() {
        val before = matchTabsFor(MatchPhase.IN_PLAY, nothing)
        val after = matchTabsFor(MatchPhase.IN_PLAY, everything)
        assertEquals(before.indexOf(INFO), pageFor(before, INFO))
        assertEquals(after.indexOf(INFO), pageFor(after, INFO))
    }

    @Test
    fun `the Live tab becomes Summary when the match ends under it`() {
        val finished = matchTabsFor(MatchPhase.FINISHED, everything)
        assertEquals(finished.indexOf(SUMMARY), pageFor(finished, LIVE))
    }

    @Test
    fun `a selected tab that went away falls back to the default`() {
        assertEquals(0, pageFor(matchTabsFor(MatchPhase.FINISHED, nothing), GRAPHS))
    }

    @Test
    fun `state comes from the match`() {
        assertEquals(MatchPhase.UPCOMING, matchPhaseOf(match(status = "Match starts at 14:00", scores = listOf("", ""))))
        assertEquals(MatchPhase.IN_PLAY, matchPhaseOf(match(status = "India need 40 runs in 30 balls", scores = listOf("180/6", "141/3"))))
        assertEquals(MatchPhase.IN_PLAY, matchPhaseOf(match(status = "Stumps - Day 2", scores = listOf("350", "120/2"))))
        // After the toss and before the first ball: the header's badge says LIVE, so the tabs do too.
        assertEquals(MatchPhase.IN_PLAY, matchPhaseOf(match(status = "India opt to bowl", scores = listOf("", ""), info = "Toss")))
        assertEquals(MatchPhase.FINISHED, matchPhaseOf(match(status = "India won by 7 wkts", scores = listOf("180/6", "181/3"))))
        assertEquals(MatchPhase.FINISHED, matchPhaseOf(match(status = "Match abandoned due to rain", scores = listOf("", ""))))
    }

    private fun match(status: String, scores: List<String>, info: String = "") = CricketMatch(
        id = "1",
        seriesId = "0",
        matchTitle = "India vs Australia",
        matchType = "T20",
        status = status,
        teams = listOf("India", "Australia").mapIndexed { i, name ->
            CricketMatch.Team(name = name, score = scores[i], status = "", isBatting = false, logoUrl = null)
        },
        venue = "",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = status,
        info = info,
    )
}
