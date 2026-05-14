package com.tanush.bouncer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.NetworkStatusBanner
import com.tanush.bouncer.ui.components.rememberIsOffline
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.HeroMatchCard
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.ShimmerMatchCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

/**
 * HomeScreenV2 — The main home tab.
 *
 * Sections: Filter chips → Hero card (featured live) → Live Now horizontal scroll →
 *           Upcoming vertical list → Recent Results → News headlines
 */
@Composable
fun HomeScreenV2(
    viewModel: LiveActivityViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    onMoreMatchesClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onNewsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
) {
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val selectedCompetitionType by viewModel.selectedCompetitionType.collectAsStateWithLifecycle()
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val unreadCount by NotificationInboxManager.unreadCount.collectAsStateWithLifecycle()
    val subscriptions by MatchSubscriptionManager.subscriptions.collectAsStateWithLifecycle()
    val isOffline = rememberIsOffline()

    val spacing = BouncerThemeV2.spacing

    val featuredMatch by remember {
        derivedStateOf { currentMatches.firstOrNull() }
    }
    val remainingMatches by remember {
        derivedStateOf { if (featuredMatch != null) currentMatches.drop(1) else emptyList() }
    }
    val statusCounts by remember {
        derivedStateOf {
            if (currentMatches.isNotEmpty()) {
                mapOf(mapStatus(selectedStatus) to currentMatches.size)
            } else {
                emptyMap()
            }
        }
    }

    val statusOptions = listOf("All", "Live", "Upcoming", "Recent")
    val categoryOptions = listOf("All", "International", "League", "Domestic")

    Scaffold(
        topBar = {
            Column {
                BouncerTopBarV2(
                    title = "BOUNCER",
                    actions = {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(if (unreadCount > 9) "9+" else unreadCount.toString()) }
                                }
                            },
                        ) {
                            IconButton(onClick = onNotificationsClick) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
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
                // ── Status filter chips ──
                item(key = "status_chips") {
                    FilterChipRowV2(
                        items = statusOptions,
                        selectedItem = mapStatus(selectedStatus),
                        onItemSelected = { chip ->
                            viewModel.setAndFetchMatches(mapChipToStatus(chip))
                        },
                        counts = statusCounts,
                    )
                    Spacer(Modifier.height(spacing.xs))
                }

                // ── Category filter chips ──
                item(key = "category_chips") {
                    FilterChipRowV2(
                        items = categoryOptions,
                        selectedItem = selectedCompetitionType.ifBlank { "All" },
                        onItemSelected = { chip ->
                            viewModel.setCompetitionType(if (chip == "All") "All" else chip)
                        },
                    )
                    Spacer(Modifier.height(spacing.md))
                }

                // ── Loading shimmers ──
                if (isLoading && currentMatches.isEmpty()) {
                    items(3, key = { "shimmer_$it" }) {
                        ShimmerMatchCard(
                            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                        )
                    }
                    return@LazyColumn
                }

                // ── Empty state ──
                if (currentMatches.isEmpty() && !isLoading) {
                    item(key = "empty") {
                        EmptyStateV2(
                            title = "No matches found",
                            subtitle = "Try changing your filters or pull to refresh",
                        )
                    }
                    return@LazyColumn
                }

                // ── Hero featured match ──
                featuredMatch?.let { fm ->
                    item(key = "hero_${fm.id}") {
                        HeroMatchCard(
                            match = fm,
                            onClick = { onMatchClick(fm) },
                            modifier = Modifier.padding(horizontal = spacing.lg),
                        )
                        Spacer(Modifier.height(spacing.lg))
                    }
                }

                // ── All matches section ──
                if (remainingMatches.isNotEmpty()) {
                    item(key = "matches_header") {
                        SectionHeaderV2(
                            title = "Matches",
                            action = if (remainingMatches.size > 5) "See All" else null,
                            onAction = { onMoreMatchesClick() },
                        )
                    }
                    items(remainingMatches, key = { "match_${it.id}" }) { match ->
                        val handleMatchClick = remember(match) {
                            { onMatchClick(match) }
                        }
                        val handleSubscribeToggle = remember(match) {
                            { MatchSubscriptionManager.toggle(match.id); Unit }
                        }
                        MatchCardV2(
                            match = match,
                            onClick = handleMatchClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.xs),
                            isSubscribed = match.id in subscriptions,
                            onSubscribeToggle = handleSubscribeToggle,
                        )
                    }
                    item(key = "matches_spacer") {
                        Spacer(Modifier.height(spacing.md))
                    }
                }

                // Bottom spacing for nav bar
                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────

private fun mapStatus(vmStatus: String): String = when (vmStatus) {
    "Live" -> "Live"
    "Upcoming" -> "Upcoming"
    "Recent", "Completed" -> "Recent"
    else -> "All"
}

private fun mapChipToStatus(chip: String): String = when (chip) {
    "Live" -> "Live"
    "Upcoming" -> "Upcoming"
    "Recent" -> "Recent"
    else -> "Live" // Default to live matches
}
