package com.tanush.bouncer.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel

// A drawn/tied/no-result match has no winner; both sides read as an even 50.
private val RESULT_NO_WINNER = """\b(drawn|tied|no\s+result|abandon(ed)?)\b""".toRegex(RegexOption.IGNORE_CASE)
private const val EVEN_PROBABILITY = 50.0

sealed class MatchStatus {
    object Live : MatchStatus()
    object Completed : MatchStatus()
    object Upcoming : MatchStatus()
    @Serializable
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

@Serializable
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
    /**
     * Win probability for `teams[0]`, 0..100, or null when the server has not supplied one.
     *
     * Never computed on device. It is produced by the bouncer-match-sync Appwrite function and
     * arrives either in the `live-winprob` bucket file (match lists) or the match-live-detail
     * realtime payload (detail screen). Null means "no number to show" — offline, a match not yet
     * synced, or a format the model declines to score — and every consumer must render nothing
     * rather than substituting 50.
     *
     * Null rather than a 50.0 default on purpose: 50 is also the genuine value for an even match,
     * so a sentinel of 50 is indistinguishable from a real result.
     */
    val winProbability: Double? = null,
    /**
     * Draw probability, 0..100, for the one format that has one.
     *
     * Null for every limited-overs match, and a null must not be read as zero: a T20 cannot be
     * drawn, whereas a Test sitting on an 8% draw is a real statement about the match. Renderers
     * branch on null to choose between a two- and a three-segment bar.
     */
    val winProbabilityDraw: Double? = null,
    /**
     * True when [winProbability] is a pre-match prior rather than a reading of a live position.
     *
     * The two are different claims and must not be rendered identically: a live number comes from
     * runs, balls and wickets, a pre-match one from team strength alone and nothing else.
     */
    val winProbabilityIsPrematch: Boolean = false,
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

    /** [winProbability] as a 0..1 fraction for the widgets and notification views that want one. */
    val winProbabilityFraction: Float?
        get() = winProbability?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }

    /**
     * [winProbability] re-oriented to whichever side is batting, or null when there is no number
     * or no side is marked as batting.
     *
     * The stored value is always team-1-oriented. Consumers that narrate the batting side must go
     * through this rather than reading the field directly — mixing the two orientations is what
     * made the old model report team 2's chances as team 1's.
     */
    val battingWinProbability: Double?
        get() {
            val probability = winProbability ?: return null
            return when (teams.indexOfFirst { it.isBatting }) {
                0 -> probability
                1 -> 100.0 - probability
                else -> null
            }
        }

    /**
     * Win probability for a finished match, read straight out of the result string.
     *
     * The server does not score completed matches — there is nothing to model once the result is
     * known — so this is the one place a probability is still derived locally. It is a reading of
     * a stated fact, not a prediction.
     */
    fun resolvedWinProbability(): Double? {
        if (!isCompleted) return winProbability
        val combined = "$currentStatus $status"
        if (RESULT_NO_WINNER.containsMatchIn(combined)) return EVEN_PROBABILITY
        return when (teams.indexOfFirst { team -> mentionsTeam(combined, team) }) {
            0 -> 100.0
            1 -> 0.0
            else -> null
        }
    }

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

    @Serializable
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

    @Serializable
    @Immutable
    data class MatchScore(
        @SerialName("inningsBreak") val currentInnings: String,
        @SerialName("batsmanScores") val batsmen: List<BatsmanScore>,
        @SerialName("bowlerScores") val bowlers: List<BowlerScore>,
        // Additional properties for live activity content state
        val overs: String?,
        val currentRunRate: String?,
        val requiredRunRate: String?,
        val lastWicket: String?,
        val recentBalls: String?
    )

    @Serializable
    @Immutable
    data class BatsmanScore(
        val name: String,
        val runs: Int,
        val balls: Int,
        val fours: Int,
        val sixes: Int,
        val strikeRate: Double
    )

    @Serializable
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
