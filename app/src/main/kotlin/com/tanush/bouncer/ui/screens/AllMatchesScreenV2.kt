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
import androidx.compose.foundation.lazy.rememberLazyListState
import java.time.ZoneId
import java.time.LocalDate
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyListScope
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val isOffline = rememberIsOffline()
    var byDate by rememberSaveable { mutableStateOf(false) }
    val source = rememberMatchSource(viewModel, byDate)
    // Memoized — the list recomposes on live score ticks.
    val filteredMatches = remember(source, selectedFormat) { source.withFormat(selectedFormat) }

    // Refresh failed while content is on screen: keep the stale list, say so.
    val snackbarHostState = remember { SnackbarHostState() }
    RefreshFailureSnackbarEffect(
        loadingState = loadingState,
        hasContent = source.isNotEmpty(),
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
                    selectedStatus = if (byDate) BY_DATE else selectedStatus,
                    selectedFormat = selectedFormat,
                    matches = filteredMatches,
                    showSkeleton = isLoading && source.isEmpty(),
                    showEmpty = filteredMatches.isEmpty() && !isLoading,
                    error = loadingState as? LoadingState.Error,
                ),
                onStatusSelected = {
                    byDate = it == BY_DATE
                    if (!byDate) viewModel.setAndFetchMatches(it)
                },
                onFormatSelected = { viewModel.setFormat(it) },
                onRetry = { viewModel.refreshMatches() },
                onMatchClick = onMatchClick,
            )
        }
    }
}

/**
 * The status list, or — for "By date" — every status the app has cached. By date is a view over
 * what is already held, not a fourth fetch; opening it only warms the statuses not yet loaded.
 */
@Composable
private fun rememberMatchSource(viewModel: LiveActivityViewModel, byDate: Boolean): List<CricketMatch> {
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val allMatches by viewModel.allMatches.collectAsStateWithLifecycle()
    LaunchedEffect(byDate) { if (byDate) viewModel.warmAllStatuses() }
    return if (byDate) allMatches else currentMatches
}

private fun List<CricketMatch>.withFormat(format: String): List<CricketMatch> =
    if (format == "All") this else filter { it.matchType.contains(format, ignoreCase = true) }

private const val BY_DATE = "By date"

/** The status and format chip rows above the list. */
private const val FILTER_ROWS = 2
private val STATUS_OPTIONS = listOf("Live", "Upcoming", "Recent", BY_DATE)
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
    val listState = rememberLazyListState()
    // By date opens on today, not on the oldest cached result a week back.
    LaunchedEffect(state.selectedStatus == BY_DATE, state.matches.isNotEmpty()) {
        if (state.selectedStatus == BY_DATE && state.matches.isNotEmpty()) {
            listState.scrollToItem(FILTER_ROWS + todayOffset(state.matches, ZoneId.systemDefault()))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().bouncerScreenBackground(),
        contentPadding = PaddingValues(vertical = spacing.md),
    ) {
        item(key = "status_filter") {
            FilterChipRowV2(
                items = STATUS_OPTIONS,
                selectedItem = if (state.selectedStatus == BY_DATE) BY_DATE else mapStatusToChip(state.selectedStatus),
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
            state.selectedStatus == BY_DATE -> matchesByDayItems(state.matches, onMatchClick)
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

/** One header per day, then that day's matches; days ascending, so results sit above fixtures. */
private fun LazyListScope.matchesByDayItems(matches: List<CricketMatch>, onMatchClick: (CricketMatch) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    groupByDay(matches, zone).forEach { (day, dayMatches) ->
        item(key = "day_$day") {
            SectionHeaderV2(title = dayLabel(day, today))
        }
        items(dayMatches, key = { "day_${day}_match_${it.id}" }) { match ->
            MatchCardV2(match = match, onClick = { onMatchClick(match) }, modifier = Modifier.fillMaxWidth())
        }
    }
    item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
}
