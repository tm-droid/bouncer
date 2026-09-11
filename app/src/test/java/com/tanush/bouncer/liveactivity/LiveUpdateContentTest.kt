package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.data.remote.LiveSituation
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
 * A promoted Live Update is a handful of strings; there is no layout and — with no Robolectric in
 * this project — no way to assert on a real `Notification`. So this is where the feature is
 * actually pinned.
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
        recentBalls: List<String> = emptyList(),
        status: String? = "Australia trail by 48 runs",
        situation: LiveSituation? = null,
    ) = MatchLiveDetailPayload(
        batsmen = batsmen,
        bowler = bowler,
        recentBalls = recentBalls,
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
        situation = situation,
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

    /**
     * The headline is the side batting, its whole contribution and the over it is on. The
     * opposition moved to the expanded block, which is where a second score belongs.
     */
    @Test
    fun `title is the batting side, its aggregate and its over figure`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload())
        assertEquals("AUS 266 & 234/1 (56.4)", content.title)
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

    /**
     * `teamId` is written by `CricBuzzScraper` only — the Appwrite list blob omits it — so roughly
     * half of live matches reach here with no id to join on. The acronym is the second chance.
     */
    @Test
    fun `an unmatched team id falls back to the acronym`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload(team2Id = "999"))
        assertEquals("Australia", content.logoTeamName)
    }

    /** An ambiguous code resolves to nothing: a missing crest beats the wrong team's crest. */
    @Test
    fun `an acronym matching both sides leaves the logo unresolved`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(teams = listOf(team("Australia", "4"), team("Austria", "9"))),
            payload(team2Id = "999", team2Name = "AUS"),
        )
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
    fun `the header line is the series and the cached format`() {
        assertEquals("The Ashes · Test", buildLiveUpdateContent(MATCH_ID, match(), payload()).subText)
    }

    /**
     * The cache goes stale and the subscription outlives it. The payload's own format word keeps
     * the header line alive — which, while `LiveUpdateSources` keyed the cache `"live"` against
     * `MatchCacheService`'s `"Live"`, was every build of the card on every device.
     */
    @Test
    fun `the format falls back to the payload when there is no cached match`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            null,
            payload(situation = situation(matchFormat = "TEST")),
        )
        assertEquals("Test", content.subText)
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

    // ── Expanded block ───────────────────────────────────────────────────────

    /** Descending order of what a follower wants next, one fact per line. */
    @Test
    fun `the expanded block is a scoreboard in priority order`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                batsmen = listOf(
                    batsman("Labuschagne", "63", "103", striker = false),
                    batsman("Warner", "95", "159", striker = true),
                ),
                bowler = ScrapedBowler("Cummins", "18.0", "4", "45", "2", "2.50"),
                recentBalls = listOf("1", "4", "0", "0", "W", "2"),
                status = "Day 4: 1st Session - Australia trail by 48 runs",
            ),
        )
        assertEquals(
            listOf(
                "ENG 348",
                "Warner 95 (159)*",
                "Labuschagne 63 (103)",
                "Cummins 2/45 (18.0)",
                "Recent: 1 4 0 0 W 2",
                "Australia trail by 48 runs",
            ),
            content.bigText.lines(),
        )
    }

    /**
     * The template ellipsises overflow rather than scrolling it, so the block has a hard ceiling.
     *
     * With the batters capped at the two actually at the crease, a full payload produces exactly
     * the ceiling — opposition, two batters, bowler, recent balls, status. This asserts the ceiling
     * is never exceeded, which is the invariant; a payload that could exceed it would be losing the
     * status line silently.
     */
    @Test
    fun `the expanded block never exceeds its line ceiling`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                batsmen = listOf(
                    batsman("Warner", "95", "159", striker = true),
                    batsman("Labuschagne", "63", "103", striker = false),
                    batsman("Khawaja", "142", "233", striker = false),
                ),
                bowler = ScrapedBowler("Cummins", "18.0", "4", "45", "2", "2.50"),
                recentBalls = listOf("1", "4"),
                status = "Australia trail by 48 runs",
            ),
        )
        val lines = content.bigText.lines()
        assertEquals(content.bigText, 6, lines.size)
        assertEquals("ENG 348", lines.first())
        assertEquals("Australia trail by 48 runs", lines.last())
    }

    /** Only the last over's worth, which is what the references show. */
    @Test
    fun `recent balls are trimmed to one over and omitted when there are none`() {
        val many = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(recentBalls = listOf("6", "1", "4", "0", "0", "W", "2", "1")),
        )
        assertTrue(many.bigText, many.bigText.contains("Recent: 4 0 0 W 2 1"))

        val none = buildLiveUpdateContent(MATCH_ID, match(), payload(recentBalls = emptyList()))
        assertFalse(none.bigText, none.bigText.contains("Recent:"))
    }

    /** "Day 4: 1st Session - Kent need 371 runs" is two facts; only the second fits a card. */
    @Test
    fun `the session prefix is stripped from the status line`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(status = "Day 4: 1st Session - Kent need 371 runs"),
        )
        assertTrue(content.bigText, content.bigText.endsWith("Kent need 371 runs"))
    }

    /** A status with no prefix passes through whole rather than being cut at a space. */
    @Test
    fun `a status with no session prefix survives intact`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload(status = "Innings Break"))
        assertTrue(content.bigText, content.bigText.endsWith("Innings Break"))
    }

    /** The numbers are exact, so a chase is stated from them rather than from Cricbuzz's prose. */
    @Test
    fun `a chase is stated from the situation numbers`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                status = "Day 5: 2nd Session - Australia need 66 runs",
                situation = situation(runsNeeded = 66, oversRemaining = 10.3, requiredRunRate = 6.6),
            ),
        )
        assertTrue(content.bigText, content.bigText.endsWith("Need 66 runs from 10.3 overs · RRR 6.60"))
    }

    // ── Structured scoreboard ────────────────────────────────────────────────

    /**
     * The custom layout binds fields, not a paragraph, and the template's text is derived from the
     * same fields. A figure that exists in one rendering and not the other is the drift this
     * structure exists to stop.
     */
    @Test
    fun `the scoreboard carries the figures as fields`() {
        val board = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                batsmen = listOf(
                    batsman("Labuschagne", "63", "103", striker = false),
                    batsman("Warner", "95", "159", striker = true),
                ),
                bowler = ScrapedBowler("Cummins", "18.0", "4", "45", "2", "2.50"),
                recentBalls = listOf("1", "4"),
            ),
        ).scoreboard

        assertEquals("AUS", board.battingName)
        assertEquals("266 & 234/1", board.battingScore)
        assertEquals("56.4", board.battingOvers)
        assertEquals("ENG", board.opponentName)
        assertEquals("348", board.opponentScore)
        assertEquals(listOf("Warner", "Labuschagne"), board.batters.map { it.name })
        assertEquals("95 (159)", board.batters.first().figure)
        assertTrue(board.batters.first().isStriker)
        assertEquals("Cummins", board.bowler?.name)
        assertEquals("2/45 (18.0)", board.bowler?.figure)
        assertEquals(listOf("1", "4"), board.recentBalls)
    }

    /** Only two batters are at the crease; a payload listing more is a scrape artefact. */
    @Test
    fun `the scoreboard keeps at most two batters`() {
        val board = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(
                batsmen = listOf(
                    batsman("Warner", "95", "159", striker = true),
                    batsman("Labuschagne", "63", "103", striker = false),
                    batsman("Khawaja", "142", "233", striker = false),
                ),
            ),
        ).scoreboard
        assertEquals(2, board.batters.size)
        assertFalse(board.batters.map { it.name }.toString(), board.batters.any { it.name == "Khawaja" })
    }

    /** The expanded text is a flattening of the scoreboard, not a second derivation of the payload. */
    @Test
    fun `the expanded text is derived from the scoreboard`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(batsmen = listOf(batsman("Warner", "95", "159", striker = true))),
        )
        assertTrue(content.bigText, content.bigText.contains("Warner 95 (159)*"))
        assertEquals("Warner", content.scoreboard.batters.single().name)
    }

    /** The other side's crest is only drawn by the custom layout, but it is resolved for both. */
    @Test
    fun `the opponent logo resolves to the side that is not batting`() {
        val content = buildLiveUpdateContent(MATCH_ID, match(), payload())
        assertEquals("Australia", content.logoTeamName)
        assertEquals("England", content.opponentLogoTeamName)
    }

    // ── situation as the authority ───────────────────────────────────────────

    /**
     * `battingTeamIsTeam1` is compared by team id on the server, which is the only comparison that
     * holds for a side batting its second innings in a Test. The score-string match below is the
     * fallback for rows written before the field existed.
     */
    @Test
    fun `the situation names the batting side even when the scores would not`() {
        val content = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(score = "unparseable", situation = situation(battingTeamIsTeam1 = true)),
        )
        assertEquals("ENG", content.accentTeamName)
        assertEquals("England", content.logoTeamName)
    }

    @Test
    fun `the situation and the score strings agree on the batting side`() {
        val fromStrings = buildLiveUpdateContent(MATCH_ID, match(), payload())
        val fromSituation = buildLiveUpdateContent(
            MATCH_ID,
            match(),
            payload(situation = situation(battingTeamIsTeam1 = false)),
        )
        assertEquals(fromStrings.accentTeamName, fromSituation.accentTeamName)
        assertEquals(fromStrings.logoTeamName, fromSituation.logoTeamName)
    }

    private fun situation(
        matchFormat: String? = null,
        battingTeamIsTeam1: Boolean? = null,
        runsNeeded: Int? = null,
        oversRemaining: Double? = null,
        requiredRunRate: Double? = null,
    ) = LiveSituation(
        matchFormat = matchFormat,
        battingTeamIsTeam1 = battingTeamIsTeam1,
        runsNeeded = runsNeeded,
        oversRemaining = oversRemaining,
        requiredRunRate = requiredRunRate,
    )

    private companion object {
        const val MATCH_ID = "170667"
    }
}
