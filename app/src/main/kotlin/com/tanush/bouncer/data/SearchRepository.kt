package com.tanush.bouncer.data

import androidx.annotation.VisibleForTesting
import com.tanush.bouncer.data.history.HistoryNameRow
import com.tanush.bouncer.data.history.PastMatchRow
import com.tanush.bouncer.utils.ParsedQuery
import com.tanush.bouncer.utils.SearchScore
import com.tanush.bouncer.utils.acronymFor
import com.tanush.bouncer.utils.expandedSearchQueries
import com.tanush.bouncer.utils.normalizeForSearch
import com.tanush.bouncer.utils.parseQuery
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.utils.resolveSide
import java.time.Instant
import java.time.ZoneOffset
import android.content.Context
import android.content.SharedPreferences
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SearchRepository — Aggregates search results across matches, teams, players, series and news.
 *
 * Every comparison goes through [normalizeForSearch] and [SearchScore]; see there for the bands.
 * Persists recent search queries in SharedPreferences.
 */
object SearchRepository {
    private const val PREFS_NAME = "bouncer_search_prefs"
    private const val KEY_RECENT_QUERIES = "recent_queries"
    private const val MAX_RECENT_QUERIES = 10

    /** Candidates scanned by generateSuggestion before giving up, to bound the empty-results path. */
    private const val MAX_SUGGESTION_CANDIDATES = 200

    private const val MIN_QUERY_LENGTH = 2
    private const val MATCH_THRESHOLD = 0.3
    private const val ENTITY_THRESHOLD = 0.4
    private const val NEWS_THRESHOLD = 0.3

    private const val MAX_MATCHES = 20
    private const val MAX_TEAMS = 10
    private const val MAX_PLAYERS = 10
    private const val MAX_NEWS = 10
    private const val MAX_SERIES = 10

    // Secondary fields count for less than the thing a row is named by.
    private const val SERIES_NAME_WEIGHT = 0.9
    private const val MATCH_DESC_WEIGHT = 0.6
    private const val VENUE_WEIGHT = 0.5
    private const val NEWS_CONTEXT_WEIGHT = 0.8
    private const val NEWS_INTRO_WEIGHT = 0.7
    private const val STATUS_HIT_SCORE = 0.3

    /** Per facet (format, stage, year) a match satisfies. Orders within a band, never across one. */
    private const val FACET_BOOST = 0.02
    private const val BAND_MARGIN = 0.001
    private val FINAL_WORD = Regex("""(^| )final""")

    /**
     * A series matched on its category alone ("international", "league"). Real, but it says
     * nothing about which series, so it sits just above the entity threshold.
     */
    private const val CATEGORY_ONLY_SERIES_SCORE = 0.45

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
            /** Loaded fixtures this team plays in, so a row can say it has some. */
            val fixtureCount: Int = 0,
        ) : SearchResult()

        data class PlayerResult(
            val playerName: String,
            val teamName: String,
            val role: String,
            override val relevanceScore: Double,
            val imageUrl: String? = null,
            /**
             * Cricbuzz profile id, when a source carried one. The player screen opens this id
             * directly; without it the screen can only re-search by name and take the first hit,
             * which opens the wrong profile for two players who share a name.
             */
            val cricbuzzId: String? = null,
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

        /** A finished match from Cricsheet or the archive; opens through [PastMatchRow.toCricketMatch]. */
        data class PastMatchResult(
            val row: PastMatchRow,
            override val relevanceScore: Double,
        ) : SearchResult()

        /** A player only Cricsheet holds (mostly retired); opens the career page. */
        data class PastPlayerResult(
            val row: HistoryNameRow,
            override val relevanceScore: Double,
        ) : SearchResult()
    }

    data class SearchResults(
        val matches: List<SearchResult.MatchResult> = emptyList(),
        val teams: List<SearchResult.TeamResult> = emptyList(),
        val players: List<SearchResult.PlayerResult> = emptyList(),
        val news: List<SearchResult.NewsResult> = emptyList(),
        val series: List<SearchResult.SeriesResult> = emptyList(),
        val pastMatches: List<SearchResult.PastMatchResult> = emptyList(),
        /** Kept apart from [players]: it must not count toward the confident-hit gate on the Cricbuzz lookup. */
        val pastPlayers: List<SearchResult.PastPlayerResult> = emptyList(),
        val suggestion: String? = null,
        /** Set when the query names a side Cricsheet withholds (`HistoryCoverage`); shown as one neutral row. */
        val coverageNotice: String? = null,
        /** The record of a resolved "a v b" pair, shown above the results. */
        val headToHead: H2HPair? = null,
    ) {
        val isEmpty: Boolean
            get() = matches.isEmpty() && teams.isEmpty() && players.isEmpty() && news.isEmpty()
                        && series.isEmpty() && pastMatches.isEmpty() && pastPlayers.isEmpty()

        val totalCount: Int
            get() = matches.size + teams.size + players.size + news.size + series.size + pastMatches.size + pastPlayers.size
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Corpora
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * One searchable team. [acronym] is a *curated* code (TeamAcronyms.json or Cricbuzz's own
     * short name) or null — never the computed initials, which would let "si" match every team
     * whose words happen to start with S and I.
     */
    class TeamEntry(val name: String, val acronym: String?, val logoUrl: String? = null) {
        internal val normalized = normalizeForSearch(name)
        internal val normalizedAcronym = acronym?.let(::normalizeForSearch)?.takeIf { it.isNotEmpty() }
    }

    /** One searchable series. Names are normalised once, not on every keystroke. */
    class SeriesEntry(
        val seriesId: String,
        val name: String,
        val category: String,
        val startDate: String,
        val endDate: String,
    ) {
        internal val normalized = normalizeForSearch(name)
        internal val normalizedCategory = normalizeForSearch(category)
    }

    /** One cached player profile. */
    class PlayerEntry(val name: String, val teamName: String, val role: String) {
        internal val normalized = normalizeForSearch(name)
    }

    /**
     * Fallback team list for when `TeamAcronyms.json` could not be read — so search still finds
     * the major sides on a device where the asset failed. The asset holds ~1,000 teams and is the
     * real corpus.
     */
    val KNOWN_CRICKET_TEAMS: List<String> = listOf(
        // International
        "India", "Australia", "England", "Pakistan", "South Africa",
        "New Zealand", "West Indies", "Sri Lanka", "Bangladesh", "Zimbabwe",
        "Afghanistan", "Ireland", "Netherlands", "Scotland", "Namibia",
        "Papua New Guinea", "UAE", "Nepal", "Oman", "USA",
        // IPL
        "Mumbai Indians", "Chennai Super Kings", "Royal Challengers Bengaluru",
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

    /**
     * Every team search knows: the teams in loaded matches first (they carry Cricbuzz's logo and
     * short name), then the curated asset, then [KNOWN_CRICKET_TEAMS]. Deduplicated on the
     * normalised name, first source wins.
     */
    fun buildTeamCorpus(
        matches: List<CricketMatch>,
        curatedAcronyms: Map<String, String>,
    ): List<TeamEntry> {
        val byName = LinkedHashMap<String, TeamEntry>()
        fun add(entry: TeamEntry) {
            if (entry.normalized.isNotEmpty()) byName.putIfAbsent(entry.normalized, entry)
        }
        matches.forEach { match ->
            match.teams.forEach { team ->
                val acronym = curatedAcronyms[team.name] ?: team.acronym.takeIf { it.isNotBlank() }
                add(TeamEntry(team.name, acronym, team.logoUrl))
            }
        }
        curatedAcronyms.forEach { (name, acronym) -> add(TeamEntry(name, acronym)) }
        KNOWN_CRICKET_TEAMS.forEach { add(TeamEntry(it, null)) }
        return byName.values.toList()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Search Logic
    // ═══════════════════════════════════════════════════════════════════════════

    /** Names-only overload, kept for the ranking tests that predate the team corpus. */
    @JvmName("searchByTeamNames")
    suspend fun search(
        query: String,
        matches: List<CricketMatch>,
        news: List<NewsArticleData>,
        teamNames: List<String>,
    ): SearchResults = search(query, matches, news, teamNames.map { TeamEntry(it, null) })

    /**
     * In-memory search over everything already loaded. [favourites] only breaks ties — a
     * favourite never outranks a better match, it wins between equals. [logoFor] fills logos for
     * teams that came from the asset rather than a loaded match.
     */
    suspend fun search(
        query: String,
        matches: List<CricketMatch>,
        news: List<NewsArticleData>,
        teams: List<TeamEntry>,
        favourites: Set<String> = emptySet(),
        logoFor: (String) -> String? = { null },
    ): SearchResults = withContext(Dispatchers.Default) {
        val q = normalizeForSearch(query)
        if (q.length < MIN_QUERY_LENGTH) return@withContext SearchResults()

        // A query that is itself the exact name or code of a known team is not a typo, so nothing
        // should be fuzzy-corrected towards it. "india" is two edits from "indies", the same
        // distance "kholi" is from "kohli" — distance alone cannot separate them, but "did the user
        // type a real team?" can. Without this, searching a country with no current fixtures
        // filled the Matches section with every West Indies game.
        val queryIsExactEntity = teams.any { it.normalized == q || it.normalizedAcronym == q }
        val queries = expandedSearchQueries(q)

        val matchResults = searchMatches(q, queries, matches, teams, !queryIsExactEntity, favourites)
        val teamResults = searchTeams(q, teams, matches, favourites, logoFor)
        val playerResults = searchPlayers(q, matches)
        val newsResults = searchNews(q, news)

        val suggestion = if (matchResults.isEmpty() && teamResults.isEmpty() && playerResults.isEmpty()) {
            generateSuggestion(q, teams, matches)
        } else null

        SearchResults(
            matches = matchResults.take(MAX_MATCHES),
            teams = teamResults.take(MAX_TEAMS),
            players = playerResults.sortedByDescending { it.relevanceScore }.take(MAX_PLAYERS),
            news = newsResults.sortedByDescending { it.relevanceScore }.take(MAX_NEWS),
            suggestion = suggestion,
        )
    }

    /**
     * Series ranked in memory over the whole cached corpus.
     *
     * This was a `LIKE … LIMIT 5` with no `ORDER BY`, so SQLite kept five arbitrary rows before
     * anything was ranked, and a multi-word query only matched if it appeared contiguously in the
     * stored name. The corpus is a few hundred rows; scoring all of them costs less than the
     * debounce it runs behind.
     */
    suspend fun searchSeries(query: String, corpus: List<SeriesEntry>): List<SearchResult.SeriesResult> =
        withContext(Dispatchers.Default) {
            val q = normalizeForSearch(query)
            if (q.length < MIN_QUERY_LENGTH) return@withContext emptyList()
            val queries = expandedSearchQueries(q)
            corpus.mapNotNull { entry ->
                val nameScore = queries.maxOf { SearchScore.of(it, entry.normalized) }
                val categoryHit = entry.normalizedCategory.isNotEmpty() && entry.normalizedCategory.startsWith(q)
                val score = maxOf(nameScore, if (categoryHit) CATEGORY_ONLY_SERIES_SCORE else 0.0)
                if (score > ENTITY_THRESHOLD) {
                    SearchResult.SeriesResult(
                        seriesId = entry.seriesId,
                        name = entry.name,
                        category = entry.category,
                        startDate = entry.startDate,
                        endDate = entry.endDate,
                        relevanceScore = score,
                    )
                } else null
            }
                // Stable: equal scores keep the corpus order, which is newest-started first.
                .sortedByDescending { it.relevanceScore }
                .take(MAX_SERIES)
        }

    /**
     * Cached player profiles, scored on the name alone.
     *
     * The old SQL also matched `country` and `teamName`, and nothing thresholded the result, so
     * "india" listed every cached Indian player at a score of zero.
     */
    suspend fun searchCachedPlayers(query: String, corpus: List<PlayerEntry>): List<SearchResult.PlayerResult> =
        withContext(Dispatchers.Default) {
            val q = normalizeForSearch(query)
            if (q.length < MIN_QUERY_LENGTH) return@withContext emptyList()
            corpus.mapNotNull { entry ->
                val score = SearchScore.of(q, entry.normalized)
                if (score > ENTITY_THRESHOLD) {
                    SearchResult.PlayerResult(entry.name, entry.teamName, entry.role, score)
                } else null
            }.sortedByDescending { it.relevanceScore }.take(MAX_PLAYERS)
        }

    /**
     * A loaded match with every searchable field normalised once. Scores repoll every few seconds
     * but a query is re-scored on every keystroke, so normalising per keystroke was most of the cost.
     */
    internal class MatchSearchEntry(val match: CricketMatch) {
        val teamNames = match.teams.map { normalizeForSearch(it.name) }
        val teamAcronyms = match.teams.map { normalizeForSearch(it.acronym) }
        val title = normalizeForSearch(match.matchTitle)
        val series = normalizeForSearch(match.seriesName)
        val desc = normalizeForSearch(match.matchDesc)
        val venue = normalizeForSearch(match.venue)
        val status = normalizeForSearch(match.currentStatus)
        val year: Int? = match.startDateMs?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).year }
    }

    /** The last list's entries, keyed by list identity so a keystroke reuses them and a repoll rebuilds. */
    @Volatile
    private var entryCache: Pair<List<CricketMatch>, List<MatchSearchEntry>>? = null

    private fun entriesFor(matches: List<CricketMatch>): List<MatchSearchEntry> {
        entryCache?.takeIf { it.first === matches }?.let { return it.second }
        val built = matches.distinctBy { it.id }.map(::MatchSearchEntry)
        entryCache = matches to built
        return built
    }

    private fun searchMatches(
        q: String,
        queries: List<String>,
        matches: List<CricketMatch>,
        teams: List<TeamEntry>,
        allowFuzzy: Boolean,
        favourites: Set<String>,
    ): List<SearchResult.MatchResult> {
        val scorer = MatchScorer(q, queries, teams, allowFuzzy)
        return entriesFor(matches).mapNotNull { entry ->
            scorer.score(entry)?.let { SearchResult.MatchResult(entry.match, it) }
        }.sortedWith(
            compareByDescending<SearchResult.MatchResult> { it.relevanceScore }
                .thenBy { stateRank(it.match) }
                .thenBy { recencyRank(it.match) }
                .thenByDescending { r -> r.match.teams.any { it.name in favourites } }
        )
    }

    /** Scores [MatchSearchEntry]s against one query: names, series and venue, then pair and facets. */
    private class MatchScorer(
        private val q: String,
        queries: List<String>,
        teams: List<TeamEntry>,
        private val allowFuzzy: Boolean,
    ) {
        private val acronymByName = teams.associate { it.normalized to it.normalizedAcronym }
        private val parsed = parseQuery(q)

        // A facet-laden query ("india 2011 final") would score zero against a title with no "2011"
        // in it, so names are scored on the entity words as well as the full text.
        private val entityQ = parsed.entityText
        private val entityQueries =
            if (entityQ == q) queries else (queries + expandedSearchQueries(entityQ)).distinct()

        /** Both sides of "a v b" as normalised team names, or null when either resolves to no team. */
        private val sides = parsed.pair
            ?.let { (a, b) -> resolveSide(a, teams) to resolveSide(b, teams) }
            ?.takeIf { it.first.isNotEmpty() && it.second.isNotEmpty() }
            ?.let { (a, b) -> a.mapTo(HashSet(), ::normalizeForSearch) to b.mapTo(HashSet(), ::normalizeForSearch) }

        private fun of(target: String): Double {
            val full = SearchScore.of(q, target, allowFuzzy)
            return if (entityQ == q) full else maxOf(full, SearchScore.of(entityQ, target, allowFuzzy))
        }

        /** The match's score, or null when it is not a result. */
        fun score(entry: MatchSearchEntry): Double? {
            val pairHit = sides != null && entry.teamNames.hasBothSides(sides.first, sides.second)
            // A resolved pair is required: "ind v aus" is that fixture, not anything mentioning India.
            if (sides != null && !pairHit) return null
            var best = maxOf(teamScore(entry), nameScore(entry))
            if (pairHit) best = maxOf(best, SearchScore.WORD_PREFIX_MIN)
            if (best <= MATCH_THRESHOLD) return null
            return withinBand(best, FACET_BOOST * facetHits(parsed, entry))
        }

        private fun teamScore(entry: MatchSearchEntry): Double =
            entry.teamNames.indices.maxOfOrNull { i ->
                val name = entry.teamNames[i]
                val acronym = acronymByName[name] ?: entry.teamAcronyms[i]
                if (acronym == q || acronym == entityQ) SearchScore.ACRONYM else of(name)
            } ?: 0.0

        private fun nameScore(entry: MatchSearchEntry): Double {
            // Alias-expanded, so "ipl" finds every IPL fixture by its series name.
            val series = entityQueries.maxOf { SearchScore.of(it, entry.series, allowFuzzy) } * SERIES_NAME_WEIGHT
            val statusScore = if (entry.status.contains(q)) STATUS_HIT_SCORE else 0.0
            return maxOf(
                of(entry.title), series, of(entry.desc) * MATCH_DESC_WEIGHT,
                of(entry.venue) * VENUE_WEIGHT, statusScore,
            )
        }
    }

    private fun List<String>.hasBothSides(a: Set<String>, b: Set<String>): Boolean =
        indices.any { i -> this[i] in a && indices.any { j -> j != i && this[j] in b } }

    /** How many of the query's facets (format, stage, year) this match satisfies. */
    private fun facetHits(parsed: ParsedQuery, entry: MatchSearchEntry): Int =
        listOf(formatHit(parsed.format, entry.match), stageHit(parsed.stage, entry), yearHit(parsed.year, entry))
            .count { it }

    private fun formatHit(format: String?, match: CricketMatch): Boolean {
        val type = match.matchType.uppercase()
        return when (format) {
            null -> false
            "T20I" -> type == "T20" && match.competitionType.equals("International", ignoreCase = true)
            else -> type == format
        }
    }

    private fun stageHit(stage: String?, entry: MatchSearchEntry): Boolean {
        val text = "${entry.desc} ${entry.title}"
        return when (stage) {
            null -> false
            "FINAL" -> FINAL_WORD.containsMatchIn(text) && "semi" !in text && "quarter" !in text
            "SEMI" -> "semi" in text
            else -> "qualifier" in text || "eliminator" in text
        }
    }

    private fun yearHit(year: Int?, entry: MatchSearchEntry): Boolean =
        year != null && (entry.year == year || entry.series.split(' ').any { it == year.toString() })

    /** [score] plus [boost], held below the next band's floor so a facet never changes a match's kind. */
    private fun withinBand(score: Double, boost: Double): Double {
        if (boost == 0.0) return score
        val ceiling = when {
            score >= SearchScore.PREFIX_MIN -> 1.0
            score >= SearchScore.WORD_PREFIX_MIN -> SearchScore.PREFIX_MIN - BAND_MARGIN
            score >= SearchScore.SUBSTRING_MIN -> SearchScore.WORD_PREFIX_MIN - BAND_MARGIN
            else -> SearchScore.SUBSTRING_MIN - BAND_MARGIN
        }
        return maxOf(score, minOf(score + boost, ceiling))
    }

    /** Live before upcoming before finished, among equally relevant matches. */
    private fun stateRank(match: CricketMatch): Int = when {
        match.isLive -> 0
        match.isUpcoming -> 1
        else -> 2
    }

    /** Within a state: finished newest first, upcoming soonest first. Unknown start times sort last. */
    private fun recencyRank(match: CricketMatch): Long {
        val start = match.startDateMs ?: return Long.MAX_VALUE
        return if (match.isUpcoming) start else -start
    }

    /**
     * What a team can be matched on. Facets and "v" name no team: "ind v aus 2011 final" scored
     * whole matched only India A, Under-19 and Women, whose extra words happened to fit. Each side
     * of a pair is a needle of its own.
     */
    private fun teamNeedles(q: String): List<String> {
        val parsed = parseQuery(q)
        return (listOf(q) + (parsed.pair?.toList() ?: listOf(parsed.entityText))).distinct()
    }

    private fun teamScore(needle: String, team: TeamEntry): Double {
        val nameScore = SearchScore.of(needle, team.normalized)
        return if (team.normalizedAcronym == needle) maxOf(nameScore, SearchScore.ACRONYM) else nameScore
    }

    private fun searchTeams(
        q: String,
        teams: List<TeamEntry>,
        matches: List<CricketMatch>,
        favourites: Set<String>,
        logoFor: (String) -> String?,
    ): List<SearchResult.TeamResult> {
        val loadedTeams = matches.distinctBy { it.id }.flatMap { it.teams }
        val fixtures = loadedTeams.groupingBy { normalizeForSearch(it.name) }.eachCount()
        val matchLogos = loadedTeams.filter { it.logoUrl != null }
            .associate { normalizeForSearch(it.name) to it.logoUrl }
        // The corpus lists loaded sides first, then the curated asset, so its order stands in for popularity.
        val order = HashMap<String, Int>().also { map -> teams.forEachIndexed { i, t -> map.putIfAbsent(t.name, i) } }
        val needles = teamNeedles(q)
        return teams.mapNotNull { team ->
            val score = needles.maxOf { teamScore(it, team) }
            if (score <= ENTITY_THRESHOLD) return@mapNotNull null
            SearchResult.TeamResult(
                teamName = team.name,
                // Shared with TeamAcronymManager's fallback, so a team with no curated code still
                // abbreviates the same way here as everywhere else.
                teamAcronym = team.acronym ?: acronymFor(team.name),
                logoUrl = team.logoUrl ?: matchLogos[team.normalized] ?: logoFor(team.name),
                relevanceScore = score,
                fixtureCount = fixtures[team.normalized] ?: 0,
            )
        }.sortedWith(
            compareByDescending<SearchResult.TeamResult> { it.relevanceScore }
                .thenByDescending { it.teamName in favourites }
                .thenByDescending { it.fixtureCount }
                .thenBy { order[it.teamName] ?: Int.MAX_VALUE }
        )
    }

    private fun searchPlayers(q: String, matches: List<CricketMatch>): List<SearchResult.PlayerResult> {
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
            .distinctBy { normalizeForSearch(it.name) }
            .mapNotNull { c ->
                val score = SearchScore.of(q, normalizeForSearch(c.name))
                if (score > ENTITY_THRESHOLD) SearchResult.PlayerResult(c.name, c.team, c.role, score) else null
            }
    }

    private fun searchNews(q: String, news: List<NewsArticleData>): List<SearchResult.NewsResult> {
        return news.mapNotNull { article ->
            val titleScore = SearchScore.of(q, normalizeForSearch(article.headline))
            val contextScore = SearchScore.of(q, normalizeForSearch(article.context.orEmpty())) * NEWS_CONTEXT_WEIGHT
            val introScore = SearchScore.of(q, normalizeForSearch(article.intro)) * NEWS_INTRO_WEIGHT
            val best = maxOf(titleScore, contextScore, introScore)
            if (best > NEWS_THRESHOLD) SearchResult.NewsResult(article, best) else null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fuzzy Matching
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [SearchScore.of] over normalised text, for tests. Exposed because the scoring is what
     * section ordering and the acronym path both hinge on, and both bugs it guards were invisible
     * from the public API.
     */
    @VisibleForTesting
    internal fun relevanceOf(query: String, target: String): Double =
        SearchScore.of(normalizeForSearch(query), normalizeForSearch(target))

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
     * This runs on **every keystroke** once a query stops matching anything. Three bounds keep it
     * cheap without changing which suggestion wins:
     *  - a length prefilter, since a candidate whose length differs by more than the tolerance
     *    cannot possibly be within it (length difference is a lower bound on edit distance);
     *  - an early exit on an exact hit, which cannot be beaten;
     *  - a cap on candidates scanned, because match titles are unbounded in number.
     */
    private fun generateSuggestion(
        query: String,
        teams: List<TeamEntry>,
        matches: List<CricketMatch>,
    ): String? {
        val maxDist = SearchScore.editTolerance(query)
        // Team names first: they are the higher-value suggestions.
        val candidates = (teams.asSequence().map { it.name } + matches.asSequence().map { it.matchTitle })
            .filter { kotlin.math.abs(it.length - query.length) <= maxDist }
            .take(MAX_SUGGESTION_CANDIDATES)

        var bestCandidate: String? = null
        var bestDist = maxDist + 1
        for (candidate in candidates) {
            val dist = SearchScore.editDistance(query, normalizeForSearch(candidate))
            if (dist < bestDist) {
                bestDist = dist
                bestCandidate = candidate
            }
            if (bestDist == 0) break
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
        if (trimmed.length < MIN_QUERY_LENGTH) return
        val current = getRecentQueries(context).toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        saveRecentQueries(context, current.take(MAX_RECENT_QUERIES))
    }

    fun removeRecentQuery(context: Context, query: String) {
        saveRecentQueries(context, getRecentQueries(context).filterNot { it == query })
    }

    fun clearRecentQueries(context: Context) {
        getPrefs(context).edit().remove(KEY_RECENT_QUERIES).apply()
    }

    private fun saveRecentQueries(context: Context, queries: List<String>) {
        getPrefs(context).edit().putString(KEY_RECENT_QUERIES, queries.joinToString("|||")).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════════════════════════════════════
    // Suggestions before typing
    // ═══════════════════════════════════════════════════════════════════════════

    private const val MAX_LIVE_SUGGESTIONS = 8

    /**
     * What is on right now: the series of live matches, then their teams. Replaces a fixed list
     * that suggested "Champions Trophy" and "Ashes" whatever was actually being played.
     */
    fun liveSuggestions(matches: List<CricketMatch>): List<String> {
        val live = matches.filter { it.isLive }
        return (live.map { it.seriesName } + live.flatMap { m -> m.teams.map { it.name } })
            .filter { it.isNotBlank() }
            .distinctBy { normalizeForSearch(it) }
            .take(MAX_LIVE_SUGGESTIONS)
    }

    private const val MAX_SERIES_SUGGESTIONS = 6

    /** Names of the newest-started series still running at [nowMs]; the idle suggestions when nothing is live. */
    fun currentSeriesSuggestions(series: List<SeriesEntry>, nowMs: Long): List<String> =
        series.filter { s -> s.endDate.toLongOrNull()?.let { it >= nowMs } ?: true }
            .sortedByDescending { it.startDate.toLongOrNull() ?: 0L }
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeForSearch(it) }
            .take(MAX_SERIES_SUGGESTIONS)

    fun getTrendingSearches(): List<String> = listOf(
        "India", "Australia", "IPL", "World Cup", "Virat Kohli",
        "Joe Root", "T20", "ODI", "Champions Trophy", "Ashes",
    )
}
