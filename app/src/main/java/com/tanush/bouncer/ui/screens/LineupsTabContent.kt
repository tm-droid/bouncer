package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.network.SquadScraper
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.Pitch3DView
import com.tanush.bouncer.ui.components.glass.PlayerAvatar
import com.tanush.bouncer.ui.components.glass.PlayerRole
import com.tanush.bouncer.ui.components.glass.RoleBadge
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.etchedText

// ─────────────────────────────────────────────────────────────────────────────
//  Enhanced Futuristic Lineups Content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Premium lineups display with a 3D Pitch visualization and 4-column player grids.
 */
@Composable
fun LineupsContent(
    team1Name: String,
    team1Code: String,
    team1Players: List<SquadScraper.Player>,
    team2Name: String,
    team2Code: String,
    team2Players: List<SquadScraper.Player>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens

    val team1Color = TeamPrimaryColors.getTeamColor(team1Code)
    val team2Color = TeamPrimaryColors.getTeamColor(team2Code)

    val (team1Playing, team1Bench) = partitionSquad(team1Players)
    val (team2Playing, team2Bench) = partitionSquad(team2Players)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 0.dp,
            end = 16.dp,
            bottom = 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── 1. Pitch Visualization (Redesigned) ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FIELD STRATEGY",
                    style = BouncerDesignSystem.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.goldPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                ) {
                    Pitch3DView(modifier = Modifier.fillMaxSize())
                }
            }
        }

        // ── 2. Side-by-Side Playing XI ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamHeaderMini(name = team1Name, code = team1Code)
                Text(
                    text = "VS",
                    style = BouncerDesignSystem.typography.headingSmall.copy(fontSize = 14.sp),
                    color = Color.White.copy(alpha = 0.2f),
                    fontWeight = FontWeight.Black
                )
                TeamHeaderMini(name = team2Name, code = team2Code, isRightAligned = true)
            }
        }

        // Playing XI Comparison Rows
        val maxPlaying = maxOf(team1Playing.size, team2Playing.size)
        items(maxPlaying) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Team 1 Player
                Box(modifier = Modifier.weight(1f)) {
                    team1Playing.getOrNull(index)?.let { player ->
                        SplitPlayerTile(player = player, teamColor = team1Color, isLeft = true)
                    }
                }

                // Team 2 Player
                Box(modifier = Modifier.weight(1f)) {
                    team2Playing.getOrNull(index)?.let { player ->
                        SplitPlayerTile(player = player, teamColor = team2Color, isLeft = false)
                    }
                }
            }
        }

        // ── 3. Bench Section ──
        if (team1Bench.isNotEmpty() || team2Bench.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                    Text(
                        text = "SUBSTITUTES & BENCH",
                        style = BouncerDesignSystem.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        letterSpacing = 2.sp
                    )
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                }
            }

            val maxBench = maxOf(team1Bench.size, team2Bench.size)
            items(maxBench) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        team1Bench.getOrNull(index)?.let { player ->
                            SplitPlayerTile(player = player, teamColor = team1Color, isLeft = true, isBench = true)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        team2Bench.getOrNull(index)?.let { player ->
                            SplitPlayerTile(player = player, teamColor = team2Color, isLeft = false, isBench = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamHeaderMini(name: String, code: String, isRightAligned: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isRightAligned) Arrangement.End else Arrangement.Start,
        modifier = Modifier.width(140.dp)
    ) {
        if (!isRightAligned) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(TeamPrimaryColors.getTeamColor(code).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = TeamPrimaryColors.getTeamEmoji(code), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = name.take(12).uppercase(),
            style = BouncerDesignSystem.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isRightAligned) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(TeamPrimaryColors.getTeamColor(code).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = TeamPrimaryColors.getTeamEmoji(code), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SplitPlayerTile(
    player: SquadScraper.Player,
    teamColor: Color,
    isLeft: Boolean,
    isBench: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        borderOpacity = if (isBench) 0.04f else 0.1f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
        ) {
            if (isLeft) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(teamColor.copy(alpha = if (isBench) 0.05f else 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👤", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = player.name,
                        style = typography.playerName.copy(fontSize = 11.sp),
                        color = if (isBench) colors.textMuted else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (player.role.isNotBlank()) {
                        Text(
                            text = player.role.take(10),
                            style = typography.labelSmall.copy(fontSize = 8.sp),
                            color = colors.goldPrimary.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = player.name,
                        style = typography.playerName.copy(fontSize = 11.sp),
                        color = if (isBench) colors.textMuted else Color.White,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (player.role.isNotBlank()) {
                        Text(
                            text = player.role.take(10),
                            style = typography.labelSmall.copy(fontSize = 8.sp),
                            color = colors.goldPrimary.copy(alpha = 0.5f),
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(teamColor.copy(alpha = if (isBench) 0.05f else 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👤", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    teamColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(teamColor)
        )
        Text(
            text = title.uppercase(),
            style = BouncerDesignSystem.typography.headingSmall,
            color = BouncerDesignSystem.colors.textPrimary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun PlayerGrid(
    players: List<SquadScraper.Player>,
    teamColor: Color,
    isBench: Boolean = false
) {
    val dimens = BouncerDesignSystem.dimens
    val columns = 4
    
    // Chunk players into rows of 4
    val rows = players.chunked(columns)
    
    Column(
        modifier = Modifier.padding(horizontal = dimens.screenHorizontalPadding - dimens.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
    ) {
        rows.forEach { rowPlayers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
            ) {
                rowPlayers.forEach { player ->
                    PlayerTile(
                        player = player,
                        teamColor = teamColor,
                        isBench = isBench,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty slots in last row
                repeat(columns - rowPlayers.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayerTile(
    player: SquadScraper.Player,
    teamColor: Color,
    isBench: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    GlassCardSmall(
        modifier = modifier.aspectRatio(0.8f),
        contentPadding = PaddingValues(4.dp),
        borderOpacity = if (isBench) 0.05f else 0.15f
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PlayerAvatar(
                imageUrl = player.imageUrl.ifBlank { null },
                playerName = player.name,
                teamColor = teamColor,
                size = 40.dp,
                showBorder = !isBench && (player.isCaptain || player.isWicketKeeper)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = player.name.split(" ").last(),
                style = typography.labelSmall,
                color = if (isBench) colors.textMuted else colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            
            if (player.role.isNotBlank()) {
                Text(
                    text = player.role.take(3).uppercase(),
                    style = typography.labelSmall.copy(fontSize = 8.sp),
                    color = colors.goldPrimary.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
//  Split Player Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SplitPlayerRow(
    player: SquadScraper.Player,
    teamColor: Color,
    isLeftAligned: Boolean,
    isBench: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val role = resolvePlayerRole(player)
    val accentAlpha = if (isBench) 0.15f else 0.45f

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Team-colored accent border on the side facing the divider
                    val barWidth = 3.dp.toPx()
                    if (isLeftAligned) {
                        drawRect(
                            color = teamColor.copy(alpha = accentAlpha),
                            topLeft = Offset(size.width - barWidth, 0f),
                            size = androidx.compose.ui.geometry.Size(barWidth, size.height)
                        )
                    } else {
                        drawRect(
                            color = teamColor.copy(alpha = accentAlpha),
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(barWidth, size.height)
                        )
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLeftAligned) {
                PlayerAvatar(
                    imageUrl = player.imageUrl.ifBlank { null },
                    playerName = player.name,
                    size = 34.dp,
                    teamColor = teamColor,
                    showBorder = !isBench,
                    isDismissed = isBench
                )
                PlayerInfoColumn(
                    player = player,
                    role = role,
                    isBench = isBench,
                    modifier = Modifier.weight(1f)
                )
            } else {
                PlayerInfoColumn(
                    player = player,
                    role = role,
                    isBench = isBench,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                PlayerAvatar(
                    imageUrl = player.imageUrl.ifBlank { null },
                    playerName = player.name,
                    size = 34.dp,
                    teamColor = teamColor,
                    showBorder = !isBench,
                    isDismissed = isBench
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player Info Column (name + role badge)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerInfoColumn(
    player: SquadScraper.Player,
    role: PlayerRole?,
    isBench: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Column(
        modifier = modifier,
        horizontalAlignment = if (textAlign == TextAlign.End) Alignment.End else Alignment.Start
    ) {
        Text(
            text = buildPlayerDisplayName(player),
            style = typography.playerName,
            color = if (isBench) colors.textMuted else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )

        if (role != null) {
            Spacer(modifier = Modifier.height(2.dp))
            RoleBadge(role = role)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bench Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BenchHeader() {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.06f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "BENCH",
            style = typography.labelSmall,
            color = colors.textMuted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = typography.labelSmall.letterSpacing * 1.5f
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Partition a squad list into playing XI (first 11) and bench/substitutes.
 */
private fun partitionSquad(
    players: List<SquadScraper.Player>
): Pair<List<SquadScraper.Player>, List<SquadScraper.Player>> {
    return if (players.size > 11) {
        players.take(11) to players.drop(11)
    } else {
        players to emptyList()
    }
}

/**
 * Build a display name with captain / wicketkeeper suffixes.
 */
private fun buildPlayerDisplayName(player: SquadScraper.Player): String = buildString {
    append(player.name)
    if (player.isCaptain && player.isWicketKeeper) append(" (C & WK)")
    else if (player.isCaptain) append(" (C)")
    else if (player.isWicketKeeper) append(" (WK)")
}

/**
 * Map a [SquadScraper.Player] to a [PlayerRole] for badge display.
 */
private fun resolvePlayerRole(player: SquadScraper.Player): PlayerRole? {
    if (player.isCaptain) return PlayerRole.CAPTAIN
    if (player.isWicketKeeper) return PlayerRole.WICKETKEEPER
    return when {
        player.role.contains("bat", ignoreCase = true) -> PlayerRole.BATSMAN
        player.role.contains("bowl", ignoreCase = true) -> PlayerRole.BOWLER
        player.role.contains("all", ignoreCase = true) -> PlayerRole.ALL_ROUNDER
        player.role.contains("wk", ignoreCase = true) ||
            player.role.contains("keeper", ignoreCase = true) -> PlayerRole.WICKETKEEPER
        else -> null
    }
}
