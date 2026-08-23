package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.SearchTab
import com.tanush.bouncer.viewmodels.SearchViewModel
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.tanush.bouncer.R

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    matches: List<CricketMatch>,
    news: List<NewsArticleData>,
    onMatchClick: (CricketMatch) -> Unit,
    onPlayerClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onNewsClick: (NewsArticleData) -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val keyboardController = LocalSoftwareKeyboardController.current

    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchError by viewModel.error.collectAsStateWithLifecycle()
    val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.loadRecentQueries()
    }

    val displayResults by remember(selectedTab, results) {
        derivedStateOf { displayResultsFor(selectedTab, results) }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            SearchTopBar(
                query = query,
                isLoading = isLoading,
                focusRequester = focusRequester,
                onQueryChange = { viewModel.onQueryChange(it, matches, news) },
                onClear = { viewModel.onQueryChange("", matches, news) },
                onBack = onBackClick,
                onSearch = {
                    if (query.isNotBlank()) {
                        viewModel.onSearchSubmitted(query)
                        keyboardController?.hide()
                    }
                },
            )
        },
    ) { innerPadding ->
        SearchScreenBody(
            modifier = Modifier.padding(innerPadding),
            query = query,
            selectedTab = selectedTab,
            results = results,
            displayResults = displayResults,
            searchError = searchError,
            recentQueries = recentQueries,
            viewModel = viewModel,
            matches = matches,
            news = news,
            onMatchClick = onMatchClick,
            onTeamClick = onTeamClick,
            onPlayerClick = onPlayerClick,
            onNewsClick = onNewsClick,
            onSeriesClick = onSeriesClick,
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    isLoading: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    Column {
        SearchBarHeader(
            query = query,
            onQueryChange = onQueryChange,
            onClear = onClear,
            onBack = onBack,
            onSearch = onSearch,
            focusRequester = focusRequester,
        )
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary,
                trackColor = colors.surfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchScreenBody(
    modifier: Modifier,
    query: String,
    selectedTab: SearchTab,
    results: SearchRepository.SearchResults,
    displayResults: SearchRepository.SearchResults,
    searchError: String?,
    recentQueries: List<String>,
    viewModel: SearchViewModel,
    matches: List<CricketMatch>,
    news: List<NewsArticleData>,
    onMatchClick: (CricketMatch) -> Unit,
    onTeamClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onNewsClick: (NewsArticleData) -> Unit,
    onSeriesClick: (String) -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    Column(modifier = modifier.fillMaxSize()) {
        if (query.length >= 2) {
            SearchResultsTabRow(
                selectedTab = selectedTab,
                results = results,
                onTabSelected = viewModel::onTabSelected,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = spacing.md),
        ) {
            displayResults.suggestion?.let { suggestion ->
                suggestionBanner(suggestion) { viewModel.onQueryChange(suggestion, matches, news) }
            }

            when {
                query.length < 2 -> searchSuggestions(
                    recentQueries = recentQueries,
                    onQuery = { viewModel.onQueryChange(it, matches, news) },
                    onClearRecent = { viewModel.clearRecentQueries() },
                )
                searchError != null && displayResults.isEmpty -> item(key = "search_error") {
                    EmptyStateV2(
                        title = stringResource(R.string.search_error_title),
                        subtitle = searchError ?: "",
                        onRetry = { viewModel.retrySearch(matches, news) },
                    )
                }
                displayResults.isEmpty -> item(key = "no_results") { NoResultsMessage() }
                else -> {
                    searchResults(
                        displayResults = displayResults,
                        onMatchClick = { viewModel.onSearchSubmitted(query); onMatchClick(it) },
                        onTeamClick = { viewModel.onSearchSubmitted(query); onTeamClick(it) },
                        onPlayerClick = { viewModel.onSearchSubmitted(query); onPlayerClick(it) },
                        onNewsClick = { viewModel.onSearchSubmitted(query); onNewsClick(it) },
                        onSeriesClick = { viewModel.onSearchSubmitted(query); onSeriesClick(it) },
                    )
                    item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

private fun displayResultsFor(
    tab: SearchTab,
    results: SearchRepository.SearchResults,
): SearchRepository.SearchResults = when (tab) {
    SearchTab.ALL -> results
    SearchTab.MATCHES -> results.copy(
        teams = emptyList(), players = emptyList(), news = emptyList(), series = emptyList()
    )
    SearchTab.PLAYERS -> results.copy(
        matches = emptyList(), teams = emptyList(), news = emptyList(), series = emptyList()
    )
    SearchTab.TEAMS -> results.copy(
        matches = emptyList(), players = emptyList(), news = emptyList(), series = emptyList()
    )
    SearchTab.TOURNAMENTS -> results.copy(
        matches = emptyList(), teams = emptyList(), players = emptyList(), news = emptyList()
    )
    SearchTab.NEWS -> results.copy(
        matches = emptyList(), teams = emptyList(), players = emptyList(), series = emptyList()
    )
}

@Composable
private fun SearchResultsTabRow(
    selectedTab: SearchTab,
    results: SearchRepository.SearchResults,
    onTabSelected: (SearchTab) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = colors.background,
        contentColor = colors.primary,
        edgePadding = spacing.md,
    ) {
        SearchTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    val count = tabCount(tab, results)
                    val label = tab.name.lowercase().replaceFirstChar { it.uppercase() }
                    Text(
                        if (count > 0) "$label ($count)" else label,
                        style = typography.labelMedium,
                    )
                },
            )
        }
    }
}

private fun tabCount(tab: SearchTab, results: SearchRepository.SearchResults): Int = when (tab) {
    SearchTab.ALL -> results.totalCount
    SearchTab.MATCHES -> results.matches.size
    SearchTab.PLAYERS -> results.players.size
    SearchTab.TEAMS -> results.teams.size
    SearchTab.TOURNAMENTS -> results.series.size
    SearchTab.NEWS -> results.news.size
}

private fun LazyListScope.suggestionBanner(suggestion: String, onClick: () -> Unit) {
    item(key = "suggestion") {
        val colors = BouncerThemeV2.colors
        val typography = BouncerThemeV2.typography
        val spacing = BouncerThemeV2.spacing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Text(
                stringResource(R.string.search_did_you_mean),
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                suggestion,
                style = typography.bodyMedium,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun LazyListScope.searchSuggestions(
    recentQueries: List<String>,
    onQuery: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    if (recentQueries.isNotEmpty()) {
        item(key = "recent_header") {
            SectionHeaderV2(
                title = stringResource(R.string.search_recent_title),
                action = stringResource(R.string.action_clear_short),
                onAction = onClearRecent,
            )
        }
        items(recentQueries, key = { "recent_$it" }) { q ->
            SearchSuggestionRow(
                text = q,
                onClick = { onQuery(q) },
                icon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = BouncerThemeV2.colors.textSecondary,
                    )
                },
            )
        }
        item(key = "recent_divider") {
            Spacer(Modifier.height(BouncerThemeV2.spacing.md))
        }
    }

    item(key = "trending_header") {
        SectionHeaderV2(title = stringResource(R.string.search_trending_title))
    }
    items(SearchRepository.getTrendingSearches(), key = { "trend_$it" }) { trend ->
        SearchSuggestionRow(
            text = trend,
            onClick = { onQuery(trend) },
            icon = {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = BouncerThemeV2.colors.primary,
                )
            },
        )
    }
}

/** A result section, paired with the relevance of its strongest member. */
private class SearchSection(val score: Double, val emit: LazyListScope.() -> Unit)

/**
 * Emits the result sections ordered by their best member's relevance.
 *
 * This used to be a fixed Matches → Teams → Players → News → Tournaments precedence, with each
 * bucket sorted only within itself. Scores were therefore never compared across types: searching
 * "India" put an exact team hit (1.0) below whatever fuzzy match hits (0.39) happened to exist,
 * which is invisible while the team has fixtures of its own and obviously wrong when it does not.
 *
 * `sortedByDescending` is stable, so ties keep the declaration order below — the old precedence
 * survives exactly where relevance has nothing to say.
 */
private fun LazyListScope.searchResults(
    displayResults: SearchRepository.SearchResults,
    onMatchClick: (CricketMatch) -> Unit,
    onTeamClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onNewsClick: (NewsArticleData) -> Unit,
    onSeriesClick: (String) -> Unit,
) {
    listOfNotNull(
        displayResults.matches.sectionOrNull(R.string.section_matches, "m", { "m_${it.match.id}" }) { result ->
            MatchCardV2(
                match = result.match,
                onClick = { onMatchClick(result.match) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = BouncerThemeV2.spacing.lg,
                        vertical = BouncerThemeV2.spacing.xs,
                    ),
            )
        },
        displayResults.teams.sectionOrNull(R.string.search_section_teams, "t", { "t_${it.teamName}" }) { result ->
            TeamSearchRow(
                teamName = result.teamName,
                acronym = result.teamAcronym,
                logoUrl = result.logoUrl,
                onClick = { onTeamClick(result.teamName) },
            )
        },
        displayResults.players.sectionOrNull(R.string.search_section_players, "p", { "p_${it.playerName}" }) { result ->
            PlayerSearchRow(
                playerName = result.playerName,
                teamName = result.teamName,
                role = result.role,
                imageUrl = result.imageUrl,
                onClick = { onPlayerClick(result.playerName) },
            )
        },
        displayResults.news.sectionOrNull(R.string.search_section_news, "n", { "n_${it.article.id}" }) { result ->
            NewsSearchRow(
                title = result.article.headline,
                source = result.article.source,
                onClick = { onNewsClick(result.article) },
            )
        },
        displayResults.series.sectionOrNull(R.string.search_section_tournaments, "s", { "s_${it.seriesId}" }) { result ->
            SeriesSearchRow(
                name = result.name,
                category = result.category,
                startDate = result.startDate,
                endDate = result.endDate,
                onClick = { onSeriesClick(result.seriesId) },
            )
        },
    ).sortedByDescending { it.score }.forEach { it.emit(this) }
}

/**
 * A section for [this], or null when there is nothing to show.
 *
 * The score is the best member's, because that is what the section competes on: a list containing
 * one exact hit and nine weak ones is a strong section, not an average one.
 */
/**
 * [keyPrefix] is separate from [titleRes] on purpose: the heading is translated, and a LazyList item
 * key built from translated text would change identity when the locale does.
 */
private fun <T : SearchRepository.SearchResult> List<T>.sectionOrNull(
    @StringRes titleRes: Int,
    keyPrefix: String,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
): SearchSection? {
    if (isEmpty()) return null
    val results = this
    return SearchSection(maxOf { it.relevanceScore }) {
        item(key = "${keyPrefix}_header") {
            SectionHeaderV2(
                title = stringResource(
                    R.string.search_section_with_count,
                    stringResource(titleRes),
                    results.size,
                )
            )
        }
        items(results, key = key) { row(it) }
    }
}
