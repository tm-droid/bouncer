package com.tanush.bouncer.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import java.time.ZoneId
import java.time.LocalDate
import java.time.Instant
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.network.CricBuzzUrls
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.SeriesMatchDay
import com.tanush.bouncer.models.SeriesStats
import com.tanush.bouncer.models.SeriesStatsTable
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.DelayedSkeleton
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FailedState
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.MatchStatusType
import com.tanush.bouncer.ui.components.v2.determineMatchStatus
import com.tanush.bouncer.ui.components.v2.ShimmerListItem
import com.tanush.bouncer.ui.components.v2.TextMenuV2
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.openLink
import com.tanush.bouncer.utils.shareText
import com.tanush.bouncer.viewmodels.SeriesDetailViewModel
import com.tanush.bouncer.viewmodels.SeriesTabState

private const val TAB_SCHEDULE = 0
private const val TAB_POINTS = 1
private const val TAB_STATS = 2

/**
 * A series: its schedule and results, its points table, and its player stats.
 *
 * Schedule and Stats used to be hardcoded "coming soon" placeholders on every series, with nothing
 * behind them; both now read the series' own Cricbuzz pages through [SeriesDetailViewModel].
 */
@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onMatchClick: (CricketMatch) -> Unit,
    onPlayerClick: (name: String, cricbuzzId: String?) -> Unit,
    onTeamClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: SeriesDetailViewModel = viewModel(),
) {
    val colors = BouncerThemeV2.colors
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_SCHEDULE) }

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    LaunchedEffect(selectedTab) { if (selectedTab == TAB_STATS) viewModel.ensureStats() }

    val title = (schedule as? SeriesTabState.Loaded)?.data
        ?.firstNotNullOfOrNull { day -> day.matches.firstOrNull()?.seriesName?.takeIf { it.isNotBlank() } }
        ?: stringResource(R.string.series_series_detail)

    Scaffold(
        topBar = {
            BouncerTopBarV2(title = title, showBack = true, onBack = onBackClick) {
                val context = LocalContext.current
                val url = "${CricBuzzUrls.BASE}/cricket-series/$seriesId"
                IconButton(onClick = {
                    context.shareText("$title\n$url", subject = title)
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.common_share))
                }
                IconButton(onClick = { context.openLink(url) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.news_open_original),
                    )
                }
            }
        },
        containerColor = colors.background,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TextTabBarV2(
                tabs = listOf(
                    stringResource(R.string.series_schedule),
                    stringResource(R.string.series_points_table),
                    stringResource(R.string.series_stats),
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            when (selectedTab) {
                TAB_SCHEDULE -> ScheduleTab(schedule, onMatchClick, viewModel::retrySchedule)
                TAB_POINTS -> PointsTableTab(seriesId = seriesId, onTeamClick = onTeamClick)
                TAB_STATS -> StatsTab(viewModel, onPlayerClick)
            }
        }
    }
}

@Composable
private fun ScheduleTab(
    state: SeriesTabState<List<SeriesMatchDay>>,
    onMatchClick: (CricketMatch) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        SeriesTabState.Loading -> LoadingRows()
        is SeriesTabState.Error -> FailedState(state.kind, onRetry, Modifier.fillMaxSize())
        SeriesTabState.Empty -> CenteredEmpty(
            icon = Icons.Outlined.CalendarMonth,
            title = stringResource(R.string.state_series_empty_title),
            subtitle = stringResource(R.string.state_series_empty_cause),
        )
        is SeriesTabState.Loaded -> ScheduleList(state.data, onMatchClick)
    }
}

@Composable
private fun ScheduleList(days: List<SeriesMatchDay>, onMatchClick: (CricketMatch) -> Unit) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    // Opens on the day of the next match, results above it a scroll away. Computed once per
    // schedule, so a rotation or a return from a match keeps wherever the reader scrolled to.
    val listState = rememberSaveable(days, saver = LazyListState.Saver) {
        LazyListState(firstVisibleItemIndex = scheduleAnchorIndex(days, System.currentTimeMillis()))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        days.forEachIndexed { dayIndex, day ->
            // Keys carry the index: the heading is scraped text, and two groups can share one.
            item(key = "day_${dayIndex}_${day.label}") {
                // "Today" / "Sat 18 Oct", as All Matches heads its days, from the first match's
                // start; Cricbuzz's own label when the day has no timed match.
                val first = day.matches.firstNotNullOfOrNull { it.startDateMs }
                val label = first?.let {
                    val zone = ZoneId.systemDefault()
                    dayLabel(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), LocalDate.now(zone))
                } ?: day.label
                SectionHeaderV2(title = pluralStringResource(R.plurals.day_match_count, day.matches.size, label, day.matches.size))
            }
            itemsIndexed(
                day.matches,
                key = { i, match -> "match_${dayIndex}_${i}_${match.id}" },
            ) { _, match ->
                MatchCardV2(match = match, onClick = { onMatchClick(match) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The lazy-list index of the date heading over the series' next match: the first one live, or yet
 * to be played. Each day is one heading item followed by one item per match. A finished series has
 * no next match and opens at its start, 0.
 *
 * A match the status classifier cannot place as finished — an abandoned fixture with no score
 * reads as upcoming — is only taken as "next" if it has not started more than a day ago, or every
 * series with one washed-out game early on would open on that game.
 */
internal fun scheduleAnchorIndex(days: List<SeriesMatchDay>, nowMs: Long): Int {
    var index = 0
    for (day in days) {
        val isNext = day.matches.any { match ->
            when (determineMatchStatus(match)) {
                MatchStatusType.Live -> true
                MatchStatusType.Completed -> false
                else -> (match.startDateMs ?: Long.MAX_VALUE) >= nowMs - STALE_FIXTURE_MS
            }
        }
        if (isNext) return index
        index += 1 + day.matches.size
    }
    return 0
}

private const val STALE_FIXTURE_MS = 24 * 60 * 60 * 1000L

@Composable
private fun StatsTab(viewModel: SeriesDetailViewModel, onPlayerClick: (name: String, cricbuzzId: String?) -> Unit) {
    val state by viewModel.stats.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedStatType.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()

    when (val s = state) {
        SeriesTabState.Loading -> LoadingRows()
        is SeriesTabState.Error -> FailedState(s.kind, viewModel::retryStats, Modifier.fillMaxSize())
        SeriesTabState.Empty -> CenteredEmpty(
            icon = Icons.Outlined.BarChart,
            title = stringResource(R.string.series_no_stats_yet),
            subtitle = stringResource(R.string.series_stats_appear_once_played),
        )
        is SeriesTabState.Loaded -> StatsContent(
            stats = s.data,
            selectedType = selectedType ?: s.data.selectedType,
            selectedFormat = selectedFormat,
            onTypeSelected = viewModel::selectStatType,
            onFormatSelected = viewModel::selectFormat,
            onPlayerClick = onPlayerClick,
        )
    }
}

@Composable
private fun StatsContent(
    stats: SeriesStats,
    selectedType: String,
    selectedFormat: String?,
    onTypeSelected: (String) -> Unit,
    onFormatSelected: (String) -> Unit,
    onPlayerClick: (name: String, cricbuzzId: String?) -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    val table = stats.tables.firstOrNull { it.format == selectedFormat } ?: stats.tables.firstOrNull()
    val currentType = stats.types.firstOrNull { it.value == selectedType }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stats.types.isNotEmpty()) {
                TextMenuV2(
                    label = currentType?.header ?: selectedType,
                    options = stats.types,
                    selected = currentType,
                    optionLabel = { it.header },
                    onSelect = { onTypeSelected(it.value) },
                    sectionOf = { it.category.ifBlank { null } },
                )
            }
            Spacer(Modifier.weight(1f))
            // Only a multi-format series has a choice to offer.
            if (stats.tables.size > 1) {
                TextMenuV2(
                    label = table?.format.orEmpty(),
                    options = stats.tables.map { it.format },
                    selected = table?.format,
                    optionLabel = { it },
                    onSelect = onFormatSelected,
                )
            }
        }
        HorizontalDivider(color = BouncerThemeV2.colors.divider)
        if (table == null || table.rows.isEmpty()) {
            CenteredEmpty(
                icon = Icons.Outlined.BarChart,
                title = currentType?.header ?: stringResource(R.string.series_no_stats_yet),
                subtitle = stringResource(R.string.series_no_players_for_stat),
            )
        } else {
            StatsTable(table, selectedType, onPlayerClick)
        }
    }
}


/**
 * Name column fixed, figures scrolling sideways together: a Most Wickets table is nine figures wide,
 * which does not fit a phone. Every row shares one [ScrollState], so the columns stay aligned.
 */
@Composable
private fun StatsTable(
    table: SeriesStatsTable,
    statType: String,
    onPlayerClick: (name: String, cricbuzzId: String?) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val listState = rememberSaveable(statType, table.format, saver = LazyListState.Saver) { LazyListState() }
    val figureHeaders = table.headers.drop(1)
    // Descending on the first tap of a column, ascending on the second; a new stat starts unsorted.
    var sortColumn by rememberSaveable(statType, table.format) { mutableIntStateOf(NO_SORT) }
    var descending by rememberSaveable(statType, table.format) { mutableStateOf(true) }
    val rows = remember(table.rows, sortColumn, descending) { sortedStatRows(table.rows, sortColumn, descending) }
    val keys = remember(rows) { statRowKeys(rows) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item(key = "header") {
            StatsHeaderRow(
                name = table.headers.firstOrNull().orEmpty(),
                cells = figureHeaders,
                scroll = scroll,
                sortColumn = sortColumn,
                descending = descending,
                onSort = { column ->
                    if (column == sortColumn) {
                        descending = !descending
                    } else {
                        sortColumn = column
                        descending = true
                    }
                },
            )
            HorizontalDivider(color = colors.divider)
        }
        itemsIndexed(rows, key = { i, _ -> keys[i] }) { i, row ->
            StatsRow(
                rank = i + 1,
                name = row.playerName,
                cells = row.cells,
                scroll = scroll,
                // The row's Cricbuzz id goes with the name, so two players sharing a name open the right one.
                modifier = Modifier.clickable { onPlayerClick(row.playerName, row.playerId.ifBlank { null }) },
            )
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = spacing.lg))
        }
    }
}

@Composable
private fun LoadingRows() {
    val spacing = BouncerThemeV2.spacing
    DelayedSkeleton(isLoading = true) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            repeat(6) { ShimmerListItem() }
        }
    }
}

@Composable
private fun CenteredEmpty(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateV2(icon = icon, title = title, subtitle = subtitle)
    }
}
