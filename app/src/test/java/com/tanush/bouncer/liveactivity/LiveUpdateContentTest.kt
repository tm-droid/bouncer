package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A promoted Live Update is a handful of strings and a bar; there is no layout and — with no
 * Robolectric in this project — no way to assert on a real `Notification`. So this is where the
 * feature is actually pinned.
 */
class LiveUpdateContentTest {

    private fun payload(
        score: String? = "234/1 (56.4)",
        team1Score: String? = "348",
        team2Score: String? = "266 & 234/1",
        team1Name: String? = "ENG",
        team2Name: String? = "AUS",
        team1Id: String? = "9",
        team2Id: String? = "4",
        batsmen: List<ScrapedBatsman> = emptyList(),
        bowler: ScrapedBowler? = null,
        status: String? = "Australia trail by 48 runs",
    ) = MatchLiveDetailPayload(
        batsmen = batsmen,
        bowler = bowler,
        score = score,
        team1Score = team1Score,
        team1Overs = "116.1",
        team2Score = team2Score,
        team2Overs = "56.4",
        team1Name = team1Name,
        team2Name = team2Name,
        team1Id = team1Id,
        team2Id = team2Id,
        status = status,
    )

    private fun match(
        matchType: String = "Test",
        title: String = "The Ashes",
        teams: List<CricketMatch.Team> = listOf(
            team("Australia", "4"),
            team("England", "9"),
        ),
        status: String = "Live",
    ) = CricketMatch(
        id = MATCH_ID,
        seriesId = "s1",
        matchTitle = title,
        matchType = matchType,
        status = status,
        teams = teams,
        venue = "Lord's",
        date = "2026-09-04",
        matchTime = "11:00",
        score = null,
        currentStatus = status,
        info = "",
    )

    private fun team(name: String, teamId: String) = CricketMatch.Team(
        name = name,
        score = "",
        status = "",
        isBatting = false,
        logoUrl = null,
        teamId = teamId,
    )

    private fun batsman(name: String, runs: String, balls: String, striker: Boolean) = ScrapedBatsman(
        name = name,
        runs = runs,
        balls = balls,
        fours = "0",
        sixes = "0",
        strikeRate = "0",
        isStriker = striker,
    )

    // ── Title ────────────────────────────────────────────────────────────────

    @Test
    fun `title carries both sides in innings order`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload())
        assertTrue(content.title, content.title.startsWith("ENG 348"))
        assertTrue(content.title, content.title.endsWith("AUS 266 & 234/1"))
    }

    /** A Test side's score is two innings joined; collapsing it to one was a shipped bug. */
    @Test
    fun `a multi-innings score is not collapsed to its latest innings`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload())
        assertTrue(content.title, content.title.contains("266 & 234/1"))
    }

    @Test
    fun `a title still appears when the payload has no side scores`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(team1Score = null, team2Score = null, team1Name = null, team2Name = null),
        )
        assertEquals("234/1 (56.4)", content.title)
    }

    // ── Status-bar chip ──────────────────────────────────────────────────────

    /** The chip is the innings being batted, never the joined two-innings string. */
    @Test
    fun `short critical text is the live innings alone`() {
        assertEquals("234/1", buildLiveUpdateContent(MATCH_ID, match(), payload()).shortCriticalText)
    }

    @Test
    fun `short critical text is dropped rather than truncated past the platform limit`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload(score = "12345/10 (5.0)"))
        assertNull(content.shortCriticalText)
    }

    @Test
    fun `a seven character score still fits the chip`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload(score = "1234/10 (5.0)"))
        assertEquals("1234/10", content.shortCriticalText)
    }

    // ── Secondary line ───────────────────────────────────────────────────────

    @Test
    fun `the striker leads the batting pair and is marked`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                batsmen = listOf(
                    batsman("Labuschagne", "63", "103", striker = false),
                    batsman("Warner", "95", "159", striker = true),
                ),
            ),
        )
        assertEquals("Warner 95(159)* · Labuschagne 63(103)", content.text)
    }

    @Test
    fun `the bowler is appended when the whole line fits`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                batsmen = listOf(batsman("Root", "9", "12", striker = true)),
                bowler = ScrapedBowler("Cummins", "18.0", "4", "45", "2", "2.50"),
            ),
        )
        assertEquals("Root 9(12)* · Cummins 2/45", content.text)
    }

    /** Either the line is complete or it is deliberately shorter — never cut mid-name by the platform. */
    @Test
    fun `the bowler is dropped rather than overflowing the line`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                batsmen = listOf(
                    batsman("Labuschagne", "63", "103", striker = true),
                    batsman("Khawaja", "142", "233", striker = false),
                ),
                bowler = ScrapedBowler("Bashir", "18.0", "4", "45", "2", "2.50"),
            ),
        )
        assertFalse(content.text, content.text.contains("Bashir"))
    }

    @Test
    fun `an innings with no batsmen falls back to the bowler and then to the status`() {
        val bowlerOnly = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(bowler = ScrapedBowler("Starc", "8.0", "1", "30", "3", "3.75")),
        )
        assertEquals("Starc 3/30", bowlerOnly.text)

        val statusOnly = buildLiveUpdateContent(MATCH_ID, match(), payload(status = "Innings break"))
        assertEquals("Innings break", statusOnly.text)
    }

    // ── Which side is batting ────────────────────────────────────────────────

    /**
     * The payload names its slots after the FIRST-INNINGS side, so `team1` is the other team for
     * the whole of a second innings. The batting side is found by matching the live score line.
     */
    @Test
    fun `the batting side is the one whose latest innings matches the live score`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload())
        assertEquals("AUS", content.accentTeamName)
    }

    /**
     * `team1Name` is a Cricbuzz short code that matches no full name and no logo key, so the join
     * onto the match's own teams is by id. A name-based join silently never fires.
     */
    @Test
    fun `the logo team is resolved through the team id, not the short code`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload())
        assertEquals("Australia", content.logoTeamName)
    }

    @Test
    fun `an unmatched team id leaves the logo unresolved rather than guessing`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload(team2Id = "999"))
        assertNull(content.logoTeamName)
    }

    @Test
    fun `an unreadable live score leaves the batting side unknown without crashing`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload(score = null))
        assertNull(content.logoTeamName)
        assertEquals("Australia", content.accentTeamName)
    }

    // ── Format and completion ────────────────────────────────────────────────

    @Test
    fun `a Test gets the wicket bar and a T20 the ball bar`() {
        val test = buildLiveUpdateContent(MATCH_ID, match(matchType = "Test"), payload())
        assertEquals(ProgressUnit.WICKETS, (test.progress as LiveUpdateProgress.Determinate).unit)

        val t20 = buildLiveUpdateContent(
            MATCH_ID,
            match(matchType = "T20", title = "Vitality Blast"),
            payload(score = "88/3 (11.2)"),
        )
        val bar = t20.progress as LiveUpdateProgress.Determinate
        assertEquals(ProgressUnit.BALLS, bar.unit)
        assertEquals(68, bar.progress)
    }

    @Test
    fun `a result in the live status ends the update even when the cached entry still says live`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload(status = "England won by 8 wickets"))
        assertTrue(content.isComplete)
    }

    @Test
    fun `an in-progress status does not end the update`() {
        assertFalse(buildLiveUpdateContent(MATCH_ID, match(), payload()).isComplete)
    }

    /** A subscription outlives the match cache; the payload alone still has to render. */
    @Test
    fun `content builds with no cached match entry`() {
        val content = buildLiveUpdateContent(MATCH_ID, null, payload())
        assertTrue(content.title, content.title.contains("AUS 266 & 234/1"))
        assertEquals("234/1", content.shortCriticalText)
        assertNull(content.subText)
        assertNull(content.logoTeamName)
    }

    private companion object {
        const val MATCH_ID = "170667"
    }
}
