package com.tanush.bouncer.ui.widgets

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widgets' single snapshot, and the two merges that write into it.
 *
 * Three stores held this fact and three writers disagreed about it. `ScorePollingWorker` wrote the
 * live payload's `score` — the innings being batted — into `team1_score` whichever side was
 * batting, never wrote `team2_score` at all, and hardcoded `is_live = true`; the FCM
 * `score_update` handler called `updateAllWidgets(match = null)`, which cleared every store rather
 * than applying the score the push was carrying. These are the rules that replaced all of that.
 */
class WidgetSnapshotTest {

    private fun team(name: String, score: String, overs: String, id: String, batting: Boolean = false) =
        CricketMatch.Team(
            name = name,
            score = score,
            status = "",
            isBatting = batting,
            logoUrl = null,
            acronym = name.take(3).uppercase(),
            overs = overs,
            teamId = id,
        )

    private fun match(
        status: String = "Live",
        currentStatus: String = "Day 2: Session 1",
    ) = CricketMatch(
        id = "170667",
        seriesId = "s1",
        matchTitle = "Australia vs India, 2nd Test",
        matchType = "Test",
        status = status,
        teams = listOf(
            team("Australia", "266 & 234/1", "56.4", id = "2", batting = true),
            team("India", "301", "88.0", id = "5"),
        ),
        venue = "MCG",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = currentStatus,
        info = "",
    )

    @Test
    fun `the list entry fills both sides`() {
        val snapshot = WidgetSnapshotSource.snapshotOf(match())

        assertEquals("266 & 234/1", snapshot.team1Score)
        // The half that was never written at all: `team2_score` kept whatever happened to be in
        // the store from some earlier match.
        assertEquals("301", snapshot.team2Score)
        assertEquals("India", snapshot.team2Name)
    }

    @Test
    fun `live and complete come from the classifier, not from a literal`() {
        assertTrue(WidgetSnapshotSource.snapshotOf(match()).isLive)

        val finished = WidgetSnapshotSource.snapshotOf(
            match(status = "Complete", currentStatus = "Australia won by 6 wickets")
        )
        assertFalse("a finished match must not carry the live treatment", finished.isLive)
        assertTrue(finished.isComplete)
    }

    @Test
    fun `the payload's first-innings side is not slot one`() {
        // The server orients everything to the side that batted FIRST. Here that is India — the
        // match lists Australia first — so an unaligned merge writes India's total against
        // Australia's name, which is the defect this alignment exists to prevent.
        val payload = MatchLiveDetailPayload(
            team1Id = "5",
            team1Name = "IND",
            team1Score = "301",
            team1Overs = "88.0",
            team2Id = "2",
            team2Name = "AUS",
            team2Score = "266 & 260/2",
            team2Overs = "61.2",
        )
        val merged = withDetail(WidgetSnapshotSource.snapshotOf(match()), payload, match().teams)

        assertEquals("266 & 260/2", merged.team1Score)
        assertEquals("301", merged.team2Score)
        assertEquals("61.2", merged.team1Overs)
    }

    @Test
    fun `a payload field the row does not carry leaves the list's value alone`() {
        val base = WidgetSnapshotSource.snapshotOf(match())
        val merged = withDetail(base, MatchLiveDetailPayload(team1Id = "2"), match().teams)

        assertEquals(base.team1Score, merged.team1Score)
        assertEquals(base.team2Score, merged.team2Score)
        assertEquals(base.statusText, merged.statusText)
    }

    @Test
    fun `a single batter at the crease is no pair rather than half a pair`() {
        assertNull(pairOf(listOf(batsman("Head", "12", "20", striker = true))))
        assertEquals(
            "Head|12|20;Khawaja|4|11",
            pairOf(listOf(batsman("Head", "12", "20", striker = true), batsman("Khawaja", "4", "11")))
        )
    }

    private fun batsman(name: String, runs: String, balls: String, striker: Boolean = false) =
        ScrapedBatsman(name, runs, balls, fours = "0", sixes = "0", strikeRate = "0.0", isStriker = striker)

    private fun snapshot() = WidgetSnapshotSource.snapshotOf(match())

    @Test
    fun `a push updates the side it names and never blanks the other`() {
        val updated = applyPushTo(snapshot(), "170667", "Australia", "India", "266 & 260/2", nowMs = 1L)

        assertEquals("266 & 260/2", updated?.team1Score)
        assertEquals("301", updated?.team2Score)
    }

    @Test
    fun `a push naming neither side changes nothing`() {
        // Better a score a minute old than a score against the wrong team. The handler this
        // replaced cleared all three stores on exactly this message.
        assertNull(applyPushTo(snapshot(), "170667", "Sussex", "Kent", "120/3"))
    }

    @Test
    fun `a push for another match changes nothing`() {
        assertNull(applyPushTo(snapshot(), "999999", "Australia", "India", "120/3"))
    }

    @Test
    fun `a push with no score changes nothing`() {
        assertNull(applyPushTo(snapshot(), "170667", "Australia", "India", "   "))
    }
}
