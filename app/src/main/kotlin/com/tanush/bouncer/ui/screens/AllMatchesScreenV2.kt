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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
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
import com.tanush.bouncer.ui.theme.v2.bouncerScreenBackground
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.LoadingState

/**
 * AllMatchesScreenV2 — the full match list, pushed from Home's "All matches" link.
 * Two filter rows (status + format) over a flat list of match cards.
 */
@Composable
fun AllMatchesScreenV2(
    viewModel: LiveActivityViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    onBackClick: () -> Unit,
) {
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val isOffline = rememberIsOffline()

    // Apply local format filter (memoized — list recomposes on live score ticks)
    val filteredMatches = remember(currentMatches, selectedFormat) {
        if (selectedFormat == "All") {
            currentMatches
        } else {
            currentMatches.filter { it.matchType.contains(selectedFormat, ignoreCase = true) }
        }
    }

    // Refresh failed while content is on screen: keep the stale list, say so.
    val snackbarHostState = remember { SnackbarHostState() }
    RefreshFailureSnackbarEffect(
        loadingState = loadingState,
        hasContent = currentMatches.isNotEmpty(),
        snackbarHostState = snackbarHostState,
    )

    Scaffold(
        topBar = {
            Column {
                BouncerTopBarV2(title = stringResource(R.string.common_matches), showBack = true, onBack = onBackClick)
                NetworkStatusBanner(isOffline = isOffline)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BouncerThemeV2.colors.background,
    ) { innerPadding ->
        PullRefreshWrapperV2(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshMatches() },
            modifier = Modifier.padding(innerPadding),
        ) {
            AllMatchesList(
                state = AllMatchesListState(
                    selectedStatus = selectedStatus,
                    selectedFormat = selectedFormat,
                    matches = filteredMatches,
                    showSkeleton = isLoading && currentMatches.isEmpty(),
                    showEmpty = filteredMatches.isEmpty() && !isLoading,
                    error = loadingState as? LoadingState.Error,
                ),
                onStatusSelected = { viewModel.setAndFetchMatches(it) },
                onFormatSelected = { viewModel.setFormat(it) },
                onRetry = { viewModel.refreshMatches() },
                onMatchClick = onMatchClick,
            )
        }
    }
}

private val STATUS_OPTIONS = listOf("Live", "Upcoming", "Recent")
private val FORMAT_OPTIONS = listOf("All", "T20", "ODI", "Test", "First Class")

private data class AllMatchesListState(
    val selectedStatus: String,
    val selectedFormat: String,
    val matches: List<CricketMatch>,
    val showSkeleton: Boolean,
    val showEmpty: Boolean,
    val error: LoadingState.Error?,
)

@Composable
private fun AllMatchesList(
    state: AllMatchesListState,
    onStatusSelected: (String) -> Unit,
    onFormatSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onMatchClick: (CricketMatch) -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().bouncerScreenBackground(),
        contentPadding = PaddingValues(vertical = spacing.md),
    ) {
        item(key = "status_filter") {
            FilterChipRowV2(
                items = STATUS_OPTIONS,
                selectedItem = mapStatusToChip(state.selectedStatus),
                onItemSelected = onStatusSelected,
            )
            Spacer(Modifier.height(spacing.xs))
        }
        item(key = "format_filter") {
            FilterChipRowV2(
                items = FORMAT_OPTIONS,
                selectedItem = state.selectedFormat,
                onItemSelected = onFormatSelected,
            )
            Spacer(Modifier.height(spacing.md))
        }

        when {
            state.showSkeleton -> items(4, key = { "shimmer_$it" }) {
                ShimmerMatchCard(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs))
            }
            state.showEmpty -> item(key = "empty") {
                AllMatchesEmptyState(error = state.error, selectedFormat = state.selectedFormat, onRetry = onRetry)
            }
            else -> {
                // Flat — the card already shows series/match info
                items(state.matches, key = { "match_${it.id}" }) { match ->
                    MatchCardV2(
                        match = match,
                        onClick = { onMatchClick(match) },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    )
                }
                item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun AllMatchesEmptyState(error: LoadingState.Error?, selectedFormat: String, onRetry: () -> Unit) {
    if (error != null) {
        EmptyStateV2(
            title = stringResource(R.string.matches_couldnt_load),
            subtitle = error.message,
            icon = Icons.Outlined.CloudOff,
            onRetry = onRetry,
        )
    } else {
        EmptyStateV2(
            title = stringResource(R.string.matches_none),
            subtitle = if (selectedFormat == "All") "No matches found" else "No $selectedFormat matches found",
            icon = Icons.Outlined.SportsCricket,
        )
    }
}

private fun mapStatusToChip(vmStatus: String): String = when {
    vmStatus.contains("Live", ignoreCase = true) -> "Live"
    vmStatus.contains("Upcoming", ignoreCase = true) -> "Upcoming"
    vmStatus.contains("Recent", ignoreCase = true) || vmStatus.contains("Completed", ignoreCase = true) -> "Recent"
    else -> "Live"
}

/** Shows the refresh-failure message as a snackbar when stale content is still visible. */
@Composable
internal fun RefreshFailureSnackbarEffect(
    loadingState: LoadingState,
    hasContent: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(loadingState) {
        val errorState = loadingState as? LoadingState.Error
        if (errorState != null && hasContent) {
            snackbarHostState.showSnackbar(errorState.message)
        }
    }
}
