package com.tanush.bouncer.services

import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.utils.daysBetween
import kotlin.math.max
import kotlin.math.min

object HighlightFuzzyMatcher {
    private const val THRESHOLD = 65

    // Relative weight of each signal. Scores are normalised over whichever are available, so these
    // are ratios rather than a budget that must total 100.
    private const val TEAMS_WEIGHT = 60.0
    private const val LEAGUE_WEIGHT = 20.0
    private const val DATE_WEIGHT = 20.0

    fun matchHighlightToBouncerMatch(
        highlight: HighlightEntity,
        bouncerMatches: List<MatchEntity>
    ): MatchEntity? {
        var bestMatch: MatchEntity? = null
        var bestScore = 0
        for (match in bouncerMatches) {
            val score = scoreMatch(highlight, match)
            if (score > bestScore) {
                bestScore = score
                bestMatch = match
            }
        }
        return if (bestScore >= THRESHOLD) bestMatch else null
    }

    private val TITLE_SEPARATORS = listOf(" vs ", " v ", " vs. ", " against ")

    private fun parseTeamFromTitle(title: String, index: Int): String {
        for (sep in TITLE_SEPARATORS) {
            val parts = title.split(sep, ignoreCase = true)
            if (parts.size >= 2) {
                return parts.getOrElse(index) { "" }
                    .split(",", "|").first().trim()
            }
        }
        return ""
    }

    /**
     * Agreement between a highlight and a match, 0–100.
     *
     * Normalised over the signals actually available rather than summed against a fixed 100.
     * Removing the old blank-league freebie (see [leagueScore]) would otherwise have docked 20
     * points from every candidate whose league name is missing, silently pushing real matches
     * below THRESHOLD. With normalisation, an unavailable signal neither helps nor hurts, and
     * THRESHOLD keeps meaning "65% agreement across what we could compare".
     */
    private fun scoreMatch(highlight: HighlightEntity, match: MatchEntity): Int {
        val t1 = match.team1Name.takeIf { it.isNotBlank() } ?: parseTeamFromTitle(match.matchTitle, 0)
        val t2 = match.team2Name.takeIf { it.isNotBlank() } ?: parseTeamFromTitle(match.matchTitle, 1)

        var earned = 0.0
        var available = 0.0

        // Teams are always comparable — a highlight with no team names at all matches nothing.
        earned += teamsScore(highlight, t1, t2)
        available += TEAMS_WEIGHT

        leagueScore(highlight, match)?.let {
            earned += it
            available += LEAGUE_WEIGHT
        }
        scoreDateProximity(highlight.matchStartDate, match.date)?.let {
            earned += it
            available += DATE_WEIGHT
        }

        if (available <= 0.0) return 0
        return (earned / available * 100).toInt()
    }

    /**
     * Team-name agreement, 0–60.
     *
     * The two sides are paired EXCLUSIVELY: each of the highlight's teams is assigned to a
     * different one of the match's teams, and we take the better of the two possible pairings.
     * Previously home and away each took `max()` over both match teams independently, so one
     * strong name could satisfy both terms and earn the full 60 on its own.
     */
    private fun teamsScore(highlight: HighlightEntity, t1: String, t2: String): Double {
        val home = highlight.homeTeam.lowercase().trim()
        val away = highlight.awayTeam.lowercase().trim()
        val a = t1.lowercase().trim()
        val b = t2.lowercase().trim()

        val straight = jaroWinkler(home, a) + jaroWinkler(away, b)
        val swapped = jaroWinkler(home, b) + jaroWinkler(away, a)
        // Highlightly may list the sides in either order, so the better pairing wins — but only
        // one pairing is used, so each match team is consumed once.
        return maxOf(straight, swapped) / 2.0 * TEAMS_WEIGHT
    }

    /** League/series agreement against the match title, or null when there is no league to compare. */
    private fun leagueScore(highlight: HighlightEntity, match: MatchEntity): Double? {
        val titleLower = match.matchTitle.lowercase()
        val leagueLower = highlight.leagueName.lowercase().trim()
        // A blank league name used to score a free 20 on every candidate:
        // `titleLower.contains("")` is always true.
        if (leagueLower.isEmpty() || titleLower.isEmpty()) return null
        val score = if (titleLower.contains(leagueLower) || leagueLower.contains(titleLower)) {
            1.0
        } else {
            jaroWinkler(leagueLower, titleLower)
        }
        return score * LEAGUE_WEIGHT
    }

    /** Date agreement, or null when either date is unparseable and so carries no information. */
    private fun scoreDateProximity(highlightDate: String, matchDate: String): Double? =
        when (daysBetween(highlightDate, matchDate)) {
            null -> null
            0L -> DATE_WEIGHT
            1L -> DATE_WEIGHT / 2
            else -> 0.0
        }

    private fun jaroWinkler(s1: String, s2: String): Double {
        val jaro = jaro(s1, s2)
        if (jaro < 0.7) return jaro

        var prefix = 0
        for (i in 0 until min(s1.length, s2.length).coerceAtMost(4)) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + (prefix * 0.1 * (1.0 - jaro))
    }

    private fun jaro(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val s1Len = s1.length
        val s2Len = s2.length
        val matchDistance = max(s1Len, s2Len) / 2 - 1
        if (matchDistance < 0) return 0.0

        val s1Matched = BooleanArray(s1Len)
        val s2Matched = BooleanArray(s2Len)

        var matches = 0
        for (i in 0 until s1Len) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, s2Len)
            for (j in start until end) {
                if (!s2Matched[j] && s1[i] == s2[j]) {
                    s1Matched[i] = true
                    s2Matched[j] = true
                    matches++
                    break
                }
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until s1Len) {
            if (!s1Matched[i]) continue
            while (!s2Matched[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        return (matches.toDouble() / s1Len +
                matches.toDouble() / s2Len +
                (matches - transpositions / 2.0) / matches) / 3.0
    }
}
