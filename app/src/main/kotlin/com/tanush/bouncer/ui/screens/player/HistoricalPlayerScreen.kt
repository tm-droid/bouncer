package com.tanush.bouncer.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.data.history.HistoryCoverage
import com.tanush.bouncer.data.history.PlayerCareer
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.DelayedSkeleton
import com.tanush.bouncer.ui.components.v2.FailedState
import com.tanush.bouncer.ui.components.v2.FailureKind
import com.tanush.bouncer.ui.components.v2.HistoryCoverageNotice
import com.tanush.bouncer.ui.components.v2.PastMatchRow
import com.tanush.bouncer.ui.components.v2.ScreenState
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.ShimmerPlayerHeader
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.components.v2.screenStateOf
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.HistoricalPlayerState
import com.tanush.bouncer.viewmodels.HistoricalPlayerViewModel

// Usual order first; any other format Cricsheet has (a league's own) follows.
private val FORMAT_ORDER = listOf("Test", "ODI", "T20I", "T20")

/**
 * A player's Cricsheet career: the figures per format, then the latest matches. It reuses the
 * Cricbuzz profile's header, format tabs and stat rows, minus the boundary and five-wicket rows
 * Cricsheet cannot supply. [displayName] is the full name from the link that led here; the record
 * itself only has "V Kohli".
 */
@Composable
fun HistoricalPlayerScreen(
    playerId: String,
    displayName: String?,
    onBackClick: () -> Unit,
    onMatchClick: (CricketMatch) -> Unit,
) {
    val viewModel: HistoricalPlayerViewModel = viewModel(
        key = HistoricalPlayerViewModel.storeKey(playerId),
        factory = HistoricalPlayerViewModel.factory(playerId),
    )
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { BouncerTopBarV2(title = stringResource(R.string.player_player), showBack = true, onBack = onBackClick) },
        containerColor = BouncerThemeV2.colors.background,
    ) { padding ->
        val loaded = state as? HistoricalPlayerState.Loaded
        val screen = screenStateOf(
            hasContent = loaded != null,
            isLoading = state is HistoricalPlayerState.Loading,
            failure = FailureKind.Server.takeIf { state is HistoricalPlayerState.Failed },
        )
        when {
            loaded != null -> CareerList(padding, loaded, displayName, onMatchClick)
            screen is ScreenState.Failed -> FailedState(screen.kind, viewModel::retry, Modifier.padding(padding))
            else -> DelayedSkeleton(isLoading = true) {
                Column(Modifier.fillMaxSize().padding(padding).padding(BouncerThemeV2.spacing.lg)) { ShimmerPlayerHeader() }
            }
        }
    }
}

@Composable
private fun CareerList(
    padding: PaddingValues,
    loaded: HistoricalPlayerState.Loaded,
    displayName: String?,
    onMatchClick: (CricketMatch) -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    val career = loaded.career
    val formats = remember(career) { orderedFormats(career) }
    var selected by rememberSaveable(career.name) { mutableStateOf(formats.firstOrNull().orEmpty()) }
    val notice = HistoryCoverage.missingAfghanistan(career.teams.map { it.first }, stringResource(R.string.history_missing_afghanistan))
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = spacing.xl)) {
        item(key = "header") {
            Column(Modifier.padding(spacing.lg)) {
                PlayerHeader(
                    name = displayName?.takeIf { it.isNotBlank() } ?: career.name,
                    country = career.teams.firstOrNull()?.first.orEmpty(), role = "", imageUrl = null,
                    isCaptain = false, isKeeper = false,
                )
                Text(
                    stringResource(R.string.history_career_caption),
                    style = BouncerThemeV2.typography.bodySmall,
                    color = BouncerThemeV2.colors.textSecondary,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
        }
        notice?.let { item(key = "coverage_notice") { HistoryCoverageNotice(it) } }
        if (formats.size > 1) {
            item(key = "format_selector") {
                TextTabBarV2(
                    tabs = formats,
                    selectedIndex = formats.indexOf(selected).coerceAtLeast(0),
                    onSelect = { selected = formats[it] },
                )
            }
        }
        formatStatItems(career.formats[selected], selected, hasBoundaries = career.hasBoundaries)
        if (loaded.recent.isNotEmpty()) {
            item(key = "recent_header") { SectionHeaderV2(title = stringResource(R.string.history_career_recent)) }
            items(loaded.recent, key = { "match_${it.id}" }) { row ->
                PastMatchRow(row = row, onClick = { onMatchClick(row.toCricketMatch()) })
            }
        }
    }
}

private fun orderedFormats(career: PlayerCareer): List<String> =
    FORMAT_ORDER.filter { it in career.formats } + career.formats.keys.filter { it !in FORMAT_ORDER }.sorted()
