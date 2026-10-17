package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.rememberTeamColors
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.components.v2.FormStrip
import com.tanush.bouncer.managers.TeamAcronymManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.ShimmerMatchCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

private const val MAX_RESULTS = 10
private const val FORM_LENGTH = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    teamName: String,
    liveActivityViewModel: LiveActivityViewModel = viewModel(),
    onMatchClick: (CricketMatch) -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography

    // Every cached status, not Home's filtered list: a team page left on the Live filter showed
    // no results at all.
    val allMatches by liveActivityViewModel.allMatches.collectAsStateWithLifecycle()
    val isLoading by liveActivityViewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by liveActivityViewModel.isRefreshing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { liveActivityViewModel.warmAllStatuses() }
    val teamMatches = remember(allMatches, teamName) {
        allMatches.filter { match ->
            !match.isCompleted && match.teams.any { it.name.equals(teamName, ignoreCase = true) }
        }.distinctBy { it.id }.sortedBy { it.startDateMs ?: Long.MAX_VALUE }
    }
    val results = remember(allMatches, teamName) { resultsFor(allMatches, teamName).take(MAX_RESULTS) }
    // The curated code ("IND"), not the name's initials, which made "India" "I".
    val acronym = remember(teamName) { TeamAcronymManager.getAcronym(teamName) }
    val logoUrl = remember(teamName) { TeamLogoManager.getLogoUrl(teamName) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(teamName, style = typography.titleMedium, color = colors.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBar,
                    titleContentColor = colors.onTopBar,
                    navigationIconContentColor = colors.onTopBarMuted,
                    actionIconContentColor = colors.onTopBarMuted,
                ),
            )
        },
    ) { innerPadding ->
        PullRefreshWrapperV2(
            isRefreshing = isRefreshing,
            onRefresh = liveActivityViewModel::refreshMatches,
            modifier = Modifier.padding(innerPadding),
        ) {
            TeamDetailContent(
                teamMatches = teamMatches,
                results = results,
                isLoading = isLoading,
                teamName = teamName,
                logoUrl = logoUrl,
                acronym = acronym,
                onMatchClick = onMatchClick,
            )
        }
    }
}

@Composable
private fun TeamDetailContent(
    teamMatches: List<CricketMatch>,
    results: List<CricketMatch>,
    isLoading: Boolean,
    teamName: String,
    logoUrl: String?,
    acronym: String,
    onMatchClick: (CricketMatch) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = spacing.md),
    ) {
        item(key = "team_header") {
            val form = results.mapNotNull { formResult(it, teamName) }.take(FORM_LENGTH)
            TeamHeaderRow(logoUrl = logoUrl, teamName = teamName, acronym = acronym, form = form)
            TeamBand(teamName)
        }

        if (isLoading && teamMatches.isEmpty() && results.isEmpty()) {
            items(3, key = { "shimmer_$it" }) {
                ShimmerMatchCard(
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                )
            }
        } else if (teamMatches.isEmpty() && results.isEmpty()) {
            item(key = "no_matches") {
                EmptyStateV2(
                    icon = Icons.Outlined.SportsCricket,
                    title = stringResource(R.string.teams_no_current_matches),
                    subtitle = stringResource(R.string.teams_matches_will_appear, teamName),
                )
            }
        } else {
            teamMatchSections(teamMatches, results, onMatchClick)
        }

        item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
    }
}

/** "Live and upcoming", then "Results" headed by the side's last five results as letters. */
private fun LazyListScope.teamMatchSections(
    teamMatches: List<CricketMatch>,
    results: List<CricketMatch>,
    onMatchClick: (CricketMatch) -> Unit,
) {
    if (teamMatches.isNotEmpty()) {
        item(key = "matches_header") { SectionHeaderV2(title = stringResource(R.string.teams_live_and_upcoming)) }
        items(teamMatches, key = { "m_${it.id}" }) { match ->
            MatchCardV2(match = match, onClick = { onMatchClick(match) }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (results.isEmpty()) return
    // The form guide is drawn under the team's name in the header now, not spelled out here.
    item(key = "results_header") { SectionHeaderV2(title = stringResource(R.string.teams_results)) }
    items(results, key = { "r_${it.id}" }) { match ->
        MatchCardV2(match = match, onClick = { onMatchClick(match) }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The team: crest on a plate with a ring in its colour, its name in that colour, the code, and the
 * last five results as squares. The crest was circle-cropped, which cut the edges off shield crests
 * and flags.
 */
@Composable
private fun TeamHeaderRow(
    logoUrl: String?,
    teamName: String,
    acronym: String,
    form: List<FormResult>,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(teamName = teamName, logoUrl = logoUrl, size = 64.dp, ring = true, acronym = acronym)
        Spacer(Modifier.width(spacing.lg))
        Column {
            Text(
                teamName,
                style = typography.headlineMedium,
                color = rememberTeamInk(teamName),
                fontWeight = FontWeight.Bold,
            )
            if (!acronym.equals(teamName, ignoreCase = true)) {
                Text(acronym, style = typography.labelLarge, color = colors.textSecondary)
            }
            if (form.isNotEmpty()) {
                Spacer(Modifier.height(spacing.sm))
                FormStrip(results = form)
            }
        }
    }
}

/** A band of the team's colour under the header, for a side the registry knows; a hairline otherwise. */
@Composable
private fun TeamBand(teamName: String) {
    if (TeamColorRegistry.isKnown(teamName)) {
        val teamColors = rememberTeamColors(teamName)
        Row(Modifier.fillMaxWidth().height(4.dp)) {
            Box(Modifier.weight(2f).fillMaxHeight().background(teamColors.primary))
            Box(Modifier.weight(1f).fillMaxHeight().background(teamColors.secondary))
        }
    } else {
        HorizontalDivider(color = BouncerThemeV2.colors.divider)
    }
}
