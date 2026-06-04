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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.SearchTab
import com.tanush.bouncer.viewmodels.SearchViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    matches: List<CricketMatch>,
    news: List<NewsArticleData>,
    onMatchClick: (CricketMatch) -> Unit,
    onPlayerClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onNewsClick: (NewsArticleData) -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val keyboardController = LocalSoftwareKeyboardController.current

    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.loadRecentQueries()
    }

    val displayResults = when (selectedTab) {
        SearchTab.ALL -> results
        SearchTab.MATCHES -> results.copy(
            teams = emptyList(), players = emptyList(), news = emptyList(), series = emptyList()
        )
        SearchTab.PLAYERS -> results.copy(
            matches = emptyList(), teams = emptyList(), news = emptyList(),
            series = emptyList(), historicalMatches = emptyList()
        )
        SearchTab.TEAMS -> results.copy(
            matches = emptyList(), players = emptyList(), news = emptyList(),
            series = emptyList(), historicalMatches = emptyList()
        )
        SearchTab.TOURNAMENTS -> results.copy(
            matches = emptyList(), teams = emptyList(), players = emptyList(),
            news = emptyList(), historicalMatches = emptyList()
        )
        SearchTab.NEWS -> results.copy(
            matches = emptyList(), teams = emptyList(), players = emptyList(),
            series = emptyList(), historicalMatches = emptyList()
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column {
                SearchBarHeader(
                    query = query,
                    onQueryChange = { viewModel.onQueryChange(it, matches, news) },
                    onClear = { viewModel.onQueryChange("", matches, news) },
                    onBack = onBackClick,
                    onSearch = {
                        if (query.isNotBlank()) {
                            viewModel.onSearchSubmitted(query)
                            keyboardController?.hide()
                        }
                    },
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (query.length >= 2) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = colors.background,
                    contentColor = colors.primary,
                    edgePadding = spacing.md,
                ) {
                    SearchTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { viewModel.onTabSelected(tab) },
                            text = {
                                val count = when (tab) {
                                    SearchTab.ALL -> results.totalCount
                                    SearchTab.MATCHES -> results.matches.size
                                    SearchTab.PLAYERS -> results.players.size
                                    SearchTab.TEAMS -> results.teams.size
                                    SearchTab.TOURNAMENTS -> results.series.size
                                    SearchTab.NEWS -> results.news.size
                                }
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = spacing.md),
            ) {
                // Suggestion banner
                displayResults.suggestion?.let { suggestion ->
                    item(key = "suggestion") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onQueryChange(suggestion, matches, news) }
                                .padding(horizontal = spacing.lg, vertical = spacing.sm),
                        ) {
                            Text("Did you mean: ", style = typography.bodyMedium, color = colors.textSecondary)
                            Text(
                                suggestion,
                                style = typography.bodyMedium,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Empty state — show recent/trending when no query
                if (query.length < 2) {
                    if (recentQueries.isNotEmpty()) {
                        item(key = "recent_header") {
                            SectionHeaderV2(
                                title = "Recent Searches",
                                action = "Clear",
                                onAction = { viewModel.clearRecentQueries() },
                            )
                        }
                        items(recentQueries, key = { "recent_$it" }) { q ->
                            SearchSuggestionRow(
                                text = q,
                                onClick = { viewModel.onQueryChange(q, matches, news) },
                                icon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.textSecondary,
                                    )
                                },
                            )
                        }
                        item(key = "recent_divider") {
                            Spacer(Modifier.height(spacing.md))
                        }
                    }

                    item(key = "trending_header") {
                        SectionHeaderV2(title = "Trending")
                    }
                    items(SearchRepository.getTrendingSearches(), key = { "trend_$it" }) { trend ->
                        SearchSuggestionRow(
                            text = trend,
                            onClick = { viewModel.onQueryChange(trend, matches, news) },
                            icon = {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = colors.primary,
                                )
                            },
                        )
                    }
                    return@LazyColumn
                }

                // Empty results state
                if (displayResults.isEmpty && query.length >= 2) {
                    item(key = "no_results") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No results found", style = typography.titleMedium, color = colors.onSurface)
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                "Try a different search term",
                                style = typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    return@LazyColumn
                }

                // Match results
                if (displayResults.matches.isNotEmpty()) {
                    item(key = "matches_header") {
                        SectionHeaderV2(title = "Matches (${displayResults.matches.size})")
                    }
                    items(displayResults.matches, key = { "m_${it.match.id}" }) { result ->
                        MatchCardV2(
                            match = result.match,
                            onClick = {
                                viewModel.onSearchSubmitted(query)
                                onMatchClick(result.match)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.xs),
                        )
                    }
                }

                // Team results
                if (displayResults.teams.isNotEmpty()) {
                    item(key = "teams_header") {
                        SectionHeaderV2(title = "Teams (${displayResults.teams.size})")
                    }
                    items(displayResults.teams, key = { "t_${it.teamName}" }) { result ->
                        TeamSearchRow(
                            teamName = result.teamName,
                            acronym = result.teamAcronym,
                            logoUrl = result.logoUrl,
                            onClick = {
                                viewModel.onSearchSubmitted(query)
                                viewModel.onQueryChange(result.teamName, matches, news)
                            },
                        )
                    }
                }

                // Player results
                if (displayResults.players.isNotEmpty()) {
                    item(key = "players_header") {
                        SectionHeaderV2(title = "Players (${displayResults.players.size})")
                    }
                    items(displayResults.players, key = { "p_${it.playerName}" }) { result ->
                        PlayerSearchRow(
                            playerName = result.playerName,
                            teamName = result.teamName,
                            role = result.role,
                            onClick = {
                                viewModel.onSearchSubmitted(query)
                                onPlayerClick(result.playerName)
                            },
                        )
                    }
                }

                // News results
                if (displayResults.news.isNotEmpty()) {
                    item(key = "news_header") {
                        SectionHeaderV2(title = "News (${displayResults.news.size})")
                    }
                    items(displayResults.news, key = { "n_${it.article.id}" }) { result ->
                        NewsSearchRow(
                            title = result.article.headline,
                            source = result.article.source,
                            onClick = {
                                viewModel.onSearchSubmitted(query)
                                onNewsClick(result.article)
                            },
                        )
                    }
                }

                // Series / Tournament results
                if (displayResults.series.isNotEmpty()) {
                    item(key = "series_header") {
                        SectionHeaderV2(title = "Tournaments (${displayResults.series.size})")
                    }
                    items(displayResults.series, key = { "s_${it.seriesId}" }) { result ->
                        SeriesSearchRow(
                            name = result.name,
                            category = result.category,
                            startDate = result.startDate,
                            endDate = result.endDate,
                            onClick = {
                                viewModel.onSearchSubmitted(query)
                                onSeriesClick(result.seriesId)
                            },
                        )
                    }
                }

                // Historical match results
                if (displayResults.historicalMatches.isNotEmpty()) {
                    item(key = "historical_header") {
                        SectionHeaderV2(title = "Historical Matches (${displayResults.historicalMatches.size})")
                    }
                    items(displayResults.historicalMatches, key = { "h_${it.matchId}" }) { result ->
                        HistoricalMatchSearchRow(
                            matchTitle = result.matchTitle,
                            teams = result.teams,
                            venue = result.venue,
                            date = result.date,
                            season = result.season,
                            matchType = result.matchType,
                            onClick = { viewModel.onSearchSubmitted(query) },
                        )
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Search Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchBarHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .padding(horizontal = spacing.sm, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textSecondary)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(spacing.sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search matches, players, teams, tournaments…",
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = typography.bodyMedium.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.width(spacing.sm))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Result Row Components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchSuggestionRow(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(spacing.md))
        Text(text, style = typography.bodyMedium, color = colors.onSurface)
    }
}

@Composable
private fun TeamSearchRow(
    teamName: String,
    acronym: String,
    logoUrl: String?,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoUrl != null) {
            AsyncImage(model = logoUrl, contentDescription = teamName, modifier = Modifier.size(32.dp).clip(CircleShape))
        } else {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(acronym.take(2), style = typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(teamName, style = typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Medium)
            Text(acronym, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun PlayerSearchRow(
    playerName: String,
    teamName: String,
    role: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(playerName, style = typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Medium)
            Text("$teamName · $role", style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun NewsSearchRow(
    title: String,
    source: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Text(title, style = typography.bodyMedium, color = colors.onSurface, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        Text(source, style = typography.bodySmall, color = colors.textSecondary)
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = spacing.sm))
    }
}

@Composable
private fun SeriesSearchRow(
    name: String,
    category: String,
    startDate: String,
    endDate: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                category.take(1),
                style = typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(name, style = typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Medium, maxLines = 1)
            val subtitle = if (startDate.isNotEmpty() && endDate.isNotEmpty()) "$startDate – $endDate" else category
            Text(subtitle, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun HistoricalMatchSearchRow(
    matchTitle: String,
    teams: List<String>,
    venue: String,
    date: String,
    season: String,
    matchType: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                matchTitle,
                style = typography.bodyMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Spacer(Modifier.width(spacing.xs))
            Text(matchType, style = typography.labelSmall, color = colors.primary)
        }
        val meta = listOfNotNull(
            venue.takeIf { it.isNotEmpty() },
            season.takeIf { it.isNotEmpty() },
            date.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = typography.bodySmall, color = colors.textSecondary)
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = spacing.sm))
    }
}
