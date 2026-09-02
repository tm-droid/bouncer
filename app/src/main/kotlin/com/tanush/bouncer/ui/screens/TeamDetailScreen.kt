package com.tanush.bouncer.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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

    val allMatches by liveActivityViewModel.currentMatches.collectAsStateWithLifecycle()
    val isLoading by liveActivityViewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by liveActivityViewModel.isRefreshing.collectAsStateWithLifecycle()
    val teamMatches = remember(allMatches, teamName) {
        allMatches.filter { match ->
            match.teams.any { it.name.equals(teamName, ignoreCase = true) }
        }
    }
    val acronym = remember(teamName) {
        teamName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    }
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
            TeamHeaderRow(logoUrl = logoUrl, teamName = teamName, acronym = acronym)
            HorizontalDivider(color = colors.divider)
        }

        if (isLoading && teamMatches.isEmpty()) {
            items(3, key = { "shimmer_$it" }) {
                ShimmerMatchCard(
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                )
            }
        } else if (teamMatches.isEmpty()) {
            item(key = "no_matches") {
                EmptyStateV2(
                    icon = Icons.Outlined.SportsCricket,
                    title = stringResource(R.string.teams_no_current_matches),
                    subtitle = "Live, upcoming and recent $teamName matches will appear here",
                )
            }
        } else {
            item(key = "matches_header") {
                SectionHeaderV2(title = "Matches (${teamMatches.size})")
            }
            items(teamMatches, key = { "m_${it.id}" }) { match ->
                MatchCardV2(
                    match = match,
                    onClick = { onMatchClick(match) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TeamHeaderRow(
    logoUrl: String?,
    teamName: String,
    acronym: String,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = teamName,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    acronym.take(2),
                    style = typography.titleMedium,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(
                teamName,
                style = typography.titleLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                acronym,
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
