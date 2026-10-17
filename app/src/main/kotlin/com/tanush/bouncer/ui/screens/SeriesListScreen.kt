package com.tanush.bouncer.ui.screens

import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.Instant
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.ZoneId
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.utils.formatEpochDateRange
import com.tanush.bouncer.R
import com.tanush.bouncer.viewmodels.SeriesListViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.Series
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.ShimmerListItem
import com.tanush.bouncer.ui.components.v2.TextMenuV2
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private val SERIES_FILTER_OPTIONS = listOf("All", "International", "League", "Domestic", "Women")

/**
 * Screen showing a list of active cricket series/tournaments.
 */
@Composable
fun SeriesListScreen(
    onSeriesClick: (String) -> Unit,
    viewModel: SeriesListViewModel = viewModel(),
    // Null on the Series tab, which has no back destination.
    onBackClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors

    val allSeries by viewModel.series.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasError by viewModel.hasError.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    val filteredSeries = filterSeries(allSeries, selectedFilter)

    Scaffold(
        topBar = {
            BouncerTopBarV2(
                title = stringResource(R.string.series_series),
                showBack = onBackClick != null,
                onBack = onBackClick,
                actions = {
                    if (onSearchClick != null) {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.cd_search))
                        }
                    }
                },
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        SeriesModeBar(
            mode = mode,
            onModeSelected = viewModel::selectMode,
            years = viewModel.archiveYears,
            selectedYear = selectedYear,
            onYearSelected = viewModel::selectYear,
        )
        PullRefreshWrapperV2(
            isRefreshing = isLoading,
            onRefresh = viewModel::retry,
        ) {
            SeriesListBody(
                innerPadding = PaddingValues(),
                emptySubtitle = if (mode == SeriesListViewModel.Mode.PAST) {
                    stringResource(R.string.series_no_archived_series_for_year, selectedYear)
                } else {
                    stringResource(R.string.series_active_cricket_series_will_appear_here)
                },
                isLoading = isLoading,
                hasError = hasError,
                allSeries = allSeries,
                filteredSeries = filteredSeries,
                filterOptions = SERIES_FILTER_OPTIONS,
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                onSeriesClick = onSeriesClick,
                onRetry = { viewModel.retry() },
            )
        }
        }
    }
}

/** Current / Past, and — on Past — which year. */
@Composable
private fun SeriesModeBar(
    mode: SeriesListViewModel.Mode,
    onModeSelected: (SeriesListViewModel.Mode) -> Unit,
    years: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    val modes = SeriesListViewModel.Mode.entries
    TextTabBarV2(
        tabs = listOf(stringResource(R.string.series_current), stringResource(R.string.series_past)),
        selectedIndex = modes.indexOf(mode),
        onSelect = { onModeSelected(modes[it]) },
    ) {
        if (mode == SeriesListViewModel.Mode.PAST) {
            TextMenuV2(
                label = selectedYear.toString(),
                options = years,
                selected = selectedYear,
                optionLabel = { it.toString() },
                onSelect = onYearSelected,
            )
        }
    }
}

@Composable
private fun SeriesListBody(
    innerPadding: PaddingValues,
    emptySubtitle: String,
    isLoading: Boolean,
    hasError: Boolean,
    allSeries: List<Series>,
    filteredSeries: List<Series>,
    filterOptions: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    // Fade between skeleton/error/empty/content instead of hard-swapping.
    // Opacity-only, so it stays on under reduced motion (shorter spec).
    Crossfade(
        targetState = seriesListStateOf(isLoading, hasError, allSeries.isEmpty()),
        animationSpec = stateFadeSpec(),
        label = "seriesListState",
    ) { state ->
        when (state) {
        SeriesListState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                repeat(6) { ShimmerListItem() }
            }
        }
        SeriesListState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyStateV2(
                    title = stringResource(R.string.series_couldn_t_load_series),
                    icon = Icons.Outlined.CloudOff,
                    subtitle = stringResource(R.string.series_check_your_connection_and_try_again),
                    onRetry = onRetry,
                )
            }
        }
        SeriesListState.Empty -> {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyStateV2(
                    title = stringResource(R.string.series_no_series_found),
                    icon = Icons.Outlined.EmojiEvents,
                    subtitle = emptySubtitle,
                )
            }
        }
        SeriesListState.Content -> SeriesListContent(
            innerPadding = innerPadding,
            filteredSeries = filteredSeries,
            filterOptions = filterOptions,
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected,
            onSeriesClick = onSeriesClick,
        )
        }
    }
}

@Composable
private fun SeriesListContent(
    innerPadding: PaddingValues,
    filteredSeries: List<Series>,
    filterOptions: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(vertical = spacing.sm),
    ) {
        item(key = "filters") {
            // Text tabs, as Home filters: a chip row here read as a different app.
            TextTabBarV2(
                tabs = filterOptions,
                selectedIndex = filterOptions.indexOf(selectedFilter).coerceAtLeast(0),
                onSelect = { onFilterSelected(filterOptions[it]) },
            )
            Spacer(Modifier.height(spacing.sm))
        }

        items(filteredSeries, key = { it.id }) { series ->
            SeriesCard(series = series, onClick = { onSeriesClick(series.id) })
        }

        item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
    }
}

private enum class SeriesListState { Loading, Error, Empty, Content }

private fun seriesListStateOf(isLoading: Boolean, hasError: Boolean, isEmpty: Boolean): SeriesListState = when {
    isLoading -> SeriesListState.Loading
    hasError -> SeriesListState.Error
    isEmpty -> SeriesListState.Empty
    else -> SeriesListState.Content
}

private fun stateFadeSpec(): FiniteAnimationSpec<Float> =
    if (BouncerMotion.reducedMotionEnabled) BouncerMotion.Specs.ReducedFadeIn else BouncerMotion.Specs.FadeIn

internal fun filterSeries(allSeries: List<Series>, filter: String): List<Series> = when (filter) {
    "International" -> allSeries.filter { it.category == Series.SeriesCategory.INTERNATIONAL }
    "League" -> allSeries.filter { it.category == Series.SeriesCategory.LEAGUE }
    "Domestic" -> allSeries.filter { it.category == Series.SeriesCategory.DOMESTIC }
    // Independent of category, so a women's T20 league appears under both "League" and "Women".
    "Women" -> allSeries.filter { it.isWomens }
    else -> allSeries
}

/**
 * Card subtitle. Category and women's cricket are separate axes, so a women's T20 league reads
 * "League · Women" rather than having to pick one.
 */
internal fun seriesLabelOf(series: Series): String {
    val category = when (series.category) {
        Series.SeriesCategory.INTERNATIONAL -> "International"
        Series.SeriesCategory.LEAGUE -> "League"
        Series.SeriesCategory.DOMESTIC -> "Domestic"
    }
    return if (series.isWomens) "$category · Women" else category
}

/**
 * "7 Aug – 20 Sep 2026" from the series' start and end.
 *
 * Cricbuzz sends both as epoch-millisecond strings, which the card used to print verbatim. A value
 * that is not a number is shown as it came, and a missing end shows the start alone.
 */
internal fun seriesDatesOf(series: Series, zone: ZoneId = ZoneId.systemDefault()): String =
    formatEpochDateRange(series.startDate, series.endDate, zone)

