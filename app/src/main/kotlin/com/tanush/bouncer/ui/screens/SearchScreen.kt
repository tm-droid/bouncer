package com.tanush.bouncer.ui.screens

import androidx.compose.material.icons.outlined.CloudOff
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.PastMatchRow
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.PlayerLookupState
import com.tanush.bouncer.viewmodels.SearchTab
import com.tanush.bouncer.viewmodels.SearchViewModel

/** Rows per section on the All tab; the rest are one tap away on the section's own tab. */
private const val ALL_TAB_MATCHES = 3
private const val ALL_TAB_TEAMS = 3
private const val ALL_TAB_PLAYERS = 4
private const val ALL_TAB_SERIES = 3
private const val ALL_TAB_NEWS = 3
private const val ALL_TAB_PAST = 3

/** Shorter queries show suggestions, not results. */
private const val MIN_QUERY_LENGTH = 2

/** Everything a result row can do, bundled so the list builders stay readable. */
private class SearchActions(
    val onMatch: (CricketMatch) -> Unit,
    val onTeam: (String) -> Unit,
    val onPlayer: (String, String?) -> Unit,
    val onNews: (NewsArticleData) -> Unit,
    val onSeries: (String) -> Unit,
    val onSeeAll: (SearchTab) -> Unit,
)

/** Everything the result list renders from, as one value. */
private class SearchListState(
    val query: String,
    val results: SearchRepository.SearchResults,
    val selectedTab: SearchTab,
    val isLoading: Boolean,
    val playerLookup: PlayerLookupState,
    val error: String?,
    val recentQueries: List<String>,
    val suggestions: List<String>,
    val suggestionsAreLive: Boolean,
) {
    /** The online lookup's row shows on the tabs that list players, while it runs or has failed. */
    val showPlayerLookup: Boolean
        get() = playerLookup != PlayerLookupState.IDLE && selectedTab in PLAYER_TABS
}

private val PLAYER_TABS = setOf(SearchTab.ALL, SearchTab.PLAYERS)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    matches: List<CricketMatch>,
    news: List<NewsArticleData>,
    onMatchClick: (CricketMatch) -> Unit,
    onPlayerClick: (name: String, cricbuzzId: String?) -> Unit,
    onTeamClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onNewsClick: (NewsArticleData) -> Unit,
    onBackClick: () -> Unit,
    initialTab: SearchTab? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val state = collectSearchListState(viewModel)
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Focus the field on first entry only. Requesting it on every composition reopened the
    // keyboard over the results each time the user came back from a match or a player.
    var focusRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusRequested) {
            focusRequested = true
            focusRequester.requestFocus()
        }
        viewModel.loadRecentQueries()
    }
    LaunchedEffect(initialTab) { initialTab?.let(viewModel::applyInitialTab) }
    LaunchedEffect(matches, news) { viewModel.onSourcesChanged(matches, news) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (scrolling) keyboardController?.hide() }
    }

    // Leaving for a result records the query and puts the keyboard away first.
    val leave: (() -> Unit) -> Unit = { action ->
        viewModel.onSearchSubmitted(state.query)
        keyboardController?.hide()
        focusManager.clearFocus()
        action()
    }
    val actions = SearchActions(
        onMatch = { leave { onMatchClick(it) } }, onTeam = { leave { onTeamClick(it) } },
        onPlayer = { name, id -> leave { onPlayerClick(name, id) } },
        onNews = { leave { onNewsClick(it) } }, onSeries = { leave { onSeriesClick(it) } },
        onSeeAll = viewModel::onTabSelected,
    )

    Scaffold(
        containerColor = BouncerThemeV2.colors.background,
        topBar = {
            SearchTopBar(
                query = state.query,
                busy = state.isLoading || state.playerLookup == PlayerLookupState.SEARCHING,
                focusRequester = focusRequester,
                viewModel = viewModel,
                onBack = onBackClick,
                onSubmit = { keyboardController?.hide() },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.query.length >= MIN_QUERY_LENGTH) {
                SearchResultsTabRow(state.selectedTab, state.results, viewModel::onTabSelected)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = BouncerThemeV2.spacing.md),
            ) {
                searchBody(state, viewModel, actions)
            }
        }
    }
}

@Composable
private fun collectSearchListState(viewModel: SearchViewModel): SearchListState {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val playerLookup by viewModel.playerLookup.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val suggestionsAreLive by viewModel.suggestionsAreLive.collectAsStateWithLifecycle()
    return SearchListState(
        query, results, selectedTab, isLoading, playerLookup, error, recentQueries, suggestions, suggestionsAreLive,
    )
}

@Composable
private fun SearchTopBar(
    query: String,
    busy: Boolean,
    focusRequester: FocusRequester,
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    Column {
        SearchBarHeader(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onClear = { viewModel.onQueryChange("") },
            onBack = onBack,
            onSearch = {
                if (query.isNotBlank()) {
                    viewModel.onSearchSubmitted(query)
                    onSubmit()
                }
            },
            focusRequester = focusRequester,
        )
        // Visible for the online player leg too, which used to run with no indicator and then
        // drop players into the list with no warning.
        AnimatedVisibility(visible = busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primaryText,
                trackColor = colors.surfaceVariant,
            )
        }
    }
}

/** Suggestions before typing; then an error, an empty state, or the results. */
private fun LazyListScope.searchBody(state: SearchListState, viewModel: SearchViewModel, actions: SearchActions) {
    val displayResults = displayResultsFor(state.selectedTab, state.results)
    when {
        state.query.length < MIN_QUERY_LENGTH -> searchSuggestions(state, viewModel)
        state.error != null && displayResults.isEmpty -> item(key = "search_error") {
            EmptyStateV2(
                title = stringResource(R.string.search_error_title),
                icon = Icons.Outlined.CloudOff,
                subtitle = state.error,
                onRetry = { viewModel.onQueryChange(state.query) },
            )
        }
        displayResults.isEmpty && !state.showPlayerLookup && !state.isLoading -> item(key = "no_results") {
            NoResultsMessage(
                query = state.query,
                suggestion = state.results.suggestion,
                onSuggestion = viewModel::onQueryChange,
                onClear = { viewModel.onQueryChange("") },
            )
        }
        else -> {
            displayResults.suggestion?.let { suggestion ->
                suggestionBanner(suggestion) { viewModel.onQueryChange(suggestion) }
            }
            searchResults(
                results = displayResults,
                compact = state.selectedTab == SearchTab.ALL,
                query = state.query,
                playerLookup = state.playerLookup.takeIf { state.showPlayerLookup },
                actions = actions,
            )
            item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun LazyListScope.suggestionBanner(suggestion: String, onClick: () -> Unit) {
    item(key = "suggestion") {
        SearchSuggestionRow(
            text = suggestion,
            onClick = onClick,
            icon = {
                Text(
                    stringResource(R.string.search_did_you_mean),
                    style = BouncerThemeV2.typography.bodyMedium,
                    color = BouncerThemeV2.colors.textSecondary,
                )
            },
        )
    }
}

private fun LazyListScope.searchSuggestions(state: SearchListState, viewModel: SearchViewModel) {
    val recentQueries = state.recentQueries
    val suggestionsAreLive = state.suggestionsAreLive
    val onQuery: (String) -> Unit = viewModel::onQueryChange
    if (recentQueries.isNotEmpty()) {
        item(key = "recent_header") {
            SectionHeaderV2(
                title = stringResource(R.string.search_recent_title),
                action = stringResource(R.string.action_clear_short),
                onAction = viewModel::clearRecentQueries,
            )
        }
        itemsIndexed(recentQueries, key = { index, q -> "recent_${index}_$q" }) { _, q ->
            SearchSuggestionRow(
                text = q,
                onClick = { onQuery(q) },
                onRemove = { viewModel.removeRecentQuery(q) },
                icon = { SuggestionIcon(Icons.Default.History) },
            )
        }
        item(key = "recent_divider") {
            Spacer(Modifier.height(BouncerThemeV2.spacing.md))
        }
    }

    item(key = "suggestions_header") {
        SectionHeaderV2(
            title = stringResource(
                if (suggestionsAreLive) R.string.search_live_now_title else R.string.search_trending_title
            )
        )
    }
    itemsIndexed(state.suggestions, key = { index, s -> "suggest_${index}_$s" }) { _, suggestion ->
        SearchSuggestionRow(
            text = suggestion,
            onClick = { onQuery(suggestion) },
            icon = {
                // Red is reserved for live; a trending term is not live.
                if (suggestionsAreLive) {
                    Icon(
                        Icons.Filled.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = BouncerThemeV2.colors.live,
                    )
                } else {
                    SuggestionIcon(Icons.Default.Star)
                }
            },
        )
    }
}

@Composable
private fun SuggestionIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = BouncerThemeV2.colors.textSecondary)
}

/** A result section, paired with the relevance of its strongest member. */
private class SearchSection(val score: Double, val emit: LazyListScope.() -> Unit)

/**
 * Emits the result sections ordered by their best member's relevance.
 *
 * `sortedByDescending` is stable, so ties keep the declaration order below — the old fixed
 * Matches → Teams → Players → News → Tournaments precedence survives exactly where relevance has
 * nothing to say. When [compact] (the All tab) each section shows its first few rows and a "See all"
 * that opens its own tab: ten full match cards pushed every other kind of result off the screen.
 */
private fun LazyListScope.searchResults(
    results: SearchRepository.SearchResults,
    compact: Boolean,
    query: String,
    playerLookup: PlayerLookupState?,
    actions: SearchActions,
) {
    fun limit(all: Int) = if (compact) all else Int.MAX_VALUE
    listOfNotNull(
        results.matches.sectionOrNull(
            R.string.section_matches, "m", SearchTab.MATCHES, limit(ALL_TAB_MATCHES), compact, actions,
            key = { it.match.id },
        ) { result ->
            MatchCardV2(match = result.match, onClick = { actions.onMatch(result.match) }, modifier = Modifier.fillMaxWidth())
        },
        results.pastMatches.sectionOrNull(
            R.string.search_section_past_matches, "pm", SearchTab.MATCHES, limit(ALL_TAB_PAST), compact, actions,
            key = { it.row.id },
        ) { result ->
            PastMatchRow(row = result.row, onClick = { actions.onMatch(result.row.toCricketMatch()) })
        },
        results.teams.sectionOrNull(
            R.string.search_section_teams, "t", SearchTab.TEAMS, limit(ALL_TAB_TEAMS), compact, actions,
            key = { it.teamName },
        ) { result ->
            TeamSearchRow(
                teamName = result.teamName,
                acronym = result.teamAcronym,
                logoUrl = result.logoUrl,
                fixtureCount = result.fixtureCount,
                query = query,
                onClick = { actions.onTeam(result.teamName) },
            )
        },
        playersSection(results.players, limit(ALL_TAB_PLAYERS), compact, query, playerLookup, actions),
        results.news.sectionOrNull(
            R.string.search_section_news, "n", SearchTab.NEWS, limit(ALL_TAB_NEWS), compact, actions,
            key = { it.article.id },
        ) { result ->
            NewsSearchRow(
                title = result.article.headline,
                source = result.article.source,
                query = query,
                onClick = { actions.onNews(result.article) },
            )
        },
        results.series.sectionOrNull(
            R.string.search_section_tournaments, "s", SearchTab.TOURNAMENTS, limit(ALL_TAB_SERIES), compact, actions,
            key = { it.seriesId },
        ) { result ->
            SeriesSearchRow(
                name = result.name,
                category = result.category,
                startDate = result.startDate,
                endDate = result.endDate,
                query = query,
                onClick = { actions.onSeries(result.seriesId) },
            )
        },
    ).sortedByDescending { it.score }.forEach { it.emit(this) }
}

/**
 * Players, plus the online lookup's state as a row of its own. The section exists while the
 * lookup is running or has failed even with no players yet, so "unavailable" is never hidden by
 * other results — it used to show only when nothing at all had matched.
 */
private fun playersSection(
    players: List<SearchRepository.SearchResult.PlayerResult>,
    limit: Int,
    compact: Boolean,
    query: String,
    playerLookup: PlayerLookupState?,
    actions: SearchActions,
): SearchSection? {
    if (players.isEmpty() && playerLookup == null) return null
    val section = players.sectionOrNull(
        R.string.search_section_players, "p", SearchTab.PLAYERS, limit, compact, actions,
        key = { it.cricbuzzId ?: it.playerName },
    ) { result ->
        PlayerSearchRow(
            playerName = result.playerName,
            teamName = result.teamName,
            role = result.role,
            imageUrl = result.imageUrl,
            query = query,
            onClick = { actions.onPlayer(result.playerName, result.cricbuzzId) },
        )
    }
    return SearchSection(section?.score ?: 0.0) {
        if (section != null) {
            section.emit(this)
        } else {
            item(key = "p_header") { SectionHeaderV2(title = stringResource(R.string.search_section_players)) }
        }
        if (playerLookup != null) {
            item(key = "p_lookup") {
                PlayerLookupRow(
                    stringResource(
                        if (playerLookup == PlayerLookupState.SEARCHING) R.string.search_players_searching
                        else R.string.search_players_unavailable
                    )
                )
            }
        }
    }
}

/**
 * A section for [this], or null when there is nothing to show.
 *
 * The score is the best member's, because that is what the section competes on: a list containing
 * one exact hit and nine weak ones is a strong section, not an average one. [keyPrefix] is separate
 * from [titleRes] because the heading is translated and a key built from it would change identity
 * with the locale; row keys carry the index because [key] can be scraped text, which is not unique.
 */
private fun <T : SearchRepository.SearchResult> List<T>.sectionOrNull(
    @StringRes titleRes: Int,
    keyPrefix: String,
    tab: SearchTab,
    limit: Int,
    compact: Boolean,
    actions: SearchActions,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
): SearchSection? {
    if (isEmpty()) return null
    val all = this
    val shown = take(limit)
    return SearchSection(maxOf { it.relevanceScore }) {
        item(key = "${keyPrefix}_header") {
            val hidden = compact && all.size > shown.size
            SectionHeaderV2(
                title = stringResource(R.string.search_section_with_count, stringResource(titleRes), all.size),
                action = if (hidden) stringResource(R.string.search_see_all, all.size) else null,
                onAction = if (hidden) ({ actions.onSeeAll(tab) }) else null,
            )
        }
        itemsIndexed(shown, key = { index, item -> "${keyPrefix}_${index}_${key(item)}" }) { _, item -> row(item) }
    }
}
