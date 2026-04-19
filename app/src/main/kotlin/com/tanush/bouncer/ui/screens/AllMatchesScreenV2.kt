package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.ShimmerMatchCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

/**
 * AllMatchesScreenV2 — Dedicated match list tab.
 * Two filter rows (status + format), series-grouped match cards, date separators.
 */
@Composable
fun AllMatchesScreenV2(
    viewModel: LiveActivityViewModel,
    onMatchClick: (CricketMatch) -> Unit,
) {
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val selectedCompetitionType by viewModel.selectedCompetitionType.collectAsState()
    val currentMatches by viewModel.currentMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val spacing = BouncerThemeV2.spacing

    // Local format filter (not in ViewModel)
    var selectedFormat by remember { mutableStateOf("All") }

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
        topBar = { BouncerTopBarV2(title = "Matches") },
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
                        onItemSelected = { selectedFormat = it },
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

                // Empty
                if (filteredMatches.isEmpty() && !isLoading) {
                    item(key = "empty") {
                        EmptyStateV2(
                            title = "No matches",
                            subtitle = "No ${selectedFormat.lowercase()} matches found for the current filter",
                        )
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
