package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.SquadScraper
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import io.sentry.Sentry

data class Player(
    val name: String,
    val role: PlayerRole,
    val isCaptain: Boolean = false,
    val isWicketKeeper: Boolean = false,
    val imageUrl: String? = null
)

enum class PlayerRole(val displayName: String) {
    BATSMAN("Batsman"),
    BOWLER("Bowler"),
    ALL_ROUNDER("All-Rounder"),
    WICKET_KEEPER("Wicket-Keeper")
}

private fun roleFromString(role: String): PlayerRole {
    try {
        val lowerRole = role.lowercase()
        return when {
            lowerRole.contains("bowl") || lowerRole.contains("fast") || lowerRole.contains("spin") || lowerRole.contains("medium") -> PlayerRole.BOWLER
            lowerRole.contains("all") || lowerRole.contains("ar") -> PlayerRole.ALL_ROUNDER
            lowerRole.contains("keeper") || lowerRole.contains("wk") -> PlayerRole.WICKET_KEEPER
            else -> PlayerRole.BATSMAN
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

private fun SquadScraper.Player.toScreenPlayer(): Player {
    try {
        return Player(
            name = this.name,
            role = if (this.isWicketKeeper) PlayerRole.WICKET_KEEPER else roleFromString(this.role),
            isCaptain = this.isCaptain,
            isWicketKeeper = this.isWicketKeeper,
            imageUrl = this.imageUrl.takeIf { it.isNotEmpty() }
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SquadScreen(
    match: CricketMatch,
    onBackClick: () -> Unit,
    onPlayerClick: ((Player) -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    var selectedTeamIndex by remember { mutableIntStateOf(0) }

    var team1Squad by remember { mutableStateOf<List<Player>>(emptyList()) }
    var team2Squad by remember { mutableStateOf<List<Player>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(match.id) {
        isLoading = true
        errorMessage = null
        try {
            val (scraped1, scraped2) = SquadScraper.fetchSquads(match.id)
            if (scraped1.isEmpty() && scraped2.isEmpty()) {
                team1Squad = generatePlaceholderSquad(team1?.name ?: "Team 1")
                team2Squad = generatePlaceholderSquad(team2?.name ?: "Team 2")
                errorMessage = "Squads not yet announced"
            } else {
                team1Squad = scraped1.map { it.toScreenPlayer() }
                team2Squad = scraped2.map { it.toScreenPlayer() }
            }
        } catch (e: Exception) {
            team1Squad = generatePlaceholderSquad(team1?.name ?: "Team 1")
            team2Squad = generatePlaceholderSquad(team2?.name ?: "Team 2")
            errorMessage = "Could not load squads"
        }
        isLoading = false
    }

    val currentSquad = if (selectedTeamIndex == 0) team1Squad else team2Squad

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Squads",
                        style = typography.headingMedium,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = dimens.screenHorizontal,
                    end = dimens.screenHorizontal,
                    top = dimens.sm,
                    bottom = dimens.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.md)
            ) {
                item {
                    TeamSelectorCard(
                        team1Name = team1?.name ?: "Team 1",
                        team2Name = team2?.name ?: "Team 2",
                        selectedIndex = selectedTeamIndex,
                        onTeamSelected = { selectedTeamIndex = it }
                    )
                }

                if (!isLoading && errorMessage != null) {
                    item {
                        StatusCard(message = errorMessage ?: "")
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = hierarchy.secondary.primary,
                            modifier = Modifier.size(dimens.sm + dimens.xs)
                        )
                        Text(
                            text = "Playing XI (${currentSquad.size})",
                            style = typography.headingSmall,
                            color = colors.textPrimary
                        )
                    }
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimens.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = hierarchy.secondary.primary)
                        }
                    }
                } else {
                    items(currentSquad, key = { it.name }) { player ->
                        PlayerRowCard(player = player, onClick = { onPlayerClick?.invoke(player) })
                    }
                }

                item {
                    SquadLegend()
                }
            }
        }
    }
}

@Composable
private fun TeamSelectorCard(
    team1Name: String,
    team2Name: String,
    selectedIndex: Int,
    onTeamSelected: (Int) -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radius.card),
        useGoldBorder = false,
        contentPadding = PaddingValues(dimens.xs)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.xs)) {
            TeamSelectorItem(
                teamName = team1Name,
                isSelected = selectedIndex == 0,
                onClick = { onTeamSelected(0) },
                modifier = Modifier.weight(1f)
            )
            TeamSelectorItem(
                teamName = team2Name,
                isSelected = selectedIndex == 1,
                onClick = { onTeamSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TeamSelectorItem(
    teamName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Box(
        modifier = modifier
            .background(
                color = if (isSelected) colors.goldPrimary.copy(alpha = 0.16f) else colors.surface1,
                shape = RoundedCornerShape(dimens.radius.md)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) colors.goldPrimary.copy(alpha = 0.4f) else colors.glassBorder,
                shape = RoundedCornerShape(dimens.radius.md)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.sm, vertical = dimens.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = teamName,
            style = typography.bodyMedium,
            color = if (isSelected) colors.textPrimary else colors.textSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusCard(message: String) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = false,
        contentPadding = PaddingValues(dimens.sm)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.sm + dimens.xs)
            )
            Text(text = message, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun PlayerRowCard(
    player: Player,
    onClick: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        useGoldBorder = player.isCaptain,
        contentPadding = PaddingValues(horizontal = dimens.sm, vertical = dimens.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.md + dimens.sm)
                    .background(colors.surface1, CircleShape)
                    .border(1.dp, colors.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(dimens.sm + dimens.xs)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = player.name,
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = player.role.displayName,
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            if (player.isCaptain) {
                RoleBadge(text = "C", color = hierarchy.secondary.primary)
            }
            if (player.isWicketKeeper) {
                RoleBadge(text = "WK", color = colors.statusInfo)
            }
        }
    }
}

@Composable
private fun RoleBadge(text: String, color: Color) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            color = if (text == "C") hierarchy.secondary.primary else color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SquadLegend() {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = false,
        contentPadding = PaddingValues(dimens.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "C = Captain", style = typography.bodySmall, color = colors.textSecondary)
            Text(text = "WK = Wicket-Keeper", style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

private fun generatePlaceholderSquad(teamName: String): List<Player> {
    try {
        val batsmen = listOf("TBA", "TBA", "TBA", "TBA")
        val allRounders = listOf("TBA", "TBA", "TBA")
        val bowlers = listOf("TBA", "TBA", "TBA", "TBA")

        return listOf(
            Player(batsmen[0], PlayerRole.BATSMAN, isCaptain = true),
            Player(batsmen[1], PlayerRole.WICKET_KEEPER, isWicketKeeper = true),
            Player(batsmen[2], PlayerRole.BATSMAN),
            Player(batsmen[3], PlayerRole.BATSMAN),
            Player(allRounders[0], PlayerRole.ALL_ROUNDER),
            Player(allRounders[1], PlayerRole.ALL_ROUNDER),
            Player(allRounders[2], PlayerRole.ALL_ROUNDER),
            Player(bowlers[0], PlayerRole.BOWLER),
            Player(bowlers[1], PlayerRole.BOWLER),
            Player(bowlers[2], PlayerRole.BOWLER),
            Player(bowlers[3], PlayerRole.BOWLER)
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
