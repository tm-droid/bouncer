package com.tanush.bouncer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.utils.PreferencesHelper
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
import com.tanush.bouncer.ui.theme.v2.bouncerScreenBackground
import com.tanush.bouncer.ui.screens.RefreshFailureSnackbarEffect
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R

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
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val pref = PreferencesHelper.getString(context, PreferencesHelper.KEY_PREFERRED_CATEGORY, "All")
        if (pref != "All") viewModel.setCompetitionType(pref)
    }

    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val selectedCompetitionType by viewModel.selectedCompetitionType.collectAsStateWithLifecycle()
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val unreadCount by NotificationInboxManager.unreadCount.collectAsStateWithLifecycle()
    val subscriptions by MatchSubscriptionManager.subscriptions.collectAsStateWithLifecycle()
    val isOffline = rememberIsOffline()
    val favourites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()

    val spacing = BouncerThemeV2.spacing
    val colors = BouncerThemeV2.colors

    // Personalization: matches involving followed teams float to the top and
    // the hero slot prefers a live favourite-team match (serial position —
    // "your cricket" is what the feed opens with).
    val favouriteTeamNames = remember(favourites) {
        favourites.map { it.trim().lowercase() }.toSet()
    }
    val featuredMatch = remember(currentMatches, favouriteTeamNames) {
        currentMatches.firstOrNull { it.isLive && it.hasFavouriteTeam(favouriteTeamNames) }
            ?: currentMatches.firstOrNull()
    }
    val remainingMatches = remember(currentMatches, featuredMatch, favouriteTeamNames) {
        if (featuredMatch == null) {
            emptyList()
        } else {
            val (fav, others) = currentMatches
                .filterNot { it.id == featuredMatch.id }
                .partition { it.hasFavouriteTeam(favouriteTeamNames) }
            fav + others
        }
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

    // Category chips are progressive-disclosure (Hick's law: one row of choices
    // by default). Auto-expand whenever a non-default category is active so a
    // hidden filter can never silently shape the feed.
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val categoryActive = selectedCompetitionType.isNotBlank() && selectedCompetitionType != "All"
    LaunchedEffect(categoryActive) {
        if (categoryActive) filtersExpanded = true
    }

    // Refresh failed while content is on screen: keep the stale list, say so.
    // (The empty-list failure case is handled by the EmptyState below.)
    val snackbarHostState = remember { SnackbarHostState() }
    RefreshFailureSnackbarEffect(
        loadingState = loadingState,
        hasContent = currentMatches.isNotEmpty(),
        snackbarHostState = snackbarHostState,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                BouncerTopBarV2(
                    title = stringResource(R.string.home_title),
                    actions = {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(
                                        if (unreadCount > 9) {
                                            stringResource(R.string.home_unread_badge_overflow)
                                        } else {
                                            unreadCount.toString()
                                        }
                                    ) }
                                }
                            },
                        ) {
                            IconButton(onClick = onNotificationsClick) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = stringResource(R.string.cd_notifications),
                                )
                            }
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_search),
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                            )
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
                modifier = Modifier.fillMaxSize().bouncerScreenBackground(),
                contentPadding = PaddingValues(vertical = spacing.md),
            ) {
                // ── Status filter chips (+ toggle for category filters) ──
                item(key = "status_chips") {
                    FilterChipRowV2(
                        items = statusOptions,
                        selectedItem = mapStatus(selectedStatus),
                        onItemSelected = { chip ->
                            viewModel.setAndFetchMatches(mapChipToStatus(chip))
                        },
                        counts = statusCounts,
                        trailingContent = {
                            IconButton(onClick = { filtersExpanded = !filtersExpanded }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(
                                        if (filtersExpanded) {
                                            R.string.cd_filters_hide
                                        } else {
                                            R.string.cd_filters_show
                                        }
                                    ),
                                    tint = if (categoryActive) colors.primary else colors.textSecondary,
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(spacing.xs))
                }

                // ── Category filter chips (collapsed by default) ──
                item(key = "category_chips") {
                    AnimatedVisibility(
                        visible = filtersExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        FilterChipRowV2(
                            items = categoryOptions,
                            selectedItem = selectedCompetitionType.ifBlank { "All" },
                            onItemSelected = { chip ->
                                viewModel.setCompetitionType(if (chip == "All") "All" else chip)
                            },
                        )
                    }
                    Spacer(Modifier.height(spacing.md))
                }

                // ── Loading shimmers ──
                if (isLoading && currentMatches.isEmpty()) {
                    items(3, key = { "shimmer_$it" }) {
                        // Mirror the real row's geometry so first paint does not jump.
                        ShimmerMatchCard(
                            modifier = Modifier.padding(
                                horizontal = spacing.lg,
                                vertical = spacing.rowVertical,
                            ),
                        )
                    }
                    return@LazyColumn
                }

                // ── Empty state ──
                if (currentMatches.isEmpty() && !isLoading) {
                    item(key = "empty") {
                        EmptyStateV2(
                            title = stringResource(R.string.home_empty_title),
                            subtitle = stringResource(R.string.home_empty_subtitle),
                            icon = Icons.Outlined.SportsCricket,
                        )
                    }
                    return@LazyColumn
                }

                // ── Hero featured match ──
                featuredMatch?.let { fm ->
                    item(key = "hero_${fm.id}") {
                        val handleHeroClick = remember(fm.id) { { onMatchClick(fm) } }
                        // Full-bleed: the hero and the rows below share one surface, so no
                        // horizontal inset and no gap — its own trailing divider is the break.
                        HeroMatchCard(
                            match = fm,
                            onClick = handleHeroClick,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // ── All matches section ──
                if (remainingMatches.isNotEmpty()) {
                    item(key = "matches_header") {
                        val handleMoreClick = remember { { onMoreMatchesClick() } }
                        SectionHeaderV2(
                            title = stringResource(R.string.section_matches),
                            action = if (remainingMatches.size > 5) stringResource(R.string.action_see_all) else null,
                            onAction = handleMoreClick,
                        )
                    }
                    items(remainingMatches, key = { "match_${it.id}" }) { match ->
                        val handleMatchClick = remember(match.id) {
                            { onMatchClick(match) }
                        }
                        val handleSubscribeToggle = remember(match.id) {
                            { MatchSubscriptionManager.toggle(match.id); Unit }
                        }
                        MatchCardV2(
                            match = match,
                            onClick = handleMatchClick,
                            // No inset and no gap — the row owns its own padding and divider.
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            isSubscribed = match.id in subscriptions,
                            onSubscribeToggle = handleSubscribeToggle,
                            isFavouriteTeam = match.hasFavouriteTeam(favouriteTeamNames),
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

private fun CricketMatch.hasFavouriteTeam(favouriteNames: Set<String>): Boolean =
    favouriteNames.isNotEmpty() && teams.any { it.name.trim().lowercase() in favouriteNames }

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
