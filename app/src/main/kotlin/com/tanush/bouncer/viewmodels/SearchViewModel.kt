package com.tanush.bouncer.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchTab { ALL, MATCHES, PLAYERS, TEAMS, TOURNAMENTS, NEWS }

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BouncerDatabase.getInstance(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(SearchRepository.SearchResults())
    val results: StateFlow<SearchRepository.SearchResults> = _results.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchTab.ALL)
    val selectedTab: StateFlow<SearchTab> = _selectedTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null

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
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isLoading.value = true
            try {
                val teamNames = matches.flatMap { m -> m.teams.map { it.name } }.distinct()
                // Run live search and DB search concurrently
                val liveResults = SearchRepository.search(newQuery, matches, news, teamNames)
                val (dbSeries, dbHistorical) = SearchRepository.searchFromDb(newQuery, db)
                val dbPlayers = SearchRepository.searchPlayersFromDb(newQuery, db)

                // Merge DB players with live players (deduplicate by name)
                val livePlayerNames = liveResults.players.map { it.playerName }.toSet()
                val mergedPlayers = liveResults.players + dbPlayers.filter { it.playerName !in livePlayerNames }

                _results.value = liveResults.copy(
                    players = mergedPlayers.sortedByDescending { it.relevanceScore }.take(10),
                    series = dbSeries,
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

    fun onSearchSubmitted(context: Context, query: String) {
        SearchRepository.addRecentQuery(context, query)
    }
}
