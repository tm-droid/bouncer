package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Tournament standings card showing points table
 *
 * TODO: Integration Requirements:
 * - Fetch tournament standings from series API
 * - Update in real-time during matches
 * - Show qualification scenarios
 * - Display NRR and tiebreaker rules
 * - Add position change animations
 * - Highlight playoff/knockout zones
 * - Show remaining fixtures impact
 * - Navigate to team details on tap
 * - Add group stage support (World Cup)
 * - Cache standings for offline viewing
 */

data class TeamStanding(
    val position: Int,
    val teamName: String,
    val teamCode: String,
    val played: Int,
    val won: Int,
    val lost: Int,
    val noResult: Int,
    val points: Int,
    val netRunRate: Float,
    val recentForm: List<MatchOutcome>,
)

@Composable
fun TournamentStandingsCard(
    tournamentName: String,
    standings: List<TeamStanding>,
    highlightedTeam: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(standings) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "POINTS TABLE",
                            color = hierarchy.secondary.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = tournamentName,
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                )
                Text(
                    text = "TEAM",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "P",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = "W",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = "L",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = "NRR",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(50.dp),
                )
                Text(
                    text = "PTS",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Standings rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                standings.forEachIndexed { index, team ->
                    val rowAlpha = (animatedProgress.value * standings.size - index).coerceIn(0f, 1f)
                    val isHighlighted = team.teamName == highlightedTeam
                    val isQualifyingPosition = team.position <= 4

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = rowAlpha }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isHighlighted -> hierarchy.secondary.primary.copy(alpha = 0.2f)
                                    isQualifyingPosition -> colors.statusSuccess.copy(alpha = 0.05f)
                                    else -> Color(0xFF2D2D44).copy(alpha = 0.5f)
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Position
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    when (team.position) {
                                        1 -> hierarchy.secondary.primary
                                        2 -> Color(0xFFC0C0C0)
                                        3 -> Color(0xFFCD7F32)
                                        in 1..4 -> colors.statusSuccess.copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${team.position}",
                                color = if (team.position <= 3) Color.Black else colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Team name
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = team.teamCode,
                                color = if (isHighlighted) hierarchy.secondary.primary else colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            // Recent form
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                team.recentForm.takeLast(5).forEach { result ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (result) {
                                                    MatchOutcome.WIN -> colors.statusSuccess
                                                    MatchOutcome.LOSS -> colors.statusLive
                                                    else -> Color.Gray
                                                },
                                            ),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${team.played}",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.width(28.dp),
                        )

                        Text(
                            text = "${team.won}",
                            color = colors.statusSuccess,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(28.dp),
                        )

                        Text(
                            text = "${team.lost}",
                            color = colors.statusLive,
                            fontSize = 13.sp,
                            modifier = Modifier.width(28.dp),
                        )

                        Text(
                            text = String.format("%+.3f", team.netRunRate),
                            color = if (team.netRunRate >= 0) colors.statusSuccess else colors.statusLive,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(50.dp),
                        )

                        Text(
                            text = "${team.points}",
                            color = hierarchy.secondary.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.statusSuccess.copy(alpha = 0.3f)),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Qualifying",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.statusSuccess),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Win",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.statusLive),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Loss",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun TournamentStandingsCardPreview() {
    val colors = BouncerDesignSystem.colors
    val standings = listOf(
        TeamStanding(
            1, "India", "IND", 8, 7, 1, 0, 14, 1.234f,
            listOf(MatchOutcome.WIN, MatchOutcome.WIN, MatchOutcome.WIN, MatchOutcome.LOSS, MatchOutcome.WIN),
        ),
        TeamStanding(
            2, "Australia", "AUS", 8, 6, 2, 0, 12, 0.856f,
            listOf(MatchOutcome.WIN, MatchOutcome.LOSS, MatchOutcome.WIN, MatchOutcome.WIN, MatchOutcome.WIN),
        ),
        TeamStanding(
            3, "England", "ENG", 8, 5, 3, 0, 10, 0.452f,
            listOf(MatchOutcome.LOSS, MatchOutcome.WIN, MatchOutcome.WIN, MatchOutcome.LOSS, MatchOutcome.WIN),
        ),
        TeamStanding(
            4, "South Africa", "SA", 8, 5, 3, 0, 10, 0.234f,
            listOf(MatchOutcome.WIN, MatchOutcome.WIN, MatchOutcome.LOSS, MatchOutcome.WIN, MatchOutcome.LOSS),
        ),
        TeamStanding(
            5, "New Zealand", "NZ", 8, 4, 4, 0, 8, -0.123f,
            listOf(MatchOutcome.LOSS, MatchOutcome.LOSS, MatchOutcome.WIN, MatchOutcome.WIN, MatchOutcome.LOSS),
        ),
        TeamStanding(
            6, "Pakistan", "PAK", 8, 3, 5, 0, 6, -0.456f,
            listOf(MatchOutcome.WIN, MatchOutcome.LOSS, MatchOutcome.LOSS, MatchOutcome.LOSS, MatchOutcome.WIN),
        ),
    )

    TournamentStandingsCard(
        tournamentName = "ICC World Cup 2024",
        standings = standings,
        highlightedTeam = "India",
    )
}
