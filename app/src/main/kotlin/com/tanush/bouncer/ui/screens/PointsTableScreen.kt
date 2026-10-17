package com.tanush.bouncer.ui.screens

import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.viewmodels.PointsTableViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.PointsTableEntry
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.ShimmerTableRows
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * A series' standings: the series name as the title, the table full-bleed beneath it.
 */
@Composable
fun PointsTableScreen(
    seriesId: String,
    seriesName: String = "Tournament Standings",
    onBackClick: () -> Unit,
    viewModel: PointsTableViewModel = viewModel(),
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing

    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }

    Scaffold(
        topBar = {
            // The series is the title — it was "Points table" over a card that named the series.
            BouncerTopBarV2(
                title = seriesName.ifBlank { stringResource(R.string.points_points_table) },
                showBack = true,
                onBack = onBackClick,
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        val screenState = when {
            isLoading -> PointsTableState.Loading
            error != null -> PointsTableState.Error
            else -> PointsTableState.Content
        }
        PullRefreshWrapperV2(
            isRefreshing = isLoading,
            onRefresh = viewModel::retry,
        ) {
        // Fade between skeleton/error/content instead of hard-swapping.
        Crossfade(
            targetState = screenState,
            animationSpec = if (BouncerMotion.reducedMotionEnabled) {
                BouncerMotion.Specs.ReducedFadeIn
            } else {
                BouncerMotion.Specs.FadeIn
            },
            label = "pointsTableState",
        ) { state ->
            when (state) {
                PointsTableState.Loading -> {
                    ShimmerTableRows(modifier = Modifier.fillMaxSize().padding(innerPadding))
                }
                PointsTableState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        EmptyStateV2(
                            icon = Icons.Outlined.TableChart,
                            title = stringResource(R.string.points_no_standings_available),
                            subtitle = error ?: "",
                            onRetry = viewModel::retry,
                        )
                    }
                }
                PointsTableState.Content -> StandingsList(
                    innerPadding = innerPadding,
                    entries = entries,
                )
            }
        }
        }
    }
}

@Composable
private fun StandingsList(
    innerPadding: PaddingValues,
    entries: List<PointsTableEntry>,
) {
    val cutoff = qualificationCutoff(entries)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(vertical = BouncerThemeV2.spacing.sm),
    ) {
        item(key = "header") { StandingsHeader() }
        itemsIndexed(entries, key = { index, e -> "row_${index}_${e.teamName}" }) { index, entry ->
            StandingsRow(entry = entry, rank = entry.position.takeIf { it > 0 } ?: (index + 1), cutoffBelow = index == cutoff)
        }
        if (cutoff >= 0) item(key = "note") { QualificationNote() }
        item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
    }
}

private enum class PointsTableState { Loading, Error, Content }

