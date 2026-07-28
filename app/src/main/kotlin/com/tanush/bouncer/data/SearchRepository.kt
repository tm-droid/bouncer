package com.tanush.bouncer.data

import com.tanush.bouncer.utils.acronymFor
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

    // Score bands for fuzzyMatchScore. Each tier's floor sits above the next tier's ceiling, so the
    // kind of match always dominates and coverage only orders candidates within a kind.
    private const val PREFIX_MIN = 0.95        // target starts with query      → [0.95, 1.00]
    private const val WORD_PREFIX_MIN = 0.85   // some word starts with query   → [0.85, 0.95)
    private const val SUBSTRING_MIN = 0.70     // query appears somewhere       → [0.70, 0.85)
    private const val FUZZY_MAX = 0.65         // within edit distance          → [0.00, 0.65]

    /** Permitted edit distance as a fraction of query length — roughly one typo per three chars. */
    private const val FUZZY_EDIT_RATIO = 0.34

    /** Candidates scanned by generateSuggestion before giving up, to bound the empty-results path. */
    private const val MAX_SUGGESTION_CANDIDATES = 200

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
            val imageUrl: String? = null,
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
    // Known Teams (static list so search works even with no live matches loaded)
    // ═══════════════════════════════════════════════════════════════════════════

    val KNOWN_CRICKET_TEAMS: List<String> = listOf(
        // International
        "India", "Australia", "England", "Pakistan", "South Africa",
        "New Zealand", "West Indies", "Sri Lanka", "Bangladesh", "Zimbabwe",
        "Afghanistan", "Ireland", "Netherlands", "Scotland", "Namibia",
        "Papua New Guinea", "UAE", "Nepal", "Oman", "USA",
        // IPL
        "Mumbai Indians", "Chennai Super Kings", "Royal Challengers Bangalore",
        "Kolkata Knight Riders", "Delhi Capitals", "Sunrisers Hyderabad",
        "Punjab Kings", "Rajasthan Royals", "Lucknow Super Giants",
        "Gujarat Titans",
        // BBL
        "Sydney Sixers", "Perth Scorchers", "Melbourne Stars",
        "Brisbane Heat", "Adelaide Strikers", "Hobart Hurricanes",
        "Melbourne Renegades", "Sydney Thunder",
        // PSL
        "Karachi Kings", "Lahore Qalandars", "Peshawar Zalmi",
        "Quetta Gladiators", "Multan Sultans", "Islamabad United",
        // SA20 / CPL
        "MI Cape Town", "Paarl Royals", "Sunrisers Eastern Cape",
        "Trinbago Knight Riders", "Barbados Royals", "Guyana Amazon Warriors",
        "St Kitts and Nevis Patriots",
    )

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
                // Shared with TeamAcronymManager. This file used to carry a second, incompatible
                // initials algorithm that took every word including "of"/"Cricket" and imposed no
                // length cap, so the same team abbreviated differently here than everywhere else.
                val acronym = acronymFor(team)
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

    /**
     * Agreement between a query and a candidate string, 0–1.
     *
     * Four tiers, ordered strongest first, each occupying its own band so a weaker kind of match can
     * never outrank a stronger one. Within a band the score varies with how much of the target the
     * query covers, which is what makes the ranking meaningful: `"in"` now orders India (0.97) above
     * West Indies (0.87) above Sunrisers Hyderabad (0.71).
     *
     * Previously `if (target.contains(query)) return 1.0` came first, so every substring hit tied at
     * exactly 1.0 and `sortedByDescending` degraded to arbitrary input order. It also made the
     * `startsWith` 0.95 tier below it unreachable — `startsWith` implies `contains`.
     */
    private fun fuzzyMatchScore(query: String, target: String): Double {
        if (query.isEmpty() || target.isEmpty()) return 0.0
        val coverage = (query.length.toDouble() / target.length).coerceIn(0.0, 1.0)

        if (target.startsWith(query)) {
            // An exact equality has coverage 1.0 and so lands on exactly 1.0.
            return PREFIX_MIN + (1.0 - PREFIX_MIN) * coverage
        }

        val words = target.split(" ", "-", "_")
        if (words.any { it.startsWith(query) }) {
            return WORD_PREFIX_MIN + (PREFIX_MIN - WORD_PREFIX_MIN) * coverage
        }

        val index = target.indexOf(query)
        if (index >= 0) {
            // A match late in the string is weaker evidence than one near the front.
            val position = 1.0 - index.toDouble() / target.length
            return SUBSTRING_MIN + (WORD_PREFIX_MIN - SUBSTRING_MIN) * coverage * position
        }

        return fuzzyWordScore(query, words)
    }

    /**
     * Levenshtein tier.
     *
     * The tolerance is proportional to the query length rather than a flat 3 edits: three edits on a
     * two-character query is not a typo, it is a different word, and `"in"` used to fuzzy-match
     * `"won"`.
     */
    private fun fuzzyWordScore(query: String, words: List<String>): Double {
        val minDistance = words.minOfOrNull { levenshteinDistance(query, it) } ?: return 0.0
        if (minDistance > editTolerance(query)) return 0.0
        val ratio = 1.0 - minDistance.toDouble() / query.length
        return (ratio * FUZZY_MAX).coerceIn(0.0, FUZZY_MAX)
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

    /**
     * "Did you mean…" for the empty-results path.
     *
     * This runs on **every keystroke** once a query stops matching anything, and used to compute a
     * full Levenshtein against every team name *and* every match title with no prefilter and no
     * early exit. Three bounds fix that without changing which suggestion wins:
     *  - a length prefilter, since a candidate whose length differs by more than the tolerance
     *    cannot possibly be within it (length difference is a lower bound on edit distance);
     *  - an early exit on an exact hit, which cannot be beaten;
     *  - a cap on candidates scanned, because match titles are unbounded in number.
     */
    private fun generateSuggestion(
        query: String,
        teamNames: List<String>,
        matches: List<CricketMatch>,
    ): String? {
        val maxDist = editTolerance(query)
        // Team names first: they are the higher-value suggestions and there are few of them. The
        // length prefilter is exact, not heuristic — the difference in lengths is a lower bound on
        // the edit distance, so a candidate it rejects could never have been within tolerance.
        val candidates = (teamNames.asSequence() + matches.asSequence().map { it.matchTitle })
            .filter { kotlin.math.abs(it.length - query.length) <= maxDist }
            .take(MAX_SUGGESTION_CANDIDATES)

        var bestCandidate: String? = null
        var bestDist = maxDist + 1
        for (candidate in candidates) {
            val dist = levenshteinDistance(query, candidate.lowercase())
            if (dist < bestDist) {
                bestDist = dist
                bestCandidate = candidate
            }
            if (bestDist == 0) break
        }
        return bestCandidate
    }

    /** Permitted edit distance for [query], proportional to its length with a floor of one. */
    private fun editTolerance(query: String): Int =
        maxOf(1, kotlin.math.ceil(query.length * FUZZY_EDIT_RATIO).toInt())

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
