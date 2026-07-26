package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.google.gson.annotations.SerializedName
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.WinProbabilityHeuristic
import com.tanush.bouncer.utils.parseRuns
import com.tanush.bouncer.utils.parseWickets
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel

// A drawn/tied/no-result match has no winner; both sides read as an even 50.
private val RESULT_NO_WINNER = """\b(drawn|tied|no\s+result|abandon(ed)?)\b""".toRegex(RegexOption.IGNORE_CASE)

sealed class MatchStatus {
    object Live : MatchStatus()
    object Completed : MatchStatus()
    object Upcoming : MatchStatus()
    data class Unknown(val raw: String) : MatchStatus()

    companion object {
        fun from(status: String): MatchStatus = when (MatchStatusClassifier.classify(status)) {
            StatusSignal.TERMINAL -> Completed
            StatusSignal.IN_PROGRESS -> Live
            StatusSignal.PRE_MATCH -> Upcoming
            // A blank status has always meant "not started yet"; a non-blank one we cannot read
            // is genuinely unknown and is surfaced rather than guessed at.
            null -> if (status.isBlank()) Upcoming else {
                sentryBreadcrumb(
                    category = "match.status",
                    message = "Unclassified match status",
                    level = SentryLevel.WARNING,
                    data = mapOf("status" to status),
                )
                Unknown(status)
            }
        }
    }
}

@Stable
data class CricketMatch(
    val id: String,
    val seriesId: String,
    val matchTitle: String,
    val matchType: String,
    val status: String,
    val teams: List<Team>,
    val venue: String,
    val date: String,
    val matchTime: String,
    val score: MatchScore?,
    val currentStatus: String,
    val info: String,
    // Scraped live data
    val recentBalls: List<String>? = null,
    val currentBowler: ScrapedBowler? = null,
    val scrapedBatsmen: List<ScrapedBatsman>? = null,
    // Add category for filtering
    val category: String? = null,
    val gender: String = "Men",
    val competitionType: String = "International",
    val winProbability: Double = 50.0,
    // Toss and officials — populated when available from live match page
    val tossWinner: String? = null,
    val tossDecision: String? = null,
    val umpire1: String? = null,
    val umpire2: String? = null,
) {
    // Classified over BOTH status fields at once. Either field can be stale or terse on its own,
    // and precedence resolves the disagreement correctly: a stale status="Live" alongside a
    // currentStatus of "India won by 5 wkts" is Completed, because terminal outranks in-progress.
    // This replaces an explicit force-to-Live bias that existed only to paper over the old
    // first-match-wins ordering, and which made exactly that case resolve Live.
    val matchStatus: MatchStatus by lazy { MatchStatus.from("$status $currentStatus") }

    val isLive: Boolean
        get() = matchStatus == MatchStatus.Live

    val isCompleted: Boolean
        get() = matchStatus == MatchStatus.Completed

    val isUpcoming: Boolean
        get() = matchStatus == MatchStatus.Upcoming

    /**
     * Win probability with the side it belongs to made explicit.
     *
     * Prefer this over [calculateHeuristicProbability] wherever the batting side matters — the
     * latter is a team-1-oriented convenience and cannot express "undetermined".
     */
    fun winProbability(): WinProbability =
        if (isCompleted) completedWinProbability() else liveWinProbability()

    /** Reads the winner out of the result string. */
    private fun completedWinProbability(): WinProbability {
        val combined = "$currentStatus $status"
        if (RESULT_NO_WINNER.containsMatchIn(combined)) {
            return WinProbability.forTeam(0, WinProbability.EVEN, WinProbability.Confidence.HIGH)
        }
        val winnerIndex = teams.indexOfFirst { team -> mentionsTeam(combined, team) }
        return if (winnerIndex == -1) {
            WinProbability.UNDETERMINED
        } else {
            WinProbability.forTeam(winnerIndex, 100.0, WinProbability.Confidence.HIGH)
        }
    }

    /** Everything the heuristic needs, or null when the live payload cannot supply it. */
    private class LiveSituation(
        val battingIndex: Int,
        val format: CricketConstants.MatchFormat,
        val ballsBowled: Int,
        val runs: Int?,
        val wicketsLost: Int,
        val requiredRunRate: Double?,
    )

    private fun liveSituation(): LiveSituation? {
        val battingIndex = teams.indexOfFirst { it.isBatting }
        if (battingIndex == -1) return null
        val matchScore = score ?: return null
        val ballsBowled = CricketConstants.ballsFromOvers(matchScore.overs) ?: return null
        val battingScore = teams[battingIndex].score

        return LiveSituation(
            battingIndex = battingIndex,
            // Format drives the ball budget. UNKNOWN and TEST have none, and the model declines
            // to score rather than falling back to T20's 120 the way the previous version did.
            format = CricketConstants.formatFromMatchType(matchType, matchTitle),
            ballsBowled = ballsBowled,
            runs = battingScore.takeIf { it.isNotBlank() }?.let(::parseRuns),
            wicketsLost = battingScore.takeIf { it.isNotBlank() }?.let(::parseWickets) ?: 0,
            requiredRunRate = matchScore.requiredRunRate?.toDoubleOrNull(),
        )
    }

    private fun liveWinProbability(): WinProbability {
        val situation = liveSituation() ?: return WinProbability.UNDETERMINED
        val probability = with(situation) {
            when {
                requiredRunRate != null -> WinProbabilityHeuristic
                    .evaluateChase(format, requiredRunRate, ballsBowled, wicketsLost)
                runs != null -> WinProbabilityHeuristic
                    .evaluateFirstInnings(format, runs, ballsBowled, wicketsLost)
                else -> null
            }
        } ?: return WinProbability.UNDETERMINED

        return WinProbability.forTeam(
            teamIndex = situation.battingIndex,
            probability = probability,
            // A chase has a target to measure against; a first innings does not.
            confidence = if (situation.requiredRunRate != null) {
                WinProbability.Confidence.HIGH
            } else {
                WinProbability.Confidence.LOW
            },
        )
    }

    /**
     * Win probability oriented to `teams[0]`, 0..100. Returns 50.0 when undetermined.
     *
     * Note this is now genuinely team 1's probability. It previously returned the *batting*
     * team's, so it was inverted whenever `teams[1]` was at the crease.
     */
    fun calculateHeuristicProbability(): Double = winProbability().forTeam1

    /**
     * Word-anchored so a team name cannot match inside a longer one — "India" is a substring of
     * "West Indies", which would otherwise credit a West Indies win to India.
     */
    private fun mentionsTeam(text: String, team: Team): Boolean {
        val candidates = listOf(team.name, team.acronym).filter { it.isNotBlank() }
        return candidates.any { candidate ->
            """\b${Regex.escape(candidate)}\b""".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
    }

    @Immutable
    data class Team(
        val name: String,
        val score: String,
        val status: String,
        val isBatting: Boolean,
        val logoUrl: String?,
        val acronym: String = "",
        val overs: String = "0.0"
    )

    @Immutable
    data class MatchScore(
        @SerializedName("inningsBreak") val currentInnings: String,
        @SerializedName("batsmanScores") val batsmen: List<BatsmanScore>,
        @SerializedName("bowlerScores") val bowlers: List<BowlerScore>,
        // Additional properties for live activity content state
        val overs: String?,
        val currentRunRate: String?,
        val requiredRunRate: String?,
        val lastWicket: String?,
        val recentBalls: String?
    )

    @Immutable
    data class BatsmanScore(
        val name: String,
        val runs: Int,
        val balls: Int,
        val fours: Int,
        val sixes: Int,
        val strikeRate: Double
    )

    @Immutable
    data class BowlerScore(
        val name: String,
        val overs: Double,
        val maidens: Int,
        val runs: Int,
        val wickets: Int,
        val economy: Double
    )
}
