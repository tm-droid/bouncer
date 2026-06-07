package com.tanush.bouncer.data

import android.content.Context
import android.content.SharedPreferences
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SearchRepository — Aggregates search results across matches, teams, players, and news.
 * Supports fuzzy matching via Levenshtein distance for "did you mean?" suggestions.
 * Persists recent search queries in SharedPreferences.
 */
object SearchRepository {
    private const val PREFS_NAME = "bouncer_search_prefs"
    private const val KEY_RECENT_QUERIES = "recent_queries"
    private const val MAX_RECENT_QUERIES = 10
    private const val FUZZY_THRESHOLD = 3

    // ═══════════════════════════════════════════════════════════════════════════
    // Search Result Models
    // ═══════════════════════════════════════════════════════════════════════════

    sealed class SearchResult {
        abstract val relevanceScore: Double

        data class MatchResult(
            val match: CricketMatch,
            override val relevanceScore: Double,
        ) : SearchResult()

        data class TeamResult(
            val teamName: String,
            val teamAcronym: String,
            val logoUrl: String?,
            override val relevanceScore: Double,
        ) : SearchResult()

        data class PlayerResult(
            val playerName: String,
            val teamName: String,
            val role: String,
            override val relevanceScore: Double,
        ) : SearchResult()

        data class NewsResult(
            val article: NewsArticleData,
            override val relevanceScore: Double,
        ) : SearchResult()

        data class SeriesResult(
            val seriesId: String,
            val name: String,
            val category: String,
            val startDate: String,
            val endDate: String,
            override val relevanceScore: Double,
        ) : SearchResult()

        data class HistoricalMatchResult(
            val matchId: String,
            val matchTitle: String,
            val teams: List<String>,
            val venue: String,
            val date: String,
            val season: String,
            val matchType: String,
            override val relevanceScore: Double,
        ) : SearchResult()
    }

    data class SearchResults(
        val matches: List<SearchResult.MatchResult> = emptyList(),
        val teams: List<SearchResult.TeamResult> = emptyList(),
        val players: List<SearchResult.PlayerResult> = emptyList(),
        val news: List<SearchResult.NewsResult> = emptyList(),
        val series: List<SearchResult.SeriesResult> = emptyList(),
        val historicalMatches: List<SearchResult.HistoricalMatchResult> = emptyList(),
        val suggestion: String? = null,
    ) {
        val isEmpty: Boolean
            get() = matches.isEmpty() && teams.isEmpty() && players.isEmpty() && news.isEmpty()
                        && series.isEmpty() && historicalMatches.isEmpty()

        val totalCount: Int
            get() = matches.size + teams.size + players.size + news.size + series.size + historicalMatches.size
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Search Logic
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun search(
        query: String,
        matches: List<CricketMatch>,
        news: List<NewsArticleData>,
        teamNames: List<String>,
    ): SearchResults = withContext(Dispatchers.Default) {
        val q = query.trim().lowercase()
        if (q.length < 2) return@withContext SearchResults()

        val matchResults = searchMatches(q, matches)
        val teamResults = searchTeams(q, teamNames, matches)
        val playerResults = searchPlayers(q, matches)
        val newsResults = searchNews(q, news)

        // Generate suggestion if no exact results
        val suggestion = if (matchResults.isEmpty() && teamResults.isEmpty() && playerResults.isEmpty()) {
            generateSuggestion(q, teamNames, matches)
        } else null

        SearchResults(
            matches = matchResults.sortedByDescending { it.relevanceScore }.take(10),
            teams = teamResults.sortedByDescending { it.relevanceScore }.take(5),
            players = playerResults.sortedByDescending { it.relevanceScore }.take(10),
            news = newsResults.sortedByDescending { it.relevanceScore }.take(5),
            suggestion = suggestion,
        )
    }

    suspend fun searchFromDb(
        query: String,
        db: com.tanush.bouncer.data.local.BouncerDatabase,
    ): AppResult<Pair<List<SearchResult.SeriesResult>, List<SearchResult.HistoricalMatchResult>>> {
        if (query.trim().length < 2) return AppResult.Success(Pair(emptyList(), emptyList()))
        val ftsQuery = buildFtsQuery(query)
        return AppResult.runCatchingSuspend {
            withContext(Dispatchers.IO) {
                val seriesResults = db.seriesCacheDao().searchSeries(ftsQuery).map { entity ->
                    SearchResult.SeriesResult(
                        seriesId = entity.seriesId,
                        name = entity.name,
                        category = entity.category,
                        startDate = entity.startDate,
                        endDate = entity.endDate,
                        relevanceScore = 1.0,
                    )
                }
                val historicalResults = db.historicalMatchCacheDao().searchHistoricalMatches(ftsQuery).map { entity ->
                    SearchResult.HistoricalMatchResult(
                        matchId = entity.matchId,
                        matchTitle = entity.matchTitle,
                        teams = entity.teams.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        venue = entity.venue,
                        date = entity.date,
                        season = entity.season,
                        matchType = entity.matchType,
                        relevanceScore = 1.0,
                    )
                }
                Pair(seriesResults, historicalResults)
            }
        }
    }

    suspend fun searchPlayersFromDb(
        query: String,
        db: com.tanush.bouncer.data.local.BouncerDatabase,
    ): AppResult<List<SearchResult.PlayerResult>> {
        if (query.trim().length < 2) return AppResult.Success(emptyList())
        val ftsQuery = buildFtsQuery(query)
        return AppResult.runCatchingSuspend {
            withContext(Dispatchers.IO) {
                db.playerCacheDao().searchPlayers(ftsQuery).map { entity ->
                    SearchResult.PlayerResult(
                        playerName = entity.name,
                        teamName = entity.teamName,
                        role = entity.role,
                        relevanceScore = 1.0,
                    )
                }
            }
        }
    }

    private fun searchMatches(query: String, matches: List<CricketMatch>): List<SearchResult.MatchResult> {
        return matches.mapNotNull { match ->
            val titleScore = fuzzyMatchScore(query, match.matchTitle.lowercase())
            val team1Score = match.teams.getOrNull(0)?.let { fuzzyMatchScore(query, it.name.lowercase()) } ?: 0.0
            val team2Score = match.teams.getOrNull(1)?.let { fuzzyMatchScore(query, it.name.lowercase()) } ?: 0.0
            val venueScore = fuzzyMatchScore(query, match.venue.lowercase()) * 0.5
            val statusScore = if (match.currentStatus.lowercase().contains(query)) 0.3 else 0.0

            val best = maxOf(titleScore, team1Score, team2Score, venueScore, statusScore)
            if (best > 0.3) SearchResult.MatchResult(match, best) else null
        }
    }

    private fun searchTeams(
        query: String,
        teamNames: List<String>,
        matches: List<CricketMatch>,
    ): List<SearchResult.TeamResult> {
        val logoUrlMap = buildMap<String, String?> {
            matches.forEach { m -> m.teams.forEach { put(it.name.lowercase(), it.logoUrl) } }
        }
        return teamNames.mapNotNull { team ->
            val score = fuzzyMatchScore(query, team.lowercase())
            if (score > 0.4) {
                val acronym = team.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                SearchResult.TeamResult(team, acronym, logoUrlMap[team.lowercase()], score)
            } else null
        }
    }

    private fun buildFtsQuery(query: String): String =
        "${query.trim().replace("\"", "").trim()}*"

    private fun searchPlayers(query: String, matches: List<CricketMatch>): List<SearchResult.PlayerResult> {
        data class Candidate(val name: String, val team: String, val role: String)

        return matches
            .flatMap { match ->
                val battingTeam = match.teams.find { it.isBatting }?.name
                    ?: match.teams.getOrNull(0)?.name.orEmpty()
                val bowlingTeam = match.teams.find { !it.isBatting }?.name
                    ?: match.teams.getOrNull(1)?.name.orEmpty()
                val batsmen = match.scrapedBatsmen.orEmpty()
                    .map { Candidate(it.name, battingTeam, "Batsman") }
                val bowler = listOfNotNull(
                    match.currentBowler?.let { Candidate(it.name, bowlingTeam, "Bowler") }
                )
                batsmen + bowler
            }
            .distinctBy { it.name.lowercase() }
            .mapNotNull { c ->
                val score = fuzzyMatchScore(query, c.name.lowercase())
                if (score > 0.4) SearchResult.PlayerResult(c.name, c.team, c.role, score) else null
            }
    }

    private fun searchNews(query: String, news: List<NewsArticleData>): List<SearchResult.NewsResult> {
        return news.mapNotNull { article ->
            val titleScore = fuzzyMatchScore(query, article.headline.lowercase())
            val descScore = fuzzyMatchScore(query, article.intro.lowercase()) * 0.7
            val best = maxOf(titleScore, descScore)
            if (best > 0.3) SearchResult.NewsResult(article, best) else null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fuzzy Matching
    // ═══════════════════════════════════════════════════════════════════════════

    private fun fuzzyMatchScore(query: String, target: String): Double {
        if (query.isEmpty() || target.isEmpty()) return 0.0
        // Exact substring match
        if (target.contains(query)) return 1.0
        // Starts-with bonus
        if (target.startsWith(query)) return 0.95
        // Word starts-with match
        val words = target.split(" ", "-", "_")
        if (words.any { it.startsWith(query) }) return 0.85
        // Levenshtein on individual words
        val minDistance = words.minOfOrNull { levenshteinDistance(query, it) } ?: Int.MAX_VALUE
        return if (minDistance <= FUZZY_THRESHOLD) {
            (1.0 - minDistance.toDouble() / maxOf(query.length, 1).toDouble()).coerceIn(0.0, 0.8)
        } else 0.0
    }

    /** Levenshtein distance between two strings. Two-row DP: O(min(m,n)) space. */
    internal fun levenshteinDistance(a: String, b: String): Int {
        val (s, t) = if (a.length <= b.length) Pair(a, b) else Pair(b, a)
        val sLen = s.length
        val tLen = t.length
        var prev = IntArray(sLen + 1) { it }
        var curr = IntArray(sLen + 1)
        for (j in 1..tLen) {
            curr[0] = j
            for (i in 1..sLen) {
                curr[i] = if (s[i - 1] == t[j - 1]) {
                    prev[i - 1]
                } else {
                    minOf(prev[i], curr[i - 1], prev[i - 1]) + 1
                }
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[sLen]
    }

    private fun generateSuggestion(
        query: String,
        teamNames: List<String>,
        matches: List<CricketMatch>,
    ): String? {
        val candidates = ArrayList<String>(teamNames.size + matches.size)
        candidates.addAll(teamNames)
        matches.forEach { candidates.add(it.matchTitle) }

        var bestCandidate: String? = null
        var bestDist = Int.MAX_VALUE
        for (candidate in candidates) {
            val dist = levenshteinDistance(query, candidate.lowercase())
            if (dist < bestDist && dist <= 3) {
                bestDist = dist
                bestCandidate = candidate
            }
        }
        return bestCandidate
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Search History
    // ═══════════════════════════════════════════════════════════════════════════

    fun getRecentQueries(context: Context): List<String> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_RECENT_QUERIES, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("|||").filter { it.isNotBlank() }
    }

    fun addRecentQuery(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val current = getRecentQueries(context).toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val limited = current.take(MAX_RECENT_QUERIES)
        getPrefs(context).edit().putString(KEY_RECENT_QUERIES, limited.joinToString("|||")).apply()
    }

    fun clearRecentQueries(context: Context) {
        getPrefs(context).edit().remove(KEY_RECENT_QUERIES).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════════════════════════════════════
    // Trending / Popular Searches
    // ═══════════════════════════════════════════════════════════════════════════

    fun getTrendingSearches(): List<String> = listOf(
        "India", "Australia", "IPL", "World Cup", "Virat Kohli",
        "Joe Root", "T20", "ODI", "Champions Trophy", "Ashes",
    )
}
