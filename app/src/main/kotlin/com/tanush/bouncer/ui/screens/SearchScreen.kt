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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.delay

/**
 * SearchScreen — Full-screen search with animated search bar and categorized results.
 */
@Composable
fun SearchScreen(
    matches: List<CricketMatch>,
    news: List<NewsArticleData>,
    onMatchClick: (CricketMatch) -> Unit,
    onPlayerClick: (String) -> Unit,
    onNewsClick: (NewsArticleData) -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchRepository.SearchResults()) }
    var recentQueries by remember { mutableStateOf(SearchRepository.getRecentQueries(context)) }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus search field
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Debounced search
    LaunchedEffect(query) {
        if (query.length < 2) {
            results = SearchRepository.SearchResults()
            return@LaunchedEffect
        }
        delay(300) // debounce
        val teamNames = matches.flatMap { m -> m.teams.map { it.name } }.distinct()
        results = SearchRepository.search(query, matches, news, teamNames)
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            // Search bar as top bar
            SearchBarHeader(
                query = query,
                onQueryChange = { query = it },
                onClear = {
                    query = ""
                    results = SearchRepository.SearchResults()
                },
                onBack = onBackClick,
                onSearch = {
                    if (query.isNotBlank()) {
                        SearchRepository.addRecentQuery(context, query)
                        recentQueries = SearchRepository.getRecentQueries(context)
                        keyboardController?.hide()
                    }
                },
                focusRequester = focusRequester,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = spacing.md),
        ) {
            // Suggestion banner
            results.suggestion?.let { suggestion ->
                item(key = "suggestion") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { query = suggestion }
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
                // Recent searches
                if (recentQueries.isNotEmpty()) {
                    item(key = "recent_header") {
                        SectionHeaderV2(
                            title = "Recent Searches",
                            action = "Clear",
                            onAction = {
                                SearchRepository.clearRecentQueries(context)
                                recentQueries = emptyList()
                            },
                        )
                    }
                    items(recentQueries, key = { "recent_$it" }) { q ->
                        SearchSuggestionRow(
                            text = q,
                            onClick = { query = q },
                            icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textSecondary) },
                        )
                    }
                    item(key = "recent_divider") {
                        Spacer(Modifier.height(spacing.md))
                    }
                }

                // Trending
                item(key = "trending_header") {
                    SectionHeaderV2(title = "Trending")
                }
                items(SearchRepository.getTrendingSearches(), key = { "trend_$it" }) { trend ->
                    SearchSuggestionRow(
                        text = trend,
                        onClick = { query = trend },
                        icon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.primary) },
                    )
                }
                return@LazyColumn
            }

            // Search results — empty
            if (results.isEmpty && query.length >= 2) {
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
            if (results.matches.isNotEmpty()) {
                item(key = "matches_header") {
                    SectionHeaderV2(title = "Matches (${results.matches.size})")
                }
                items(results.matches, key = { "m_${it.match.id}" }) { result ->
                    MatchCardV2(
                        match = result.match,
                        onClick = {
                            SearchRepository.addRecentQuery(context, query)
                            onMatchClick(result.match)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.xs),
                    )
                }
            }

            // Team results
            if (results.teams.isNotEmpty()) {
                item(key = "teams_header") {
                    SectionHeaderV2(title = "Teams (${results.teams.size})")
                }
                items(results.teams, key = { "t_${it.teamName}" }) { result ->
                    TeamSearchRow(
                        teamName = result.teamName,
                        acronym = result.teamAcronym,
                        logoUrl = result.logoUrl,
                        onClick = {
                            SearchRepository.addRecentQuery(context, query)
                            query = result.teamName
                        },
                    )
                }
            }

            // Player results
            if (results.players.isNotEmpty()) {
                item(key = "players_header") {
                    SectionHeaderV2(title = "Players (${results.players.size})")
                }
                items(results.players, key = { "p_${it.playerName}" }) { result ->
                    PlayerSearchRow(
                        playerName = result.playerName,
                        teamName = result.teamName,
                        role = result.role,
                        onClick = {
                            SearchRepository.addRecentQuery(context, query)
                            onPlayerClick(result.playerName)
                        },
                    )
                }
            }

            // News results
            if (results.news.isNotEmpty()) {
                item(key = "news_header") {
                    SectionHeaderV2(title = "News (${results.news.size})")
                }
                items(results.news, key = { "n_${it.article.id}" }) { result ->
                    NewsSearchRow(
                        title = result.article.headline,
                        source = result.article.source,
                        onClick = {
                            SearchRepository.addRecentQuery(context, query)
                            onNewsClick(result.article)
                        },
                    )
                }
            }

            // Bottom spacing
            item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
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
                            "Search matches, players, teams…",
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
