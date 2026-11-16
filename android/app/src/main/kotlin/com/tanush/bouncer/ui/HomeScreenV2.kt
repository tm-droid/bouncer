package com.tanush.bouncer.ui







import com.tanush.bouncer.services.MatchCacheService
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import com.tanush.bouncer.viewmodels.LoadingState
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.DelayedSkeleton
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FailedState
import com.tanush.bouncer.ui.components.v2.FailureKind
import com.tanush.bouncer.ui.components.v2.HeroMatchCard
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.ScreenState
import com.tanush.bouncer.ui.components.v2.ShimmerMatchCard
import com.tanush.bouncer.ui.components.v2.StaleDataBanner
import com.tanush.bouncer.ui.components.v2.screenStateOf
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.bouncerScreenBackground
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.STATUS_ALL
import com.tanush.bouncer.viewmodels.statusesToFetch
import com.tanush.bouncer.ui.screens.dayLabel
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * HomeScreenV2 — the main home tab.
 *
 * Top bar → status tabs (with the competition filter behind one icon) → the featured live match →
 * every other match as rows.
 *
 * The filters used to be two rows of boxed chips, the first carrying a count ("Live 14") and the
 * second sliding open under a toggle, above a "MATCHES" overline and a gold "See All". Cricbuzz and
 * Cricinfo both switch match lists with plain text tabs; that is what this is now.
 */
@Composable
fun HomeScreenV2(
    viewModel: LiveActivityViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
) {
    val (filters, onFiltersChanged) = rememberHomeFilters(viewModel)
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val favourites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()
    val favouriteNames = remember(favourites) { favourites.map { it.trim().lowercase() }.toSet() }
    val matches = remember(currentMatches, filters, favouriteNames) { currentMatches.narrowedBy(filters, favouriteNames) }
    val allMatches by viewModel.allMatches.collectAsStateWithLifecycle()
    val nextUp = remember(allMatches) { nextUpcomingMatch(allMatches, System.currentTimeMillis()) }
    // The disk cache stamps every successful fetch, so its newest stamp is when the list on screen was saved.
    val savedAtMs = remember(loadingState, selectedStatus) { MatchCacheService.savedAtMs(statusesToFetch(selectedStatus)) }

    Scaffold(
        topBar = {
            HomeHeader(selectedStatus, filters, onFiltersChanged, onNotificationsClick, onSearchClick) {
                viewModel.setAndFetchMatches(it)
            }
        },
        containerColor = BouncerThemeV2.colors.background,
    ) { innerPadding ->
        PullRefreshWrapperV2(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshMatches() },
            modifier = Modifier.padding(innerPadding),
        ) {
            HomeMatchList(
                matches = matches,
                favouriteNames = favouriteNames,
                byDay = dayOrderFor(selectedStatus),
                isLoading = isLoading,
                failure = (loadingState as? LoadingState.Error)?.kind,
                savedAtMs = savedAtMs,
                nextUp = nextUp,
                filtered = if (filters.active) FilteredList(filters.displayLabel()) { onFiltersChanged(HomeFilters()) } else null,
                scrollKey = selectedStatus to filters,
                onMatchClick = onMatchClick,
                onRetry = { viewModel.refreshMatches() },
                onSeeUpcoming = { viewModel.setAndFetchMatches(MatchCacheService.UPCOMING) }
                    .takeIf { selectedStatus == MatchCacheService.LIVE },
            )
        }
    }
}

/** The top bar and the status tabs with the filter menu. */
@Composable
private fun HomeHeader(
    selectedStatus: String,
    filters: HomeFilters,
    onFiltersChanged: (HomeFilters) -> Unit,
    onNotificationsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onStatusSelected: (String) -> Unit,
) {
    val unreadCount by NotificationInboxManager.unreadCount.collectAsStateWithLifecycle()
    Column {
        HomeTopBar(unreadCount, onNotificationsClick, onSearchClick)
        HomeFilterBar(
            selectedStatus = selectedStatus,
            filters = filters,
            onStatusSelected = onStatusSelected,
            onFiltersChanged = onFiltersChanged,
        )
    }
}

@Composable
private fun HomeTopBar(unreadCount: Int, onNotificationsClick: () -> Unit, onSearchClick: () -> Unit) {
    // Settings is not here: the More tab owns it, and a third icon only crowded the wordmark.
    BouncerTopBarV2(
        title = stringResource(R.string.home_title),
        showDivider = false,
        actions = {
            IconButton(onClick = onNotificationsClick) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge {
                                Text(
                                    if (unreadCount > MAX_BADGE_COUNT) {
                                        stringResource(R.string.home_unread_badge_overflow)
                                    } else {
                                        unreadCount.toString()
                                    }
                                )
                            }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.cd_notifications))
                }
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.cd_search))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFilterBar(
    selectedStatus: String,
    filters: HomeFilters,
    onStatusSelected: (String) -> Unit,
    onFiltersChanged: (HomeFilters) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val selectedIndex = STATUS_TABS.indexOf(tabForStatus(selectedStatus)).coerceAtLeast(0)
    // The bar's colour, not the page's: the tabs belong to the header, and in light mode the page grey
    // under a white bar drew one more band.
    Column(modifier = Modifier.background(colors.topBar)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = Modifier.weight(1f),
                containerColor = colors.topBar,
                contentColor = colors.onSurface,
                edgePadding = BouncerThemeV2.spacing.xs,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true),
                        width = Dp.Unspecified,
                        color = colors.primaryText,
                    )
                },
                divider = {},
            ) {
                STATUS_TABS.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onStatusSelected(STATUS_BY_TAB.getValue(tab)) },
                        text = {
                            Text(stringResource(STATUS_LABELS.getValue(tab)), style = BouncerThemeV2.typography.labelLarge)
                        },
                        selectedContentColor = colors.primaryText,
                        unselectedContentColor = colors.textSecondary,
                    )
                }
            }
            HomeFilterMenu(filters, onFiltersChanged)
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun HomeMatchList(
    matches: List<CricketMatch>,
    favouriteNames: Set<String>,
    byDay: DayOrder,
    isLoading: Boolean,
    failure: FailureKind?,
    savedAtMs: Long?,
    nextUp: CricketMatch?,
    filtered: FilteredList?,
    onMatchClick: (CricketMatch) -> Unit,
    onRetry: () -> Unit,
    onSeeUpcoming: (() -> Unit)?,
    scrollKey: Any,
) {
    val spacing = BouncerThemeV2.spacing
    val listState = rememberLazyListState()
    // A new tab or filter is a different list: start it at the top. Not on first composition.
    var firstKey by remember { mutableStateOf(true) }
    LaunchedEffect(scrollKey) {
        if (firstKey) firstKey = false else listState.scrollToItem(0)
    }
    val favourites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()
    val (featured, rows) = remember(matches, favourites) {
        featuredFor(
            matches,
            favourites.map { it.trim().lowercase() },
            System.currentTimeMillis(),
        )
    }
    val subscriptions by MatchSubscriptionManager.subscriptions.collectAsStateWithLifecycle()
    val alertPrefs by MatchSubscriptionManager.alertPrefs.collectAsStateWithLifecycle()
    val rowContext = RowContext(
        favouriteNames = favouriteNames,
        following = subscriptions + alertPrefs.filterValues { it.anyOn }.keys,
        onMatchClick = onMatchClick,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().bouncerScreenBackground(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        val state = screenStateOf(matches.isNotEmpty(), isLoading, failure, savedAtMs)
        when (state) {
            ScreenState.Loading -> item(key = "shimmer") {
                DelayedSkeleton(isLoading) {
                    Column {
                        repeat(SHIMMER_ROWS) {
                            // Mirror the real row's geometry so first paint does not jump.
                            ShimmerMatchCard(Modifier.padding(horizontal = spacing.lg, vertical = spacing.rowVertical))
                        }
                    }
                }
            }
            is ScreenState.Failed -> item(key = "load_error") { FailedState(state.kind, onRetry) }
            ScreenState.Empty -> homeEmptyItems(filtered, nextUp, onSeeUpcoming)
            else -> homeContentItems(state, featured, rows, byDay, rowContext, onRetry)
        }
    }
}

/** Home's loaded list: the stale banner, the hero card, then the match rows. */
private fun LazyListScope.homeContentItems(
    state: ScreenState,
    featured: Featured?,
    rows: List<CricketMatch>,
    byDay: DayOrder,
    rowContext: RowContext,
    onRetry: () -> Unit,
) {
    if (state is ScreenState.ContentWithBanner) {
        item(key = "stale", contentType = "stale") { StaleDataBanner(state.kind, state.savedAtMs, onRetry) }
    }
    featured?.let { hero ->
        val fm = hero.match
        item(key = "hero_${fm.id}", contentType = "hero") {
            HeroMatchCard(
                match = fm,
                kind = hero.kind,
                lastFetchedMs = { MatchCacheService.savedAtMs(listOf(MatchCacheService.LIVE)) },
                onClick = { rowContext.onMatchClick(fm) },
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (byDay == DayOrder.NONE) {
        matchRows(rows, "all", rowContext)
    } else {
        dayGroupedRows(rows, byDay == DayOrder.DESCENDING, rowContext)
    }
}

/** Home with nothing to list and no failure: a filter that emptied it, or no cricket on. */
private fun LazyListScope.homeEmptyItems(
    filtered: FilteredList?,
    nextUp: CricketMatch?,
    onSeeUpcoming: (() -> Unit)?,
) {
    when {
        // A filter that empties the list says which filter, and offers the way out. It used to
        // read "No matches found" with no action.
        filtered != null -> item(key = "empty_filtered") {
            EmptyStateV2(
                title = stringResource(R.string.home_empty_filtered_title, filtered.label),
                subtitle = stringResource(R.string.home_empty_filtered_subtitle),
                icon = Icons.Outlined.SportsCricket,
                onRetry = filtered.onShowAll,
                actionLabel = stringResource(R.string.home_show_all),
            )
        }
        // Nothing live is the usual state between matches, not a filter problem; the old copy said
        // "Try changing your filters" on an unfiltered Live tab.
        onSeeUpcoming != null -> item(key = "empty_live") {
            EmptyStateV2(
                title = stringResource(R.string.state_no_live_title),
                subtitle = nextUp?.let { nextUpCause(it) } ?: stringResource(R.string.home_empty_live_subtitle),
                icon = Icons.Outlined.SportsCricket,
                onRetry = onSeeUpcoming,
                actionLabel = stringResource(R.string.home_see_upcoming),
            )
        }
        else -> item(key = "empty") {
            EmptyStateV2(
                title = stringResource(R.string.home_empty_title),
                subtitle = stringResource(R.string.home_empty_subtitle),
                icon = Icons.Outlined.SportsCricket,
            )
        }
    }
}

/** "Next: India v Australia, Tomorrow 7:30 pm." for the dated [match]. */
@Composable
private fun nextUpCause(match: CricketMatch): String {
    val zone = ZoneId.systemDefault()
    val startMs = match.startDateMs ?: return stringResource(R.string.home_empty_live_subtitle)
    val day = dayLabel(Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate(), LocalDate.now(zone))
    val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(startMs))
    val teams = match.teams.joinToString(" v ") { it.name }
    return stringResource(R.string.state_no_live_cause, teams, "$day $time")
}

// ─── Helpers ──────────────────────────────────────────────

internal fun CricketMatch.hasFavouriteTeam(favouriteNames: Set<String>): Boolean =
    favouriteNames.isNotEmpty() && teams.any { it.name.trim().lowercase() in favouriteNames }

internal fun tabForStatus(vmStatus: String): String = when (vmStatus) {
    "Live" -> "Live"
    "Upcoming" -> "Upcoming"
    "Recent", "Completed" -> "Recent"
    else -> "All"
}

// The tab label IS the ViewModel status, "All" included -- see STATUS_ALL. This used to map
// "All" onto "Live", so the one filter that could not round-trip was also the one that looked
// broken: the highlight jumped to Live and the feed never changed.
private val STATUS_BY_TAB = linkedMapOf("All" to STATUS_ALL, "Live" to "Live", "Upcoming" to "Upcoming", "Recent" to "Recent")
private val STATUS_TABS = STATUS_BY_TAB.keys.toList()

/** Displayed text per tab key; the keys themselves stay as they are. */
private val STATUS_LABELS = mapOf(
    "All" to R.string.home_status_all,
    "Live" to R.string.tab_live,
    "Upcoming" to R.string.home_status_upcoming,
    "Recent" to R.string.home_status_recent,
)
private const val SHIMMER_ROWS = 3
private const val MAX_BADGE_COUNT = 9
