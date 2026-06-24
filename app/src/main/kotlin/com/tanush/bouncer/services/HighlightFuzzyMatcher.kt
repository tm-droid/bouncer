package com.tanush.bouncer.services

import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.entities.MatchEntity
import kotlin.math.max
import kotlin.math.min

object HighlightFuzzyMatcher {
    private const val THRESHOLD = 65

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

    private fun parseTeamFromTitle(title: String, index: Int): String =
        title.split(" vs ", ignoreCase = true)
            .getOrElse(index) { "" }
            .split(",").first().trim()

    private fun scoreMatch(highlight: HighlightEntity, match: MatchEntity): Int {
        var score = 0

        val t1 = match.team1Name.takeIf { it.isNotBlank() } ?: parseTeamFromTitle(match.matchTitle, 0)
        val t2 = match.team2Name.takeIf { it.isNotBlank() } ?: parseTeamFromTitle(match.matchTitle, 1)

        // Home team (0–30): try both home and away since Highlightly may swap order
        val homeVsTeam1 = jaroWinkler(highlight.homeTeam.lowercase().trim(), t1.lowercase().trim())
        val homeVsTeam2 = jaroWinkler(highlight.homeTeam.lowercase().trim(), t2.lowercase().trim())
        val homeScore = maxOf(homeVsTeam1, homeVsTeam2)
        score += (homeScore * 30).toInt()

        // Away team (0–30)
        val awayVsTeam1 = jaroWinkler(highlight.awayTeam.lowercase().trim(), t1.lowercase().trim())
        val awayVsTeam2 = jaroWinkler(highlight.awayTeam.lowercase().trim(), t2.lowercase().trim())
        val awayScore = maxOf(awayVsTeam1, awayVsTeam2)
        score += (awayScore * 30).toInt()

        // League vs match title (0–20): matchTitle usually contains the series/league name
        val titleLower = match.matchTitle.lowercase()
        val leagueLower = highlight.leagueName.lowercase().trim()
        val leagueScore = if (titleLower.contains(leagueLower) || leagueLower.contains(titleLower)) {
            1.0
        } else {
            jaroWinkler(leagueLower, titleLower)
        }
        score += (leagueScore * 20).toInt()

        // Date proximity (0–20)
        val dateScore = scoreDateProximity(highlight.matchStartDate, match.date)
        score += dateScore

        return score
    }

    private fun scoreDateProximity(highlightDate: String, matchDate: String): Int {
        val hDate = extractDate(highlightDate)
        val mDate = extractDate(matchDate)
        if (hDate.isEmpty() || mDate.isEmpty()) return 0
        return when {
            hDate == mDate -> 20
            areDatesAdjacent(hDate, mDate) -> 10
            else -> 0
        }
    }

    private fun extractDate(dateStr: String): String {
        return dateStr.take(10)  // YYYY-MM-DD
    }

    private fun areDatesAdjacent(date1: String, date2: String): Boolean {
        if (date1.isEmpty() || date2.isEmpty()) return false
        return runCatching {
            val d1 = date1.split("-").map { it.toInt() }
            val d2 = date2.split("-").map { it.toInt() }
            val diffDays = ((d1[0] - d2[0]) * 365 + (d1[1] - d2[1]) * 30 + (d1[2] - d2[2])).let { kotlin.math.abs(it) }
            diffDays <= 1
        }.getOrDefault(false)
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
