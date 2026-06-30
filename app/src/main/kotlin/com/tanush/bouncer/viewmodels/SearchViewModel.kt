package com.tanush.bouncer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.network.PlayerProfileScraper
import com.tanush.bouncer.network.SeriesScraper
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.utils.bouncerExceptionHandler
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
    val dbFrom: AppResult<Pair<List<SearchRepository.SearchResult.SeriesResult>, List<SearchRepository.SearchResult.HistoricalMatchResult>>>,
    val dbPlayers: AppResult<List<SearchRepository.SearchResult.PlayerResult>>,
    val networkPlayers: List<SearchRepository.SearchResult.PlayerResult>,
)

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
        if (newQuery.length < 2) {
            _results.value = SearchRepository.SearchResults()
            _isLoading.value = false
            return
        }
        searchJob = viewModelScope.launch(bouncerExceptionHandler) {
            delay(300) // debounce
            _isLoading.value = true
            try {
                val teamNames = (matches.flatMap { m -> m.teams.map { it.name } } + SearchRepository.KNOWN_CRICKET_TEAMS).distinct()
                val bundle = coroutineScope {
                    val liveDeferred = async { SearchRepository.search(newQuery, matches, news, teamNames) }
                    val dbFromDeferred = async { SearchRepository.searchFromDb(newQuery, db) }
                    val dbPlayersDeferred = async { SearchRepository.searchPlayersFromDb(newQuery, db) }
                    val networkPlayersDeferred = async {
                        runCatching { PlayerProfileScraper.searchPlayers(newQuery) }.getOrDefault(emptyList())
                            .map { p ->
                                SearchRepository.SearchResult.PlayerResult(
                                    playerName = p.name,
                                    teamName = p.country,
                                    role = "",
                                    relevanceScore = 0.9,
                                    imageUrl = p.imageUrl,
                                )
                            }
                    }
                    SearchBundle(liveDeferred.await(), dbFromDeferred.await(), dbPlayersDeferred.await(), networkPlayersDeferred.await())
                }
                val liveResults = bundle.live
                val dbFromResult = bundle.dbFrom
                val dbPlayersResult = bundle.dbPlayers
                val networkPlayerResults = bundle.networkPlayers

                val (dbSeries, dbHistorical) = when (dbFromResult) {
                    is AppResult.Success -> dbFromResult.data
                    is AppResult.Error -> { Sentry.captureException(dbFromResult.exception); Pair(emptyList(), emptyList()) }
                    is AppResult.Loading -> Pair(emptyList(), emptyList())
                }

                // Fall back to live network fetch if Room DB has no series yet
                val resolvedSeries: List<SearchRepository.SearchResult.SeriesResult> = if (dbSeries.isEmpty()) {
                    runCatching {
                        val q = newQuery.trim().lowercase()
                        seriesScraper.fetchActiveSeries()
                            .filter { series ->
                                series.name.lowercase().contains(q) ||
                                    series.category.name.lowercase().contains(q)
                            }
                            .map { series ->
                                SearchRepository.SearchResult.SeriesResult(
                                    seriesId = series.id,
                                    name = series.name,
                                    category = series.category.name,
                                    startDate = series.startDate,
                                    endDate = series.endDate,
                                    relevanceScore = if (series.name.lowercase().contains(q)) 1.0 else 0.7,
                                )
                            }
                    }.getOrDefault(emptyList())
                } else {
                    dbSeries
                }

                val dbPlayers = dbPlayersResult.let { result ->
                    if (result is AppResult.Error) Sentry.captureException(result.exception)
                    result.getOrDefault(emptyList())
                }

                // Merge live + DB + network players (deduplicate by name, case-insensitive)
                val livePlayerNames = liveResults.players.map { it.playerName.lowercase() }.toSet()
                val dbPlayerNames = dbPlayers.map { it.playerName.lowercase() }.toSet()
                val mergedPlayers = liveResults.players +
                    dbPlayers.filter { it.playerName.lowercase() !in livePlayerNames } +
                    networkPlayerResults.filter {
                        it.playerName.lowercase() !in livePlayerNames && it.playerName.lowercase() !in dbPlayerNames
                    }

                _results.value = liveResults.copy(
                    players = mergedPlayers.sortedByDescending { it.relevanceScore }.take(10),
                    series = resolvedSeries,
                    historicalMatches = dbHistorical,
                )
            } finally {
                _isLoading.value = false
            }
        }
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
