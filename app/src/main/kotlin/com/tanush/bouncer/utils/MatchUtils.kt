package com.tanush.bouncer.utils

import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Sentry

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
    
        return when {
            // Result states
            s.contains("won") || s.contains("drawn") || s.contains("tied") -> MatchPhase.RESULT
            s.contains("abandoned") -> MatchPhase.ABANDONED
            s.contains("no result") -> MatchPhase.NO_RESULT
        
            // Breaks and delays
            s.contains("rain") || s.contains("stopped") -> MatchPhase.RAIN_DELAY
            s.contains("bad light") -> MatchPhase.BAD_LIGHT
            s.contains("lunch") -> MatchPhase.LUNCH
            s.contains("tea") && !s.contains("team") -> MatchPhase.TEA
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
        Sentry.captureException(e)
        throw e
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
        Sentry.captureException(e)
        throw e
    }
}

private val placeholderScores = setOf("Data Not Found", "-", "TBD")

private fun parseRunsWickets(scorePart: String, separator: Char): Pair<Int, Int> {
    val parts = scorePart.split(separator)
    val r = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val w = parts.getOrNull(1)?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    return r to w
}

/**
 * Parse cricket score from various formats:
 * - "199/7" or "199-7"
 * - "199/7 (20.0)" or "199-7 (20.0)"
 * - "199/7 (20.0 Ov)"
 * - "&amp; 123/4" for second innings
 * Returns ScoreInfo with rawScore as fallback for display
 */
fun parseScore(score: String?): ScoreInfo {
    try {
        if (score.isNullOrBlank() || score in placeholderScores) {
            return ScoreInfo(0, 0, "", score ?: "-", false)
        }

        val cleanedScore = score
            .replace("&amp;", "&")
            .replace("& ", "")
            .replace("&", "")
            .trim()

        if (cleanedScore.isBlank()) return ScoreInfo(0, 0, "", score, false)

        return try {
            val overs = when {
                cleanedScore.contains("(") && cleanedScore.contains(")") -> {
                    cleanedScore.substringAfter("(")
                        .substringBefore(")")
                        .replace("Ov", "")
                        .replace("ov", "")
                        .trim()
                }
                cleanedScore.lowercase().contains("ov") -> {
                    cleanedScore.lowercase()
                        .substringBefore("ov")
                        .trim()
                        .split(" ")
                        .lastOrNull() ?: ""
                }
                else -> ""
            }

            val scorePart = cleanedScore
                .substringBefore("(")
                .substringBefore(" Ov")
                .substringBefore(" ov")
                .trim()

            val (runs, wickets) = when {
                scorePart.contains("/") -> parseRunsWickets(scorePart, '/')
                scorePart.contains("-") -> parseRunsWickets(scorePart, '-')
                scorePart.all { it.isDigit() } -> (scorePart.toIntOrNull() ?: 0) to 0
                else -> 0 to 0
            }

            // Valid for "0/1"-style edge cases where runs==0 but a wicket was recorded
            val isValid = runs > 0 || wickets > 0 || cleanedScore.contains("/") || cleanedScore.contains("-")

            ScoreInfo(runs, wickets, overs, score, isValid)
        } catch (e: Exception) {
            ScoreInfo(0, 0, "", score, false)
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
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
        return when {
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
