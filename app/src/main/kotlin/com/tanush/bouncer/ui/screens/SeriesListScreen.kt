package com.tanush.bouncer.ui.screens

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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.ShimmerListItem
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Screen showing a list of active cricket series/tournaments.
 */
@Composable
fun SeriesListScreen(
    onSeriesClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: SeriesListViewModel = viewModel(),
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val allSeries by viewModel.series.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasError by viewModel.hasError.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredSeries = filterSeries(allSeries, selectedFilter)

    val filterOptions = listOf("All", "International", "League", "Domestic", "Women")

    Scaffold(
        topBar = {
            BouncerTopBarV2(title = "Series", showBack = true, onBack = onBackClick)
        },
        containerColor = colors.background,
    ) { innerPadding ->
        SeriesListBody(
            innerPadding = innerPadding,
            isLoading = isLoading,
            hasError = hasError,
            allSeries = allSeries,
            filteredSeries = filteredSeries,
            filterOptions = filterOptions,
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            onSeriesClick = onSeriesClick,
            onRetry = { viewModel.retry() },
        )
    }
}

@Composable
private fun SeriesListBody(
    innerPadding: PaddingValues,
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
    when {
        isLoading -> {
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
        hasError -> {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyStateV2(
                    title = "Couldn't load series",
                    subtitle = "Check your connection and try again",
                    onRetry = onRetry,
                )
            }
        }
        allSeries.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyStateV2(
                    title = "No series found",
                    subtitle = "Active cricket series will appear here",
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = spacing.sm),
            ) {
                item(key = "filters") {
                    FilterChipRowV2(
                        items = filterOptions,
                        selectedItem = selectedFilter,
                        onItemSelected = onFilterSelected,
                    )
                    Spacer(Modifier.height(spacing.sm))
                }

                items(filteredSeries, key = { it.id }) { series ->
                    SeriesCard(series = series, onClick = { onSeriesClick(series.id) })
                }

                item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

private fun filterSeries(allSeries: List<Series>, filter: String): List<Series> = when (filter) {
    "International" -> allSeries.filter { it.category == Series.SeriesCategory.INTERNATIONAL }
    "League" -> allSeries.filter { it.category == Series.SeriesCategory.LEAGUE }
    "Domestic" -> allSeries.filter { it.category == Series.SeriesCategory.DOMESTIC }
    "Women" -> allSeries.filter { it.category == Series.SeriesCategory.WOMEN }
    else -> allSeries
}

@Composable
private fun SeriesCard(series: Series, onClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = BouncerThemeV2.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Series icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.name,
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (series.startDate.isNotEmpty()) {
                    Text(
                        series.startDate,
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                val categoryLabel = when (series.category) {
                    Series.SeriesCategory.INTERNATIONAL -> "International"
                    Series.SeriesCategory.LEAGUE -> "League"
                    Series.SeriesCategory.DOMESTIC -> "Domestic"
                    Series.SeriesCategory.WOMEN -> "Women"
                }
                Text(
                    categoryLabel,
                    style = typography.labelSmall,
                    color = colors.primary,
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
