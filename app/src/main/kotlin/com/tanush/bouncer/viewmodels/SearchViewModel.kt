package com.tanush.bouncer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.network.PlayerProfileScraper
import com.tanush.bouncer.network.PlayerSearchOutcome
import com.tanush.bouncer.network.SeriesScraper
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.utils.bouncerExceptionHandler
import com.tanush.bouncer.utils.competitionAliasesFor
import io.sentry.Sentry
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchTab { ALL, MATCHES, PLAYERS, TEAMS, TOURNAMENTS, NEWS }

private data class SearchBundle(
    val live: SearchRepository.SearchResults,
    val dbSeries: AppResult<List<SearchRepository.SearchResult.SeriesResult>>,
    val dbPlayers: AppResult<List<SearchRepository.SearchResult.PlayerResult>>,
)

/** Local sources answer at this delay; the screen feels responsive at 300 ms. */
private const val LOCAL_DEBOUNCE_MS = 300L

/** The billed Appwrite leg waits for the user to genuinely stop typing. */
private const val NETWORK_DEBOUNCE_MS = 800L

/** Below this, a query is too broad to be worth an execution. Local sources still run at 2. */
private const val MIN_NETWORK_QUERY_LENGTH = 3

/** A local player hit at least this good makes the network lookup redundant. */
private const val CONFIDENT_PLAYER_SCORE = 0.95

private const val NETWORK_PLAYER_SCORE = 0.9
private const val CATEGORY_ONLY_SERIES_SCORE = 0.7
private const val MAX_PLAYER_RESULTS = 10

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BouncerDatabase.getInstance(application)
    private val seriesScraper = SeriesScraper()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(SearchRepository.SearchResults())
    val results: StateFlow<SearchRepository.SearchResults> = _results.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchTab.ALL)
    val selectedTab: StateFlow<SearchTab> = _selectedTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Non-null when the whole search failed (vs. genuinely empty results). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    private var searchJob: Job? = null

    fun loadRecentQueries() {
        _recentQueries.value = SearchRepository.getRecentQueries(getApplication())
    }

    fun onQueryChange(
        newQuery: String,
        matches: List<CricketMatch>,
        news: List<NewsArticleData>,
    ) {
        _query.value = newQuery
        searchJob?.cancel()
        _error.value = null
        if (newQuery.length < 2) {
            _results.value = SearchRepository.SearchResults()
            _isLoading.value = false
            return
        }
        searchJob = viewModelScope.launch(bouncerExceptionHandler) {
            delay(LOCAL_DEBOUNCE_MS)
            _isLoading.value = true
            val localResults = try {
                performLocalSearch(newQuery, matches, news)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Partial-source failures degrade silently inside performLocalSearch;
                // this is the whole-search failure path — surface it instead of
                // faking "no results".
                Sentry.captureException(e)
                _error.value = "Search hit a rain delay — check your connection and retry."
                null
            } finally {
                _isLoading.value = false
            }
            if (localResults != null) mergeNetworkPlayers(newQuery, localResults)
        }
    }

    /**
     * The network player lookup, on its own longer debounce.
     *
     * The 300 ms above is a typing pause, not a rate limit: typed at any normal speed, "Virat
     * Kohli" clears it several times and each clearance was one Appwrite function execution. The
     * local sources still answer at 300 ms so the screen stays responsive; only the billed leg
     * waits for the user to actually stop typing. This runs inside [searchJob], so the next
     * keystroke cancels it before it can fire.
     */
    private suspend fun mergeNetworkPlayers(
        newQuery: String,
        localResults: SearchRepository.SearchResults,
    ) {
        // A confident local hit already answers the question; no need to spend an execution.
        if (localResults.players.any { it.relevanceScore >= CONFIDENT_PLAYER_SCORE }) return
        if (newQuery.trim().length < MIN_NETWORK_QUERY_LENGTH) return

        delay(NETWORK_DEBOUNCE_MS - LOCAL_DEBOUNCE_MS)

        when (val outcome = PlayerProfileScraper.searchPlayersDetailed(newQuery)) {
            is PlayerSearchOutcome.Unavailable ->
                // Deliberately not an empty list. A dead player-search backend rendering as a
                // clean "no results" is exactly how the previous one stayed broken unnoticed.
                _error.value = "Player search is unavailable right now."
            is PlayerSearchOutcome.Success -> {
                val existing = localResults.players.map { it.playerName.lowercase() }.toSet()
                val merged = localResults.players + outcome.results
                    .filter { it.name.lowercase() !in existing }
                    .map { p ->
                        SearchRepository.SearchResult.PlayerResult(
                            playerName = p.name,
                            teamName = p.country,
                            role = "",
                            relevanceScore = NETWORK_PLAYER_SCORE,
                            imageUrl = p.imageUrl,
                        )
                    }
                _results.value = _results.value.copy(
                    players = merged.sortedByDescending { it.relevanceScore }.take(MAX_PLAYER_RESULTS)
                )
            }
        }
    }

    /** Everything that costs no Appwrite execution: in-memory matches, Room, and the series scrape. */
    private suspend fun performLocalSearch(
        newQuery: String,
        matches: List<CricketMatch>,
        news: List<NewsArticleData>,
    ): SearchRepository.SearchResults {
        val teamNames = (matches.flatMap { m -> m.teams.map { it.name } } + SearchRepository.KNOWN_CRICKET_TEAMS).distinct()
        val bundle = coroutineScope {
            val liveDeferred = async { SearchRepository.search(newQuery, matches, news, teamNames) }
            val dbSeriesDeferred = async { SearchRepository.searchSeriesFromDb(newQuery, db) }
            val dbPlayersDeferred = async { SearchRepository.searchPlayersFromDb(newQuery, db) }
            SearchBundle(liveDeferred.await(), dbSeriesDeferred.await(), dbPlayersDeferred.await())
        }
        val liveResults = bundle.live

        val dbSeries = when (val result = bundle.dbSeries) {
            is AppResult.Success -> result.data
            is AppResult.Error -> { Sentry.captureException(result.exception); emptyList() }
            is AppResult.Loading -> emptyList()
        }

        val resolvedSeries = resolveSeries(newQuery, dbSeries)

        val dbPlayers = bundle.dbPlayers.let { result ->
            if (result is AppResult.Error) Sentry.captureException(result.exception)
            result.getOrDefault(emptyList())
        }

        // Merge live + DB players (deduplicate by name, case-insensitive)
        val livePlayerNames = liveResults.players.map { it.playerName.lowercase() }.toSet()
        val mergedPlayers = liveResults.players +
            dbPlayers.filter { it.playerName.lowercase() !in livePlayerNames }

        return liveResults.copy(
            players = mergedPlayers.sortedByDescending { it.relevanceScore }.take(MAX_PLAYER_RESULTS),
            series = resolvedSeries,
        ).also { _results.value = it }
    }

    /**
     * Falls back to a live network fetch when Room has no series yet.
     *
     * Acronym-expanded like the Room path: the corpus stores "Indian Premier League 2026" and a
     * raw `contains("ipl")` against it has never matched anything.
     */
    private suspend fun resolveSeries(
        newQuery: String,
        dbSeries: List<SearchRepository.SearchResult.SeriesResult>,
    ): List<SearchRepository.SearchResult.SeriesResult> {
        if (dbSeries.isNotEmpty()) return dbSeries
        val terms = competitionAliasesFor(newQuery).map { it.lowercase() }
        if (terms.isEmpty()) return emptyList()
        return runCatching {
            seriesScraper.fetchActiveSeries()
                .mapNotNull { series ->
                    val name = series.name.lowercase()
                    val category = series.category.name.lowercase()
                    val nameHit = terms.any { name.contains(it) }
                    if (!nameHit && terms.none { category.contains(it) }) return@mapNotNull null
                    SearchRepository.SearchResult.SeriesResult(
                        seriesId = series.id,
                        name = series.name,
                        category = series.category.name,
                        startDate = series.startDate,
                        endDate = series.endDate,
                        relevanceScore = if (nameHit) 1.0 else CATEGORY_ONLY_SERIES_SCORE,
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun retrySearch(matches: List<CricketMatch>, news: List<NewsArticleData>) {
        onQueryChange(_query.value, matches, news)
    }

    fun onTabSelected(tab: SearchTab) {
        _selectedTab.value = tab
    }

    fun onSearchSubmitted(query: String) {
        SearchRepository.addRecentQuery(getApplication(), query)
        _recentQueries.value = SearchRepository.getRecentQueries(getApplication())
    }

    fun clearRecentQueries() {
        SearchRepository.clearRecentQueries(getApplication())
        _recentQueries.value = emptyList()
    }
}
