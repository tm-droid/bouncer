package com.tanush.bouncer.ui.components.v2

import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.models.CricketMatch

/** What the hero's requirement line says. The UI formats these with the hero_* strings. */
sealed interface HeroLine {
    /** [balls] is null when only the runs are known; a Test never has it. */
    data class Chase(val team: String, val runs: Int, val balls: Int?) : HeroLine
    data class TestNeed(val team: String, val runs: Int) : HeroLine
    data class Lead(val team: String, val runs: Int) : HeroLine
    data class Trail(val team: String, val runs: Int) : HeroLine
    data object Level : HeroLine
    data class StatusText(val text: String) : HeroLine
}

data class CollapsedTeamScore(val label: String, val score: String?)

data class CollapsedSummary(val teams: List<CollapsedTeamScore>, val isLive: Boolean)

private val NEED_RUNS = Regex(
    """(?:^|[:\-,]\s*)([A-Za-z][\w .'&-]*?)\s+needs?\s+(\d+)\s+runs?(?:\s+(?:in|off|from)\s+(\d+)\s+balls?)?""",
    RegexOption.IGNORE_CASE,
)
private val OVERS = Regex("""^(\d+)(?:\.(\d+))?$""")

private const val BALLS_PER_OVER = 6

/** `"32.4"` is 32 overs and 4 balls = 196. Null for blank text or a ball digit above 5. */
fun ballsFromOvers(overs: String): Int? {
    val match = OVERS.matchEntire(overs.trim()) ?: return null
    val whole = match.groupValues[1].toIntOrNull() ?: return null
    val balls = match.groupValues[2].ifEmpty { "0" }
    if (balls.length != 1) return null
    val extra = balls.toInt()
    if (extra >= BALLS_PER_OVER) return null
    return whole * BALLS_PER_OVER + extra
}

fun ballsFromOvers(overs: Double): Int? = ballsFromOvers(overs.toString())

fun isTestMatch(match: CricketMatch, situation: LiveSituation? = null): Boolean {
    val format = situation?.matchFormat?.takeIf { it.isNotBlank() } ?: match.matchType
    return format.trim().equals("TEST", ignoreCase = true) ||
        format.contains("test", ignoreCase = true) && !format.contains("t20", ignoreCase = true)
}

/**
 * The requirement line of a chase. Cricbuzz's status text wins when it already states the runs;
 * [situation] fills in only when it does not. Null when no chase is under way.
 *
 * `oversRemaining` is decimal overs, not cricket notation: the server turns it into balls as
 * `round(oversRemaining * 6)` (`winprob.js` `ballsRemainingFor`), so 2.5 is 15 balls, not 17.
 */
fun chaseLineFor(match: CricketMatch, situation: LiveSituation? = null): HeroLine? {
    val test = isTestMatch(match, situation)
    for (text in listOf(match.currentStatus, match.status)) {
        val found = NEED_RUNS.find(text) ?: continue
        val runs = found.groupValues[2].toIntOrNull() ?: continue
        val team = found.groupValues[1].trim()
        if (test) return HeroLine.TestNeed(team, runs)
        return HeroLine.Chase(team, runs, found.groupValues[3].toIntOrNull())
    }
    return derivedChase(match, situation, test)
}

private fun derivedChase(match: CricketMatch, situation: LiveSituation?, test: Boolean): HeroLine? {
    val s = situation ?: return null
    val needed = runsNeeded(s)?.takeIf { it > 0 } ?: return null
    val team = battingTeamName(match, s) ?: return null
    if (test) return HeroLine.TestNeed(team, needed)
    val balls = s.oversRemaining?.takeIf { it > 0 }?.let { Math.round(it * BALLS_PER_OVER).toInt() }
    return HeroLine.Chase(team, needed, balls)
}

private fun runsNeeded(s: LiveSituation): Int? =
    s.runsNeeded ?: s.target?.let { target -> s.runsScored?.let { target - it } }

private fun battingTeamName(match: CricketMatch, situation: LiveSituation): String? {
    val first = situation.battingTeamIsTeam1
    val team = when {
        first != null -> match.teams.getOrNull(if (first) 0 else 1)
        else -> match.teams.firstOrNull { it.isBatting }
    }
    return team?.name?.takeIf { it.isNotBlank() }
}

/** Slim-bar model, "MI 186/4 · CSK 142/3". A side without a score keeps its label and a null score. */
fun collapsedSummary(match: CricketMatch): CollapsedSummary {
    val teams = match.teams.take(2).map { team ->
        val label = team.acronym.ifBlank { team.name.take(3).uppercase() }
        val latest = splitInningsScore(team.score).lastOrNull()
        CollapsedTeamScore(label, latest?.let { it.wickets?.let { w -> "${it.runs}/$w" } ?: "${it.runs}" })
    }
    return CollapsedSummary(teams, determineMatchStatus(match) == MatchStatusType.Live)
}
