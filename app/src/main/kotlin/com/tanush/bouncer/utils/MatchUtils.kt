package com.tanush.bouncer.utils

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal
import io.sentry.Sentry

private const val TAG = "MatchUtils"

private val RAIN_RE = """\brain\b""".toRegex()
private val TEA_RE = """\btea\b""".toRegex()

/**
 * iOS Parity: Detailed Match Phase Classification
 * Matches iOS's MatchPhase enum for consistent status handling
 */
enum class MatchPhase(val displayName: String, val icon: String) {
    TOSS("Toss", "🪙"),
    BATTING_1("1st Innings", "🏏"),
    INNINGS_BREAK("Break", "⏸️"),
    BATTING_2("2nd Innings", "🏏"),
    CHASING("Chase", "🎯"),
    STRATEGIC_TIMEOUT("Timeout", "⏱️"),
    DRINKS_BREAK("Drinks", "🍹"),
    RAIN_DELAY("Rain Delay", "🌧️"),
    BAD_LIGHT("Bad Light", "🌙"),
    LUNCH("Lunch", "🍽️"),
    TEA("Tea", "☕"),
    STUMPS("Stumps", "🏏"),
    RESULT("Result", "🏆"),
    ABANDONED("Abandoned", "❌"),
    NO_RESULT("No Result", "⚠️"),
    UPCOMING("Upcoming", "📅"),
    UNKNOWN("Unknown", "❓")
}

/**
 * Shared UI Models for Match Data
 */
data class MatchStatusInfo(
    val text: String,
    val color: Color,
    val isLive: Boolean = false,
    val phase: MatchPhase = MatchPhase.UNKNOWN
)

/**
 * Get detailed match phase from status string and match data (iOS Parity)
 */
fun getMatchPhase(status: String?, match: CricketMatch? = null): MatchPhase {
    try {
        val s = status?.lowercase() ?: ""
        // The coarse live/complete axis comes from the shared classifier; this function only
        // decides the finer phase within it. Previously `contains("won")` was tested first and
        // unconditionally, so "Rain delay, India won the toss" rendered as Finished.
        val signal = MatchStatusClassifier.classify(status)

        return when {
            // Result states — reachable only when the classifier agrees the match is over.
            signal == StatusSignal.TERMINAL && s.contains("abandoned") -> MatchPhase.ABANDONED
            signal == StatusSignal.TERMINAL && s.contains("no result") -> MatchPhase.NO_RESULT
            signal == StatusSignal.TERMINAL -> MatchPhase.RESULT

            // Breaks and delays. Word-anchored so "rain" cannot fire inside "training"/"Bahrain"
            // and "tea" cannot fire inside "team" — the latter previously needed an explicit
            // `&& !s.contains("team")` guard, which is what chasing substrings one at a time
            // looks like.
            RAIN_RE.containsMatchIn(s) || s.contains("stopped") -> MatchPhase.RAIN_DELAY
            s.contains("bad light") -> MatchPhase.BAD_LIGHT
            s.contains("lunch") -> MatchPhase.LUNCH
            TEA_RE.containsMatchIn(s) -> MatchPhase.TEA
            s.contains("stumps") -> MatchPhase.STUMPS
            s.contains("drinks") -> MatchPhase.DRINKS_BREAK
            s.contains("timeout") || s.contains("strategic") -> MatchPhase.STRATEGIC_TIMEOUT
            s.contains("innings break") || s.contains("inn break") -> MatchPhase.INNINGS_BREAK
        
            // Toss
            s.contains("toss") || s.contains("opted to") -> MatchPhase.TOSS
        
            // Chase detection
            s.contains("need") || s.contains("chase") || s.contains("require") || s.contains("target") -> MatchPhase.CHASING
        
            // Live innings detection - check if 2nd innings based on match data
            s.contains("live") || s.contains("batting") || s.contains("bowling") || 
            s.contains("day ") || s.contains("session") || s.contains("in progress") -> {
                // Try to determine which innings
                match?.let {
                    val team1HasScore = it.teams.getOrNull(0)?.score?.isNotBlank() == true
                    val team2HasScore = it.teams.getOrNull(1)?.score?.isNotBlank() == true
                    val team2Batting = it.teams.getOrNull(1)?.isBatting == true
                
                    when {
                        team2Batting && team1HasScore -> MatchPhase.CHASING
                        team1HasScore && team2HasScore -> MatchPhase.BATTING_2
                        else -> MatchPhase.BATTING_1
                    }
                } ?: MatchPhase.BATTING_1
            }
        
            // Coming soon
            s.contains("upcoming") || s.contains("scheduled") || s.isBlank() -> MatchPhase.UPCOMING
        
            else -> MatchPhase.UNKNOWN
        }
    } catch (e: Exception) {
        // Composition-reachable via getMatchStatusInfo; UNKNOWN is the defined "cannot classify"
        // value, so return it rather than crashing the screen on an unexpected status string.
        Sentry.captureException(e)
        return MatchPhase.UNKNOWN
    }
}

data class ScoreInfo(
    val runs: Int,
    val wickets: Int,
    val overs: String,
    val rawScore: String = "", // Original score string for fallback display
    val isValid: Boolean = false // Whether parsing was successful
)

data class TeamVisuals(
    val color: Color
)

/**
 * Utility functions for match-related data
 */
fun getMatchStatusInfo(status: String?, match: CricketMatch? = null): MatchStatusInfo {
    try {
        val s = status?.lowercase() ?: ""
        val phase = getMatchPhase(status, match)
    
        return when (phase) {
            MatchPhase.RESULT -> MatchStatusInfo("Finished", SuccessGreen, phase = phase)
            MatchPhase.ABANDONED -> MatchStatusInfo("Abandoned", ErrorRed, phase = phase)
            MatchPhase.NO_RESULT -> MatchStatusInfo("No Result", ErrorRed, phase = phase)
        
            MatchPhase.RAIN_DELAY -> MatchStatusInfo("Rain Delay", WarningOrange, true, phase)
            MatchPhase.BAD_LIGHT -> MatchStatusInfo("Bad Light", WarningOrange, true, phase)
            MatchPhase.LUNCH, MatchPhase.TEA -> MatchStatusInfo(phase.displayName, WarningOrange, true, phase)
            MatchPhase.STUMPS -> MatchStatusInfo("Stumps", WarningOrange, phase = phase)
            MatchPhase.DRINKS_BREAK -> MatchStatusInfo("Drinks", WarningOrange, true, phase)
            MatchPhase.STRATEGIC_TIMEOUT -> MatchStatusInfo("Timeout", WarningOrange, true, phase)
            MatchPhase.INNINGS_BREAK -> MatchStatusInfo("Break", WarningOrange, true, phase)
        
            MatchPhase.TOSS -> MatchStatusInfo("Toss", LiveBlue, true, phase)
            MatchPhase.CHASING -> MatchStatusInfo("Chase", ChartCyan, true, phase)
            MatchPhase.BATTING_1 -> MatchStatusInfo("1st Innings", LiveBlue, true, phase)
            MatchPhase.BATTING_2 -> MatchStatusInfo("2nd Innings", LiveBlue, true, phase)
        
            MatchPhase.UPCOMING -> MatchStatusInfo("Upcoming", GoldDark, phase = phase)
            MatchPhase.UNKNOWN -> {
                // Fallback to original logic for unknown states
                when {
                    s.contains("live") || s.contains("in progress") -> 
                        MatchStatusInfo("Live", LiveBlue, true, phase)
                    s.contains("delayed") -> 
                        MatchStatusInfo("Delayed", WarningOrange, phase = phase)
                    s.contains("break") -> 
                        MatchStatusInfo("Break", WarningOrange, phase = phase)
                    else -> MatchStatusInfo("Upcoming", GoldDark, phase = phase)
                }
            }
        }
    } catch (e: Exception) {
        // Its sole caller renders inside a composable, so a throw here takes the screen down over
        // an unparseable status. Fall back to the same neutral label an unknown status produces.
        Sentry.captureException(e)
        return MatchStatusInfo("Upcoming", GoldDark, phase = MatchPhase.UNKNOWN)
    }
}

private val placeholderScores = setOf("Data Not Found", "-", "TBD")

/**
 * Parse cricket score from various formats:
 * - "199/7" or "199-7"
 * - "199/7 (20.0)" or "199-7 (20.0)"
 * - "199/7 (20.0 Ov)"
 * - "&amp; 123/4" for second innings
 * - "266 &amp; 83/2 (25.1)" for a Test side that has batted twice
 *
 * The numbers describe the innings being batted now, so a multi-innings score parses from its last
 * segment; [rawScore] keeps the whole string, which is what the score cells render.
 * Returns ScoreInfo with rawScore as fallback for display
 */
fun parseScore(score: String?): ScoreInfo {
    try {
        if (score.isNullOrBlank() || score in placeholderScores) {
            return ScoreInfo(0, 0, "", score ?: "-", false)
        }

        val cleanedScore = score
            .replace("&amp;", "&")
            .trim()

        if (cleanedScore.isBlank()) return ScoreInfo(0, 0, "", score, false)

        return try {
            val liveInnings = lastInningsSegment(cleanedScore)
            val overs = extractOvers(liveInnings)
            // Validity is decided by whether a score actually parsed, not by whether the string
            // happens to contain a separator. The old test — `contains("/") || contains("-")` —
            // made "Not-Started" a valid 0/0, which rendered as the score "0/0".
            val parsed = parseRunsAndWickets(scorePartOf(liveInnings))
                ?: return ScoreInfo(0, 0, overs, score, false)

            ScoreInfo(parsed.first, parsed.second, overs, score, true)
        } catch (e: Exception) {
            // An unparseable score is a normal outcome for an upcoming or abandoned match, so this
            // degrades rather than throws — but the reason is kept, because a parser that has
            // started failing on live scores looks identical to one that has not from here.
            Log.d(TAG, "Could not parse score \"$score\": ${e.message}")
            ScoreInfo(0, 0, "", score, false)
        }
    } catch (e: Exception) {
        // Reached during composition (EliteUIComponents, PredictionMatchCard) on scraped text, so
        // rethrowing crashes the UI over a malformed score. Report it and return the
        // already-defined invalid sentinel; the caller falls back to displaying the raw string.
        Sentry.captureException(e)
        return ScoreInfo(0, 0, "", score ?: "-", false)
    }
}

/** Extract just the runs from a score string like "199/7 (20.0 Ov)". Returns 0 on failure. */
fun parseRuns(score: String?): Int = parseScore(score).runs

/** Extract just the wickets from a score string like "199/7 (20.0 Ov)". Returns 0 on failure. */
fun parseWickets(score: String?): Int = parseScore(score).wickets

/**
 * Get a displayable score string - uses parsed values if valid, otherwise raw score
 */
fun ScoreInfo.toDisplayString(): String {
    try {
        val innings = inningsSegments(rawScore.replace("&amp;", "&").substringBefore("("))
        return when {
            // A Test side's completed innings is part of its score, not decoration. Collapsing to
            // the parsed numbers here would put "83/2" where "266 & 83/2" belongs.
            innings.size > 1 -> innings.joinToString(" & ") { it.replace("-", "/").trim() }
            isValid -> "$runs/$wickets"
            rawScore.isNotBlank() && rawScore != "-" && rawScore != "Data Not Found" -> 
                rawScore.replace("-", "/").substringBefore("(").trim()
            else -> "-"
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Get full score with overs if available
 */
fun ScoreInfo.toFullDisplayString(): String {
    try {
        val scoreStr = toDisplayString()
        return if (overs.isNotBlank() && overs != "0.0") {
            "$scoreStr ($overs)"
        } else {
            scoreStr
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

// Order matters: "south africa" must precede "africa"-only checks; substring match preserved.
private val teamColorMap: List<Pair<String, Color>> = listOf(
    "india" to TeamIndia,
    "australia" to TeamAustralia,
    "england" to TeamEngland,
    "pakistan" to TeamPakistan,
    "south africa" to TeamSouthAfrica,
    "new zealand" to TeamNewZealand,
    "west indies" to TeamWestIndies,
    "sri lanka" to TeamSriLanka,
    "bangladesh" to TeamBangladesh,
    "afghanistan" to Color(0xFF0045AD),
    "ireland" to Color(0xFF009A44),
    "scotland" to Color(0xFF003087),
    "zimbabwe" to Color(0xFFD40000),
)

fun getTeamColor(teamName: String?): Color {
    try {
        val name = teamName?.lowercase() ?: return GoldDark
        return teamColorMap.firstOrNull { (key, _) -> name.contains(key) }?.second ?: GoldDark
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
