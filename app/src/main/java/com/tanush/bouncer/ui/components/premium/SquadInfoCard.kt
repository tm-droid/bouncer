package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Squad info card showing team lineup.
 * Updated: BouncerImportance.MINIMAL to avoid visual clutter in high-stakes dashboards.
 */

data class SquadInfoData(
    val teamName: String,
    val teamLogoUrl: String?,
    val captain: String,
    val viceCaptain: String?,
    val wicketKeeper: String,
    val players: List<SquadPlayer>,
    val recentForm: String,
    val matchesPlayed: Int,
)

data class SquadPlayer(
    val name: String,
    val role: PlayerRole,
    val isPlaying: Boolean,
    val isCaptain: Boolean = false,
    val isWicketKeeper: Boolean = false,
    val jerseyNumber: Int?,
)

enum class PlayerRole {
    BATSMAN, BOWLER, ALL_ROUNDER, WICKET_KEEPER
}

@Composable
fun SquadInfoCard(
    squadData: SquadInfoData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(squadData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
    }

    val roleEmojis = mapOf(
        PlayerRole.BATSMAN to "🏏",
        PlayerRole.BOWLER to "🎳",
        PlayerRole.ALL_ROUNDER to "⭐",
        PlayerRole.WICKET_KEEPER to "🧤",
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Technical & Subdued
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.02f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (squadData.teamLogoUrl != null) {
                            CoilImage(
                                model = squadData.teamLogoUrl,
                                contentDescription = squadData.teamName,
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Icon(
                                Icons.Default.People,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = squadData.teamName.uppercase(),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${squadData.players.count { it.isPlaying }}/11 ACTIVE",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = squadData.recentForm,
                        color = colors.statusSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "RECENT FORM",
                        color = TextTertiary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Leadership Row - Clean Technical Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LeadershipBadge(Modifier.weight(1f), "CPT", squadData.captain, colors.goldPrimary)
                if (squadData.viceCaptain != null) {
                    LeadershipBadge(Modifier.weight(1f), "VCP", squadData.viceCaptain, TextSecondary)
                }
                LeadershipBadge(Modifier.weight(1f), "WKT", squadData.wicketKeeper, colors.statusInfo)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Squad Composition - Subdued
            Text(
                text = "SQUAD COMPOSITION",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val total = squadData.players.size.coerceAtLeast(1)
                RoleBar(count = squadData.players.count { it.role == PlayerRole.BATSMAN }, total = total, color = colors.statusWarning, label = "BAT")
                RoleBar(count = squadData.players.count { it.role == PlayerRole.ALL_ROUNDER }, total = total, color = colors.goldPrimary, label = "AR")
                RoleBar(count = squadData.players.count { it.role == PlayerRole.BOWLER }, total = total, color = colors.statusInfo, label = "BOWL")
                RoleBar(count = squadData.players.count { it.role == PlayerRole.WICKET_KEEPER }, total = total, color = colors.statusSuccess, label = "WK")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Playing XI Header
            Text(
                text = "PLAYING XI",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Player Grid - Fixed overlapping with Ellipsis
            val playingPlayers = squadData.players.filter { it.isPlaying }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                playingPlayers.chunked(2).forEach { rowPlayers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowPlayers.forEach { player ->
                            PlayerChip(
                                player = player,
                                roleEmoji = roleEmojis[player.role] ?: "🏏",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowPlayers.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LeadershipBadge(
    modifier: Modifier,
    title: String,
    name: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    GlassSurface(
        modifier = modifier,
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = color.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = name.split(" ").last().uppercase(),
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RowScope.RoleBar(
    count: Int,
    total: Int,
    color: Color,
    label: String
) {
    val colors = BouncerDesignSystem.colors
    val weight = (count.toFloat() / total).coerceAtLeast(0.05f)
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = color, fontSize = 7.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PlayerChip(
    player: SquadPlayer,
    roleEmoji: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = roleEmoji, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = player.name.uppercase(),
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (player.isCaptain) {
                    Text(text = "©", color = colors.goldPrimary, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 2.dp))
                }
            }
            Text(
                text = player.role.name.replace("_", " "),
                color = TextTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
