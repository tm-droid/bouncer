package com.tanush.bouncer.demo

import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.network.CricBuzzScraper

/**
 * Deterministic, camera-ready match timeline for the showcase video:
 * India chasing 187 vs Australia, 3rd T20I, death overs.
 *
 * All data is keyed off elapsed milliseconds since [ShowcaseActivity] launch so
 * every take is identical. One tick per live-poll interval (6s) advances the chase.
 */
internal object ShowcaseScript {

    const val MATCH_ID = "showcase-1"
    private const val TICK_MS = 6_000L

    private data class Tick(
        val indScore: String,
        val indOvers: String,
        val statusLine: String,
        val crr: String,
        val rrr: String,
        val recentBalls: List<String>,
        val striker: ScrapedBatsman,
        val nonStriker: ScrapedBatsman,
        val bowler: ScrapedBowler,
        val lastWicket: String,
    )

    private fun batsman(name: String, runs: Int, balls: Int, fours: Int, sixes: Int, sr: String, onStrike: Boolean) =
        ScrapedBatsman(
            name = name,
            runs = runs.toString(),
            balls = balls.toString(),
            fours = fours.toString(),
            sixes = sixes.toString(),
            strikeRate = sr,
            isStriker = onStrike,
        )

    private val cummins = ScrapedBowler(
        name = "Pat Cummins", overs = "3.1", maidens = "0", runs = "32", wickets = "1", economy = "10.10"
    )
    private val cumminsLater = ScrapedBowler(
        name = "Pat Cummins", overs = "4.0", maidens = "0", runs = "38", wickets = "2", economy = "9.50"
    )
    private val starc = ScrapedBowler(
        name = "Mitchell Starc", overs = "3.4", maidens = "0", runs = "44", wickets = "1", economy = "12.00"
    )

    private val kohliWicket = "Kohli c Marsh b Cummins 47 (31)"
    private val pandyaWicket = "Pandya c Maxwell b Cummins 30 (15)"

    private val ticks = listOf(
        Tick(
            indScore = "158/4", indOvers = "17.1", statusLine = "India need 29 runs in 17 balls",
            crr = "9.20", rrr = "10.24", recentBalls = listOf("1", "4", "6", "W", "2", "1"),
            striker = batsman(name = "Suryakumar Yadav", runs = 67, balls = 38, fours = 6, sixes = 3, sr = "176.32", onStrike = true),
            nonStriker = batsman(name = "Hardik Pandya", runs = 24, balls = 11, fours = 2, sixes = 1, sr = "218.18", onStrike = false),
            bowler = cummins, lastWicket = kohliWicket,
        ),
        Tick(
            indScore = "164/4", indOvers = "17.3", statusLine = "India need 23 runs in 15 balls",
            crr = "9.37", rrr = "9.20", recentBalls = listOf("W", "2", "1", "4", "2", "4"),
            striker = batsman(name = "Hardik Pandya", runs = 28, balls = 13, fours = 3, sixes = 1, sr = "215.38", onStrike = true),
            nonStriker = batsman(name = "Suryakumar Yadav", runs = 69, balls = 40, fours = 6, sixes = 3, sr = "172.50", onStrike = false),
            bowler = cummins, lastWicket = kohliWicket,
        ),
        Tick(
            indScore = "170/5", indOvers = "17.5", statusLine = "India need 17 runs in 13 balls",
            crr = "9.53", rrr = "7.85", recentBalls = listOf("1", "4", "2", "4", "6", "W"),
            striker = batsman(name = "Ravindra Jadeja", runs = 0, balls = 1, fours = 0, sixes = 0, sr = "0.00", onStrike = true),
            nonStriker = batsman(name = "Suryakumar Yadav", runs = 70, balls = 41, fours = 6, sixes = 3, sr = "170.73", onStrike = false),
            bowler = cumminsLater, lastWicket = pandyaWicket,
        ),
        Tick(
            indScore = "177/5", indOvers = "18.2", statusLine = "India need 10 runs in 10 balls",
            crr = "9.65", rrr = "6.00", recentBalls = listOf("4", "6", "W", "1", "2", "4"),
            striker = batsman(name = "Suryakumar Yadav", runs = 76, balls = 44, fours = 7, sixes = 3, sr = "172.73", onStrike = true),
            nonStriker = batsman(name = "Ravindra Jadeja", runs = 2, balls = 2, fours = 0, sixes = 0, sr = "100.00", onStrike = false),
            bowler = starc, lastWicket = pandyaWicket,
        ),
        Tick(
            indScore = "183/5", indOvers = "18.4", statusLine = "India need 4 runs in 8 balls",
            crr = "9.80", rrr = "3.00", recentBalls = listOf("W", "1", "2", "4", "4", "2"),
            striker = batsman(name = "Suryakumar Yadav", runs = 81, balls = 47, fours = 8, sixes = 3, sr = "172.34", onStrike = true),
            nonStriker = batsman(name = "Ravindra Jadeja", runs = 3, balls = 3, fours = 0, sixes = 0, sr = "100.00", onStrike = false),
            bowler = starc, lastWicket = pandyaWicket,
        ),
        Tick(
            indScore = "185/5", indOvers = "19.0", statusLine = "India need 2 runs in 6 balls",
            crr = "9.74", rrr = "2.00", recentBalls = listOf("2", "4", "4", "2", "1", "1"),
            striker = batsman(name = "Suryakumar Yadav", runs = 82, balls = 48, fours = 8, sixes = 3, sr = "170.83", onStrike = true),
            nonStriker = batsman(name = "Ravindra Jadeja", runs = 4, balls = 4, fours = 0, sixes = 0, sr = "100.00", onStrike = false),
            bowler = starc, lastWicket = pandyaWicket,
        ),
    )

    private fun tickAt(elapsedMs: Long): Tick =
        ticks[(elapsedMs / TICK_MS).toInt().coerceIn(0, ticks.lastIndex)]

    fun matchAt(elapsedMs: Long): CricketMatch {
        val t = tickAt(elapsedMs)
        return CricketMatch(
            id = MATCH_ID,
            seriesId = "showcase-series",
            matchTitle = "India vs Australia, 3rd T20I",
            matchType = "T20",
            status = t.statusLine,
            teams = listOf(
                CricketMatch.Team(
                    name = "India", score = t.indScore, status = "", isBatting = true,
                    logoUrl = null, acronym = "IND", overs = t.indOvers,
                ),
                CricketMatch.Team(
                    name = "Australia", score = "186/5", status = "", isBatting = false,
                    logoUrl = null, acronym = "AUS", overs = "20.0",
                ),
            ),
            venue = "Wankhede Stadium, Mumbai",
            date = "Jul 3, Thu",
            matchTime = "19:00",
            score = CricketMatch.MatchScore(
                currentInnings = "2nd Innings",
                batsmen = listOf(
                    CricketMatch.BatsmanScore(
                        name = t.striker.name, runs = t.striker.runs.toInt(), balls = t.striker.balls.toInt(),
                        fours = t.striker.fours.toInt(), sixes = t.striker.sixes.toInt(),
                        strikeRate = t.striker.strikeRate.toDouble(),
                    ),
                    CricketMatch.BatsmanScore(
                        name = t.nonStriker.name, runs = t.nonStriker.runs.toInt(), balls = t.nonStriker.balls.toInt(),
                        fours = t.nonStriker.fours.toInt(), sixes = t.nonStriker.sixes.toInt(),
                        strikeRate = t.nonStriker.strikeRate.toDouble(),
                    ),
                ),
                bowlers = listOf(
                    CricketMatch.BowlerScore(
                        name = t.bowler.name, overs = t.bowler.overs.toDouble(), maidens = t.bowler.maidens.toInt(),
                        runs = t.bowler.runs.toInt(), wickets = t.bowler.wickets.toInt(),
                        economy = t.bowler.economy.toDouble(),
                    ),
                ),
                overs = t.indOvers,
                currentRunRate = t.crr,
                requiredRunRate = t.rrr,
                lastWicket = t.lastWicket,
                recentBalls = t.recentBalls.joinToString(","),
            ),
            currentStatus = t.statusLine,
            info = "In Progress",
            recentBalls = t.recentBalls,
            currentBowler = t.bowler,
            scrapedBatsmen = listOf(t.striker, t.nonStriker),
        )
    }

    fun detailPayloadAt(elapsedMs: Long): MatchLiveDetailPayload {
        val t = tickAt(elapsedMs)
        return MatchLiveDetailPayload(
            batsmen = listOf(t.striker, t.nonStriker),
            bowler = t.bowler,
            recentBalls = t.recentBalls,
            score = "${t.indScore} (${t.indOvers})",
            team1Score = t.indScore,
            team1Overs = t.indOvers,
            team2Score = "186/5",
            team2Overs = "20.0",
            currentRunRate = t.crr,
            requiredRunRate = t.rrr,
            scorecard = fullScorecard,
            status = t.statusLine,
        )
    }

    fun scrapedDetailsAt(elapsedMs: Long): CricBuzzScraper.ScrapedMatchDetails {
        val t = tickAt(elapsedMs)
        return CricBuzzScraper.ScrapedMatchDetails(
            recentBalls = t.recentBalls,
            overNumbers = listOf("17", "17", "17", "18", "18", "18"),
            batsmen = listOf(t.striker, t.nonStriker),
            bowler = t.bowler,
            recentBallsSummary = t.recentBalls.joinToString(" "),
            score = "${t.indScore} (${t.indOvers})",
            team1Score = t.indScore,
            team2Score = "186/5",
            team1Overs = t.indOvers,
            team2Overs = "20.0",
            currentRunRate = t.crr,
            requiredRunRate = t.rrr,
        )
    }

    private val fullScorecard = Scorecard(
        innings = listOf(
            Innings(
                teamName = "Australia",
                score = "186/5",
                overs = "20.0",
                batsmen = listOf(
                    BatsmanStats(name = "Travis Head", dismissal = "c Kohli b Bumrah", runs = "62", balls = "41", fours = "7", sixes = "3", strikeRate = "151.22"),
                    BatsmanStats(name = "Mitchell Marsh", dismissal = "b Chahal", runs = "38", balls = "24", fours = "4", sixes = "2", strikeRate = "158.33"),
                    BatsmanStats(name = "Glenn Maxwell", dismissal = "c Jadeja b Arshdeep", runs = "31", balls = "17", fours = "2", sixes = "3", strikeRate = "182.35"),
                    BatsmanStats(name = "Steve Smith", dismissal = "not out", runs = "26", balls = "21", fours = "2", sixes = "0", strikeRate = "123.81"),
                    BatsmanStats(name = "Tim David", dismissal = "c Rohit b Bumrah", runs = "14", balls = "9", fours = "1", sixes = "1", strikeRate = "155.56"),
                    BatsmanStats(name = "Matthew Wade", dismissal = "not out", runs = "6", balls = "5", fours = "0", sixes = "0", strikeRate = "120.00"),
                ),
                bowlers = listOf(
                    BowlerStats(name = "Jasprit Bumrah", overs = "4.0", maidens = "0", runs = "28", wickets = "2", noBalls = "0", wides = "1", economy = "7.00"),
                    BowlerStats(name = "Arshdeep Singh", overs = "4.0", maidens = "0", runs = "39", wickets = "1", noBalls = "0", wides = "2", economy = "9.75"),
                    BowlerStats(name = "Yuzvendra Chahal", overs = "4.0", maidens = "0", runs = "34", wickets = "1", noBalls = "0", wides = "0", economy = "8.50"),
                    BowlerStats(name = "Hardik Pandya", overs = "4.0", maidens = "0", runs = "41", wickets = "1", noBalls = "1", wides = "1", economy = "10.25"),
                    BowlerStats(name = "Ravindra Jadeja", overs = "4.0", maidens = "0", runs = "38", wickets = "0", noBalls = "0", wides = "1", economy = "9.50"),
                ),
                fallOfWickets = listOf(
                    FallOfWicket(score = "58", overs = "6.2", batsman = "Mitchell Marsh", number = 1),
                    FallOfWicket(score = "112", overs = "12.4", batsman = "Travis Head", number = 2),
                    FallOfWicket(score = "148", overs = "16.1", batsman = "Glenn Maxwell", number = 3),
                    FallOfWicket(score = "170", overs = "18.3", batsman = "Tim David", number = 4),
                ),
                extras = "9 (lb 3, w 5, nb 1)",
                total = "186/5 (20 Ov)",
            ),
            Innings(
                teamName = "India",
                score = "170/5",
                overs = "17.5",
                batsmen = listOf(
                    BatsmanStats(name = "Rohit Sharma", dismissal = "c Head b Starc", runs = "28", balls = "19", fours = "3", sixes = "1", strikeRate = "147.37"),
                    BatsmanStats(name = "Shubman Gill", dismissal = "b Zampa", runs = "12", balls = "9", fours = "2", sixes = "0", strikeRate = "133.33"),
                    BatsmanStats(name = "Virat Kohli", dismissal = "c Marsh b Cummins", runs = "47", balls = "31", fours = "5", sixes = "1", strikeRate = "151.61"),
                    BatsmanStats(name = "Suryakumar Yadav", dismissal = "batting", runs = "70", balls = "41", fours = "6", sixes = "3", strikeRate = "170.73"),
                    BatsmanStats(name = "Hardik Pandya", dismissal = "c Maxwell b Cummins", runs = "30", balls = "15", fours = "3", sixes = "1", strikeRate = "200.00"),
                    BatsmanStats(name = "Ravindra Jadeja", dismissal = "batting", runs = "0", balls = "1", fours = "0", sixes = "0", strikeRate = "0.00"),
                ),
                bowlers = listOf(
                    BowlerStats(name = "Mitchell Starc", overs = "3.4", maidens = "0", runs = "44", wickets = "1", noBalls = "0", wides = "2", economy = "12.00"),
                    BowlerStats(name = "Pat Cummins", overs = "4.0", maidens = "0", runs = "38", wickets = "2", noBalls = "0", wides = "1", economy = "9.50"),
                    BowlerStats(name = "Adam Zampa", overs = "4.0", maidens = "0", runs = "36", wickets = "1", noBalls = "0", wides = "0", economy = "9.00"),
                    BowlerStats(name = "Glenn Maxwell", overs = "3.0", maidens = "0", runs = "29", wickets = "0", noBalls = "0", wides = "0", economy = "9.67"),
                ),
                fallOfWickets = listOf(
                    FallOfWicket(score = "44", overs = "4.3", batsman = "Shubman Gill", number = 1),
                    FallOfWicket(score = "71", overs = "8.1", batsman = "Rohit Sharma", number = 2),
                    FallOfWicket(score = "139", overs = "15.2", batsman = "Virat Kohli", number = 3),
                    FallOfWicket(score = "168", overs = "17.4", batsman = "Hardik Pandya", number = 4),
                ),
                extras = "7 (lb 2, w 5)",
                total = "170/5 (17.5 Ov)",
            ),
        ),
    )

    /** Newest-first, matching AppwriteMatchCommentaryClient.getForMatch ordering. */
    fun commentaryLines(): List<String> = listOf(
        "SIX! Suryakumar walks across and flicks it over fine leg — pure audacity. The chase is back on.",
        "Cummins strikes! Pandya holes out to Maxwell at long-off. Australia claw their way back into this.",
        "FOUR! Short and wide, Suryakumar slashes it over point. India need 17 off 13.",
        "Clever from Jadeja — opens the face and steals two behind point. Every run gold now.",
        "Kohli falls for a classy 47. Marsh takes a screamer at deep midwicket and Wankhede goes silent.",
        "Back-to-back boundaries! Pandya is dealing in fours and the required rate is tumbling.",
        "Zampa finishes with 1 for 36. Composed spell in a game moving at a hundred miles an hour.",
        "Fifty for Suryakumar — 29 balls, and he brings it up with a scoop off the fastest bowler on the planet.",
    )

    /** Timed lines for the Realtime commentary stream (drives the typewriter reveal). */
    fun streamingCommentary(): List<String> = listOf(
        "FOUR! Suryakumar threads the gap at extra cover — nobody moves at Wankhede, they just watch.",
        "Cummins strikes! Pandya holes out to Maxwell at long-off. Game on again.",
        "Jadeja gets off the mark, and the equation slips under ten off ten.",
        "SIX! Suryakumar again — over the keeper's head! This man bats on a different planet.",
        "Two more into the off side. India a boundary away from sealing the series.",
        "Singles traded now — Australia need a miracle, India need to breathe.",
    )

    /** Probabilities on the 0..1 scale, team1 = India. Scripted for a dramatic swing. */
    fun probabilityHistory(): List<WinProbabilityPoint> = listOf(
        WinProbabilityPoint(over = 2f, team1Probability = 0.52f, team2Probability = 0.48f),
        WinProbabilityPoint(over = 3f, team1Probability = 0.55f, team2Probability = 0.45f),
        WinProbabilityPoint(over = 4f, team1Probability = 0.48f, team2Probability = 0.52f),
        WinProbabilityPoint(over = 5f, team1Probability = 0.42f, team2Probability = 0.58f, keyEvent = "Gill b Zampa 12"),
        WinProbabilityPoint(over = 6f, team1Probability = 0.38f, team2Probability = 0.62f),
        WinProbabilityPoint(over = 7f, team1Probability = 0.33f, team2Probability = 0.67f),
        WinProbabilityPoint(over = 8f, team1Probability = 0.26f, team2Probability = 0.74f, keyEvent = "Rohit c Head b Starc 28"),
        WinProbabilityPoint(over = 9f, team1Probability = 0.22f, team2Probability = 0.78f),
        WinProbabilityPoint(over = 10f, team1Probability = 0.18f, team2Probability = 0.82f),
        WinProbabilityPoint(over = 11f, team1Probability = 0.24f, team2Probability = 0.76f),
        WinProbabilityPoint(over = 12f, team1Probability = 0.31f, team2Probability = 0.69f, keyEvent = "18 off the over"),
        WinProbabilityPoint(over = 13f, team1Probability = 0.37f, team2Probability = 0.63f),
        WinProbabilityPoint(over = 14f, team1Probability = 0.45f, team2Probability = 0.55f),
        WinProbabilityPoint(over = 15f, team1Probability = 0.41f, team2Probability = 0.59f, keyEvent = "Kohli c Marsh b Cummins 47"),
        WinProbabilityPoint(over = 16f, team1Probability = 0.55f, team2Probability = 0.45f),
        WinProbabilityPoint(over = 17f, team1Probability = 0.68f, team2Probability = 0.32f, keyEvent = "22 off the over"),
        WinProbabilityPoint(over = 18f, team1Probability = 0.78f, team2Probability = 0.22f),
        WinProbabilityPoint(over = 19f, team1Probability = 0.85f, team2Probability = 0.15f),
    )

    fun upcomingMatches(): List<CricketMatch> = listOf(
        fixture(
            id = "showcase-up-1", title = "England vs South Africa, 1st ODI",
            team1 = "England" to "ENG", team2 = "South Africa" to "SA",
            status = "", venue = "Lord's, London", date = "Jul 5, Sat", time = "15:00",
        ),
        fixture(
            id = "showcase-up-2", title = "West Indies vs Sri Lanka, 2nd T20I",
            team1 = "West Indies" to "WI", team2 = "Sri Lanka" to "SL",
            status = "", venue = "Kensington Oval, Barbados", date = "Jul 6, Sun", time = "23:30",
        ),
    )

    fun recentMatches(): List<CricketMatch> = listOf(
        fixture(
            id = "showcase-re-1", title = "Pakistan vs New Zealand, 3rd T20I",
            team1 = "Pakistan" to "PAK", team2 = "New Zealand" to "NZ",
            status = "New Zealand won by 21 runs", venue = "Gaddafi Stadium, Lahore", date = "Jul 1, Tue", time = "19:30",
            team1Score = "167/8", team2Score = "188/6", team1Overs = "20.0", team2Overs = "20.0",
        ),
        fixture(
            id = "showcase-re-2", title = "Bangladesh vs Afghanistan, 2nd ODI",
            team1 = "Bangladesh" to "BAN", team2 = "Afghanistan" to "AFG",
            status = "Bangladesh won by 4 wickets", venue = "Shere Bangla Stadium, Dhaka", date = "Jun 30, Mon", time = "14:00",
            team1Score = "245/6", team2Score = "244/9", team1Overs = "48.3", team2Overs = "50.0",
        ),
    )

    @Suppress("LongParameterList") // rule is disabled project-wide; annotation documents intent
    private fun fixture(
        id: String,
        title: String,
        team1: Pair<String, String>,
        team2: Pair<String, String>,
        status: String,
        venue: String,
        date: String,
        time: String,
        team1Score: String = "",
        team2Score: String = "",
        team1Overs: String = "0.0",
        team2Overs: String = "0.0",
    ): CricketMatch = CricketMatch(
        id = id,
        seriesId = "$id-series",
        matchTitle = title,
        matchType = if (title.contains("T20")) "T20" else "ODI",
        status = status,
        teams = listOf(
            CricketMatch.Team(
                name = team1.first, score = team1Score, status = "", isBatting = false,
                logoUrl = null, acronym = team1.second, overs = team1Overs,
            ),
            CricketMatch.Team(
                name = team2.first, score = team2Score, status = "", isBatting = false,
                logoUrl = null, acronym = team2.second, overs = team2Overs,
            ),
        ),
        venue = venue,
        date = date,
        matchTime = time,
        score = null,
        currentStatus = status,
        info = "",
    )
}
