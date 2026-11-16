package com.tanush.bouncer.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.models.PointsTableEntry
import com.tanush.bouncer.ui.components.v2.DelayedSkeleton
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FailedState
import com.tanush.bouncer.ui.components.v2.FailureKind
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.ShimmerTableRows
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.PointsTableViewModel

/**
 * A series' standings, as the Points table tab of its series page. It used to be a screen of its
 * own that the tab only linked to — the tab looked like content and was a link — titled
 * "Tournament Standings" for every series, because the series name was never passed to it.
 *
 * A row opens its team.
 */
@Composable
fun PointsTableTab(
    seriesId: String,
    onTeamClick: (String) -> Unit,
    viewModel: PointsTableViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val failureKind by viewModel.failureKind.collectAsStateWithLifecycle()

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }

    val screenState = when {
        isLoading -> PointsTableState.Loading
        error != null && entries.isEmpty() -> PointsTableState.Error
        // Fetched fine, but the series has no table (knockouts, bilateral tours): not a failure.
        entries.isEmpty() -> PointsTableState.Empty
        else -> PointsTableState.Content
    }
    PullRefreshWrapperV2(isRefreshing = isLoading, onRefresh = viewModel::retry) {
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
            PointsTableBody(state, entries, failureKind, viewModel::retry, onTeamClick)
        }
    }
}

@Composable
private fun PointsTableBody(
    state: PointsTableState,
    entries: List<PointsTableEntry>,
    failureKind: FailureKind,
    onRetry: () -> Unit,
    onTeamClick: (String) -> Unit,
) {
    when (state) {
        PointsTableState.Loading -> DelayedSkeleton(isLoading = true) {
            ShimmerTableRows(modifier = Modifier.fillMaxSize())
        }
        PointsTableState.Error -> FailedState(failureKind, onRetry, Modifier.fillMaxSize())
        PointsTableState.Empty -> EmptyStateV2(
            icon = Icons.Outlined.TableChart,
            title = stringResource(R.string.points_no_standings_available),
            subtitle = stringResource(R.string.points_no_table_for_series),
            modifier = Modifier.fillMaxSize(),
        )
        PointsTableState.Content -> StandingsList(entries = entries, onTeamClick = onTeamClick)
    }
}

@Composable
private fun StandingsList(entries: List<PointsTableEntry>, onTeamClick: (String) -> Unit) {
    val cutoff = qualificationCutoff(entries)
    // Tied and no-result columns only where a side has one: most tables would carry two of zeros.
    val showDrawn = entries.any { it.drawOrNR > 0 }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = BouncerThemeV2.spacing.sm),
    ) {
        item(key = "header") { StandingsHeader(showDrawn) }
        itemsIndexed(entries, key = { index, e -> "row_${index}_${e.teamName}" }) { index, entry ->
            StandingsRow(
                entry = entry,
                rank = entry.position.takeIf { it > 0 } ?: (index + 1),
                cutoffBelow = index == cutoff,
                showDrawn = showDrawn,
                onClick = { onTeamClick(entry.teamName) },
            )
        }
        if (cutoff >= 0) item(key = "note") { QualificationNote() }
        item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
    }
}

private enum class PointsTableState { Loading, Error, Empty, Content }
