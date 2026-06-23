package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ui.components.FormatStats
import com.tanush.bouncer.ui.components.HeadToHeadHistory
import com.tanush.bouncer.ui.components.HeadToHeadStats
import com.tanush.bouncer.ui.components.HistoricalMatch
import com.tanush.bouncer.ui.components.VenueHistoryStats
import org.json.JSONObject

data class H2HPair(
    val stats: HeadToHeadStats,
    val history: HeadToHeadHistory,
)

object H2HRepository {

    private const val BUCKET_ID = "cricket-stats"
    private const val FILE_ID = "h2h-stats"
    private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour

    @Volatile private var cachedH2H: JSONObject? = null
    @Volatile private var cacheExpiry = 0L

    suspend fun getH2H(team1: String, team2: String): H2HPair? {
        val h2hRoot = loadCached() ?: return null
        val t1Lower = team1.lowercase().trim()
        val t2Lower = team2.lowercase().trim()
        val key = listOf(t1Lower, t2Lower).sorted().joinToString("|||")
        val entry = h2hRoot.optJSONObject(key) ?: return null

        // Determine if query order matches storage order (sorted order)
        val swapped = t1Lower > t2Lower
        return buildH2HPair(entry, team1, team2, swapped)
    }

    private suspend fun loadCached(): JSONObject? {
        val now = System.currentTimeMillis()
        if (cachedH2H != null && now < cacheExpiry) return cachedH2H
        val bytes = AppwriteClient.downloadFile(BUCKET_ID, FILE_ID) ?: return null
        return runCatching {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val h2h = root.optJSONObject("h2h") ?: return null
            cachedH2H = h2h
            cacheExpiry = now + CACHE_TTL_MS
            h2h
        }.getOrNull()
    }

    private fun buildH2HPair(
        entry: JSONObject,
        queryTeam1: String,
        queryTeam2: String,
        swapped: Boolean,
    ): H2HPair {
        val t1Wins = entry.optInt(if (swapped) "team2Wins" else "team1Wins")
        val t2Wins = entry.optInt(if (swapped) "team1Wins" else "team2Wins")
        val t1Highest = entry.optString(if (swapped) "team2HighestScore" else "team1HighestScore", "0")
        val t1Lowest  = entry.optString(if (swapped) "team2LowestScore"  else "team1LowestScore",  "0")
        val t2Highest = entry.optString(if (swapped) "team1HighestScore" else "team2HighestScore", "0")
        val t2Lowest  = entry.optString(if (swapped) "team1LowestScore"  else "team2LowestScore",  "0")

        val lastFiveRaw = entry.optJSONArray("lastFive")
        val lastFive = buildList {
            if (lastFiveRaw != null) {
                for (i in 0 until lastFiveRaw.length()) {
                    val raw = lastFiveRaw.optString(i, "D")
                    // If swapped, flip W1↔W2
                    add(if (swapped) when (raw) { "W1" -> "W2"; "W2" -> "W1"; else -> raw } else raw)
                }
            }
        }

        val stats = HeadToHeadStats(
            totalMatches = entry.optInt("totalMatches"),
            team1Wins = t1Wins,
            team2Wins = t2Wins,
            draws = entry.optInt("draws"),
            team1HomeWins = 0,
            team2HomeWins = 0,
            lastFiveResults = lastFive,
            team1HighestScore = t1Highest,
            team2HighestScore = t2Highest,
            team1LowestScore = t1Lowest,
            team2LowestScore = t2Lowest,
            lastWinner = entry.optString("lastWinner").takeIf { it.isNotEmpty() },
        )

        val recentArray = entry.optJSONArray("recentMatches")
        val recentMatches = buildList<HistoricalMatch> {
            if (recentArray != null) {
                for (i in 0 until recentArray.length()) {
                    val m = recentArray.optJSONObject(i) ?: continue
                    val t1Score = m.optString(if (swapped) "team2Score" else "team1Score", "-")
                    val t2Score = m.optString(if (swapped) "team1Score" else "team2Score", "-")
                    add(HistoricalMatch(
                        date = m.optString("date", ""),
                        venue = m.optString("venue", ""),
                        winner = m.optString("winner").takeIf { it.isNotEmpty() },
                        team1Score = t1Score,
                        team2Score = t2Score,
                        format = m.optString("format", "T20"),
                        margin = m.optString("margin").takeIf { it.isNotEmpty() },
                    ))
                }
            }
        }

        val venueStats = recentMatches
            .groupBy { it.venue }
            .maxByOrNull { it.value.size }
            ?.let { (venue, matches) ->
                VenueHistoryStats(
                    venueName = venue,
                    totalMatches = matches.size,
                    team1Wins = matches.count { it.winner?.contains(queryTeam1, ignoreCase = true) == true },
                    team2Wins = matches.count { it.winner?.contains(queryTeam2, ignoreCase = true) == true },
                )
            }

        val formatStats = recentMatches
            .groupBy { it.format }
            .mapValues { (format, matches) ->
                FormatStats(
                    format = format,
                    totalMatches = matches.size,
                    team1Wins = matches.count { it.winner?.contains(queryTeam1, ignoreCase = true) == true },
                    team2Wins = matches.count { it.winner?.contains(queryTeam2, ignoreCase = true) == true },
                )
            }

        val history = HeadToHeadHistory(
            team1Name = queryTeam1,
            team2Name = queryTeam2,
            totalMatches = stats.totalMatches,
            team1Wins = t1Wins,
            team2Wins = t2Wins,
            noResults = stats.draws,
            recentMatches = recentMatches,
            venueStats = venueStats,
            formatStats = formatStats,
        )

        return H2HPair(stats, history)
    }
}
