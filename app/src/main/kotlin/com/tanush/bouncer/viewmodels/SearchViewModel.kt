package com.tanush.bouncer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.R
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.data.history.HistoryCoverage
import com.tanush.bouncer.data.history.PastMatchIndex
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.network.PlayerProfileScraper
import com.tanush.bouncer.network.PlayerSearchOutcome
import com.tanush.bouncer.network.PlayerSearchResult
import com.tanush.bouncer.network.SeriesScraper
import com.tanush.bouncer.utils.bouncerExceptionHandler
import com.tanush.bouncer.utils.normalizeForSearch
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchTab { ALL, MATCHES, PLAYERS, TEAMS, TOURNAMENTS, NEWS }

/** Local sources answer at this delay; the screen feels responsive at 300 ms. */
private const val LOCAL_DEBOUNCE_MS = 300L

/** The billed Appwrite leg waits for the user to genuinely stop typing. */
private const val NETWORK_DEBOUNCE_MS = 800L

/** Below this, a query is too broad to be worth an execution. Local sources still run at 2. */
private const val MIN_NETWORK_QUERY_LENGTH = 3

/** Shorter queries show suggestions, not results. */
private const val MIN_QUERY_LENGTH = 2

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val corpora = SearchCorpusLoader(BouncerDatabase.getInstance(application), SeriesScraper())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Everything that costs no Appwrite execution. Recomputed when the sources change. */
    private val _localResults = MutableStateFlow(SearchRepository.SearchResults())

    /** The last online player answer and the normalised query it answers. */
    private val _networkPlayers = MutableStateFlow<Pair<String, List<PlayerSearchResult>>?>(null)

    val results: StateFlow<SearchRepository.SearchResults> =
        combine(_localResults, _networkPlayers) { local, network ->
            val merged = if (network == null) local
            else local.copy(players = mergePlayers(_query.value, local.players, network.second, network.first))
            merged.copy(pastPlayers = attachHistory(local.pastPlayers, merged.players))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchRepository.SearchResults())

    private val _selectedTab = MutableStateFlow(SearchTab.ALL)
    val selectedTab: StateFlow<SearchTab> = _selectedTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playerLookup = MutableStateFlow(PlayerLookupState.IDLE)
    val playerLookup: StateFlow<PlayerLookupState> = _playerLookup.asStateFlow()

    /** Non-null when the whole local search failed (vs. genuinely empty results). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    private val _matches = MutableStateFlow<List<CricketMatch>>(emptyList())
    private val _news = MutableStateFlow<List<NewsArticleData>>(emptyList())

    /** Series still running, from `series_cache`; the idle suggestions when nothing is live. */
    private val _currentSeries = MutableStateFlow<List<String>>(emptyList())

    /** What is live right now, else the current series, else a fixed list. */
    val suggestions: StateFlow<List<String>> = combine(_matches, _currentSeries) { matches, series ->
        SearchRepository.liveSuggestions(matches).ifEmpty { series }.ifEmpty { SearchRepository.getTrendingSearches() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchRepository.getTrendingSearches())

    private val _liveNow = MutableStateFlow(false)
    val suggestionsAreLive: StateFlow<Boolean> = _liveNow.asStateFlow()

    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var initialTabApplied = false


    init {
        viewModelScope.launch(bouncerExceptionHandler) {
            _currentSeries.value = SearchRepository.currentSeriesSuggestions(
                corpora.corpusFor(_matches.value).series, System.currentTimeMillis(),
            )
        }
        // The index is a few MB over the network on first use; a query typed before it lands is re-run.
        viewModelScope.launch(bouncerExceptionHandler) {
            if (PastMatchIndex.load()) {
                PastMatchIndex.shared.first { it != null }
                onSourcesChanged(_matches.value, _news.value)
            }
        }
    }

    fun loadRecentQueries() {
        _recentQueries.value = SearchRepository.getRecentQueries(getApplication())
    }

    /** The tab a caller opened search on (the Series and News tabs). Applied once. */
    fun applyInitialTab(tab: SearchTab) {
        if (initialTabApplied) return
        initialTabApplied = true
        _selectedTab.value = tab
    }

    /**
     * The screen's live lists. Scores repoll every few seconds; results used to be a snapshot
     * taken at the keystroke, so a match found while live kept its old score for as long as the
     * screen stayed open. A change re-runs the local leg only — never the billed player lookup.
     */
    fun onSourcesChanged(matches: List<CricketMatch>, news: List<NewsArticleData>) {
        _matches.value = matches
        _news.value = news
        _liveNow.value = matches.any { it.isLive }
        val query = _query.value
        if (query.length < MIN_QUERY_LENGTH || searchJob?.isActive == true) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(bouncerExceptionHandler) {
            try {
                performLocalSearch(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The results on screen stay; a background refresh has no error state of its own.
                Sentry.captureException(e)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        refreshJob?.cancel()
        _error.value = null
        if (newQuery.length < MIN_QUERY_LENGTH) {
            _localResults.value = SearchRepository.SearchResults()
            _networkPlayers.value = null
            _isLoading.value = false
            _playerLookup.value = PlayerLookupState.IDLE
            return
        }
        searchJob = viewModelScope.launch(bouncerExceptionHandler) {
            delay(LOCAL_DEBOUNCE_MS)
            _isLoading.value = true
            val localResults = try {
                performLocalSearch(newQuery)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Partial-source failures degrade inside performLocalSearch; this is the
                // whole-search failure path — surface it instead of faking "no results".
                Sentry.captureException(e)
                _error.value = "Search hit a rain delay — check your connection and retry."
                null
            } finally {
                _isLoading.value = false
            }
            if (localResults != null) lookUpPlayersOnline(newQuery, localResults)
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
    private suspend fun lookUpPlayersOnline(
        newQuery: String,
        localResults: SearchRepository.SearchResults,
    ) {
        // A confident local hit already answers the question; no need to spend an execution.
        if (hasConfidentPlayer(localResults.players) ||
            newQuery.trim().length < MIN_NETWORK_QUERY_LENGTH
        ) {
            _playerLookup.value = PlayerLookupState.IDLE
            return
        }

        // Cancelled by the next keystroke before it fires; that keystroke sets its own state.
        _playerLookup.value = PlayerLookupState.SEARCHING
        delay(NETWORK_DEBOUNCE_MS - LOCAL_DEBOUNCE_MS)
        when (val outcome = PlayerProfileScraper.searchPlayersDetailed(newQuery)) {
            // Deliberately not an empty list. A dead player-search backend rendering as a clean
            // "no results" is exactly how the previous one stayed broken unnoticed — and it is
            // shown inline, so other results no longer hide it.
            is PlayerSearchOutcome.Unavailable -> _playerLookup.value = PlayerLookupState.UNAVAILABLE
            is PlayerSearchOutcome.Success -> {
                _networkPlayers.value = normalizeForSearch(newQuery) to outcome.results
                _playerLookup.value = PlayerLookupState.IDLE
            }
        }
    }

    /** In-memory matches, news and teams, plus the Room corpora, ranked together. */
    private suspend fun performLocalSearch(newQuery: String): SearchRepository.SearchResults {
        val matches = _matches.value
        val corpus = corpora.corpusFor(matches)
        val favourites = FavouriteTeamsManager.favourites.value.toSet()
        val results = coroutineScope {
            val live = async {
                SearchRepository.search(
                    newQuery, matches, _news.value, corpus.teams, favourites, TeamLogoManager::getLogoUrl,
                )
            }
            val series = async { SearchRepository.searchSeries(newQuery, corpus.series) }
            val players = async { SearchRepository.searchCachedPlayers(newQuery, corpus.players) }
            val past = async(Dispatchers.Default) {
                PastMatchIndex.shared.value?.search(newQuery, matches, corpus.teams).orEmpty()
            }
            val history = async { searchPastPlayers(newQuery) }
            val h2h = async { searchHeadToHead(newQuery, corpus.teams) }
            live.await().let {
                it.copy(
                    players = mergeLocalPlayers(it.players, players.await()),
                    series = series.await(),
                    pastMatches = past.await(),
                    pastPlayers = history.await(),
                    headToHead = h2h.await(),
                    coverageNotice = HistoryCoverage.missingAfghanistan(
                        newQuery, corpus.teams, getApplication<Application>().getString(R.string.history_missing_afghanistan),
                    ),
                )
            }
        }
        _localResults.value = results
        return results
    }

    fun onTabSelected(tab: SearchTab) {
        _selectedTab.value = tab
    }

    fun onSearchSubmitted(query: String) {
        SearchRepository.addRecentQuery(getApplication(), query)
        _recentQueries.value = SearchRepository.getRecentQueries(getApplication())
    }

    fun removeRecentQuery(query: String) {
        SearchRepository.removeRecentQuery(getApplication(), query)
        _recentQueries.value = SearchRepository.getRecentQueries(getApplication())
    }

    fun clearRecentQueries() {
        SearchRepository.clearRecentQueries(getApplication())
        _recentQueries.value = emptyList()
    }
}
