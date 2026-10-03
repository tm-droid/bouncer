package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ui.components.FormatStats
import com.tanush.bouncer.ui.components.HeadToHeadHistory
import com.tanush.bouncer.ui.components.HeadToHeadStats
import com.tanush.bouncer.ui.components.HistoricalMatch
import com.tanush.bouncer.ui.components.VenueHistoryStats
import com.tanush.bouncer.utils.WinnerSide
import com.tanush.bouncer.utils.attributeWinner
import org.json.JSONObject

data class H2HPair(
    val stats: HeadToHeadStats,
    val history: HeadToHeadHistory,
)

private const val KEY_SEPARATOR = "|||"

/** Order- and case-independent key for a pair of sides. */
internal fun h2hPairKey(team1: String, team2: String): String =
    listOf(team1, team2).map { it.trim().lowercase() }.sorted().joinToString(KEY_SEPARATOR)

/**
 * The file's pairs, re-keyed by [h2hPairKey].
 *
 * The trainer writes each key in the sides' original case (`"Australia|||Sri Lanka"`) and the
 * lookup used to lowercase its own side of the comparison, so no query ever matched and the
 * head-to-head card never appeared. Indexing here rather than changing what the trainer writes
 * keeps every installed build's file readable.
 */
internal fun indexH2HPairs(h2h: JSONObject): Map<String, JSONObject> = buildMap {
    for (key in h2h.keys()) {
        val entry = h2h.optJSONObject(key) ?: continue
        val sides = key.split(KEY_SEPARATOR)
        if (sides.size == 2) put(h2hPairKey(sides[0], sides[1]), entry)
    }
}

object H2HRepository {

    private const val BUCKET_ID = "cricket-stats"
    private const val FILE_ID = "h2h-stats"
    // Memory holds the parsed tree for an hour so it can be released; the bytes stay in OkHttp's
    // disk cache for a day, so re-parsing after that hour costs no download.
    private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    private const val HTTP_MAX_AGE_SECONDS = 24 * 60 * 60

    @Volatile private var cachedH2H: Map<String, JSONObject>? = null
    @Volatile private var cacheExpiry = 0L

    suspend fun getH2H(team1: String, team2: String): H2HPair? {
        val entry = loadCached()?.get(h2hPairKey(team1, team2)) ?: return null
        // The entry names its own sides, in the order its figures are stored. Comparing against
        // that — rather than re-deriving the trainer's sort — is what keeps team1 the caller's team1.
        val swapped = !entry.optString("team1").trim().equals(team1.trim(), ignoreCase = true)
        return buildH2HPair(entry, team1, team2, swapped)
    }

    private suspend fun loadCached(): Map<String, JSONObject>? {
        val now = System.currentTimeMillis()
        cachedH2H?.takeIf { now < cacheExpiry }?.let { return it }
        // The file is rewritten only by the weekly trainer, and it is ~240 KB gzipped on the wire —
        // re-downloaded hourly it was the app's largest per-device Storage cost. A day of staleness
        // on head-to-head history is invisible.
        val bytes = AppwriteClient.downloadFile(BUCKET_ID, FILE_ID, HTTP_MAX_AGE_SECONDS) ?: return null
        return runCatching {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val h2h = root.optJSONObject("h2h") ?: return null
            indexH2HPairs(h2h).also {
                cachedH2H = it
                cacheExpiry = now + CACHE_TTL_MS
            }
        }.getOrNull()
    }

    private fun buildH2HPair(
        entry: JSONObject,
        queryTeam1: String,
        queryTeam2: String,
        swapped: Boolean,
    ): H2HPair {
        val stats = parseStats(entry, swapped)
        val recentMatches = parseRecentMatches(entry, swapped)
        val history = HeadToHeadHistory(
            team1Name = queryTeam1,
            team2Name = queryTeam2,
            totalMatches = stats.totalMatches,
            team1Wins = stats.team1Wins,
            team2Wins = stats.team2Wins,
            noResults = stats.draws,
            recentMatches = recentMatches,
            venueStats = buildVenueStats(recentMatches, queryTeam1, queryTeam2),
            formatStats = buildFormatStats(recentMatches, queryTeam1, queryTeam2),
        )
        return H2HPair(stats, history)
    }

    // Storage stores stats in sorted team order; when the query order is swapped we read
    // the opposite field so team1/team2 always map to the caller's team1/team2.
    private fun pick(swapped: Boolean, first: String, second: String): String =
        if (swapped) second else first

    private fun parseLastFive(entry: JSONObject, swapped: Boolean): List<String> {
        val arr = entry.optJSONArray("lastFive") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val raw = arr.optString(i, "D")
            if (swapped) when (raw) { "W1" -> "W2"; "W2" -> "W1"; else -> raw } else raw
        }
    }

    private fun parseStats(entry: JSONObject, swapped: Boolean): HeadToHeadStats = HeadToHeadStats(
        totalMatches = entry.optInt("totalMatches"),
        team1Wins = entry.optInt(pick(swapped, "team1Wins", "team2Wins")),
        team2Wins = entry.optInt(pick(swapped, "team2Wins", "team1Wins")),
        draws = entry.optInt("draws"),
        team1HomeWins = 0,
        team2HomeWins = 0,
        lastFiveResults = parseLastFive(entry, swapped),
        team1HighestScore = entry.optString(pick(swapped, "team1HighestScore", "team2HighestScore"), "0"),
        team2HighestScore = entry.optString(pick(swapped, "team2HighestScore", "team1HighestScore"), "0"),
        team1LowestScore = entry.optString(pick(swapped, "team1LowestScore", "team2LowestScore"), "0"),
        team2LowestScore = entry.optString(pick(swapped, "team2LowestScore", "team1LowestScore"), "0"),
        lastWinner = entry.optString("lastWinner").takeIf { it.isNotEmpty() },
    )

    private fun parseRecentMatches(entry: JSONObject, swapped: Boolean): List<HistoricalMatch> {
        val recentArray = entry.optJSONArray("recentMatches") ?: return emptyList()
        return buildList {
            for (i in 0 until recentArray.length()) {
                val m = recentArray.optJSONObject(i) ?: continue
                add(HistoricalMatch(
                    date = m.optString("date", ""),
                    venue = m.optString("venue", ""),
                    winner = m.optString("winner").takeIf { it.isNotEmpty() },
                    team1Score = m.optString(pick(swapped, "team1Score", "team2Score"), "-"),
                    team2Score = m.optString(pick(swapped, "team2Score", "team1Score"), "-"),
                    format = m.optString("format", "T20"),
                    margin = m.optString("margin").takeIf { it.isNotEmpty() },
                ))
            }
        }
    }

    private fun buildVenueStats(
        recentMatches: List<HistoricalMatch>,
        queryTeam1: String,
        queryTeam2: String,
    ): VenueHistoryStats? = recentMatches
        .groupBy { it.venue }
        .maxByOrNull { it.value.size }
        ?.let { (venue, matches) ->
            val (team1Wins, team2Wins) = countWins(matches, queryTeam1, queryTeam2)
            VenueHistoryStats(
                venueName = venue,
                totalMatches = matches.size,
                team1Wins = team1Wins,
                team2Wins = team2Wins,
            )
        }

    private fun buildFormatStats(
        recentMatches: List<HistoricalMatch>,
        queryTeam1: String,
        queryTeam2: String,
    ): Map<String, FormatStats> = recentMatches
        .groupBy { it.format }
        .mapValues { (format, matches) ->
            val (team1Wins, team2Wins) = countWins(matches, queryTeam1, queryTeam2)
            FormatStats(
                format = format,
                totalMatches = matches.size,
                team1Wins = team1Wins,
                team2Wins = team2Wins,
            )
        }

    /**
     * Wins for each side, attributed exclusively.
     *
     * Previously each side was counted with its own `winner.contains(team)`, which meant a single
     * match could be credited to both — "India" is a substring of "West Indies", so every West
     * Indies win also counted as an India win and team1Wins + team2Wins could exceed the number of
     * matches played. A result is now attributed to at most one side, or to neither.
     */
    internal fun countWins(
        matches: List<HistoricalMatch>,
        queryTeam1: String,
        queryTeam2: String,
    ): Pair<Int, Int> {
        var team1 = 0
        var team2 = 0
        for (match in matches) {
            when (attributeWinner(match.winner, queryTeam1, queryTeam2)) {
                WinnerSide.TEAM1 -> team1++
                WinnerSide.TEAM2 -> team2++
                null -> Unit
            }
        }
        return team1 to team2
    }
}
