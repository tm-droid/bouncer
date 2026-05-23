package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.NetworkStatusBanner
import com.tanush.bouncer.ui.components.rememberIsOffline
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.ShimmerMatchCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.LoadingState

/**
 * AllMatchesScreenV2 — Dedicated match list tab.
 * Two filter rows (status + format), series-grouped match cards, date separators.
 */
@Composable
fun AllMatchesScreenV2(
    viewModel: LiveActivityViewModel,
    onMatchClick: (CricketMatch) -> Unit,
) {
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val selectedCompetitionType by viewModel.selectedCompetitionType.collectAsStateWithLifecycle()
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val isOffline = rememberIsOffline()

    val spacing = BouncerThemeV2.spacing

    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()

    // Status chips
    val statusOptions = listOf("Live", "Upcoming", "Recent")
    // Format chips
    val formatOptions = listOf("All", "T20", "ODI", "Test", "First Class")

    // Apply local format filter
    val filteredMatches = if (selectedFormat == "All") {
        currentMatches
    } else {
        currentMatches.filter {
            it.matchType.contains(selectedFormat, ignoreCase = true)
        }
    }

    // No grouping — card already displays match/series info

    Scaffold(
        topBar = {
            Column {
                BouncerTopBarV2(title = "Matches")
                NetworkStatusBanner(isOffline = isOffline)
            }
        },
        containerColor = BouncerThemeV2.colors.background,
    ) { innerPadding ->
        PullRefreshWrapperV2(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshMatches() },
            modifier = Modifier.padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = spacing.md),
            ) {
                // Status filter
                item(key = "status_filter") {
                    FilterChipRowV2(
                        items = statusOptions,
                        selectedItem = mapStatusToChip(selectedStatus),
                        onItemSelected = { chip ->
                            viewModel.setAndFetchMatches(chip)
                        },
                    )
                    Spacer(Modifier.height(spacing.xs))
                }

                // Format filter
                item(key = "format_filter") {
                    FilterChipRowV2(
                        items = formatOptions,
                        selectedItem = selectedFormat,
                        onItemSelected = { viewModel.setFormat(it) },
                    )
                    Spacer(Modifier.height(spacing.md))
                }

                // Loading
                if (isLoading && currentMatches.isEmpty()) {
                    items(4, key = { "shimmer_$it" }) {
                        ShimmerMatchCard(
                            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                        )
                    }
                    return@LazyColumn
                }

                // Empty or error
                if (filteredMatches.isEmpty() && !isLoading) {
                    item(key = "empty") {
                        val errorState = loadingState as? LoadingState.Error
                        if (errorState != null) {
                            EmptyStateV2(
                                title = "Couldn't load matches",
                                subtitle = errorState.message,
                                onRetry = { viewModel.refreshMatches() },
                            )
                        } else {
                            EmptyStateV2(
                                title = "No matches",
                                subtitle = if (selectedFormat == "All") "No matches found" else "No $selectedFormat matches found",
                            )
                        }
                    }
                    return@LazyColumn
                }

                // Match list (flat — card already shows series/match info)
                items(filteredMatches, key = { "match_${it.id}" }) { match ->
                    MatchCardV2(
                        match = match,
                        onClick = { onMatchClick(match) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.xs),
                    )
                }

                // Bottom spacing
                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

private fun mapStatusToChip(vmStatus: String): String = when {
    vmStatus.contains("Live", ignoreCase = true) -> "Live"
    vmStatus.contains("Upcoming", ignoreCase = true) -> "Upcoming"
    vmStatus.contains("Recent", ignoreCase = true) || vmStatus.contains("Completed", ignoreCase = true) -> "Recent"
    else -> "Live"
}
