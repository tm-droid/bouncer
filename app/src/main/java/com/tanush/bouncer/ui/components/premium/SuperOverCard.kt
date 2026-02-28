package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BoundaryFour
import com.tanush.bouncer.ui.theme.BoundarySix
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.LiveBlue
import com.tanush.bouncer.ui.theme.GoldDark
import com.tanush.bouncer.ui.theme.GoldLight
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*

/**
 * Super over card for tied matches
 *
 * TODO: Integration Requirements:
 * - Detect super over scenario from match state
 * - Track super over in real-time (ball-by-ball)
 * - Show both teams' super over scores
 * - Display required runs calculation
 * - Add dramatic tension animations
 * - Track wickets in super over
 * - Show historical super over stats
 * - Handle multiple super overs
 * - Add celebration effects for winner
 * - Cache super over data for highlights
 */

data class SuperOverData(
    val team1Name: String,
    val team2Name: String,
    val team1Score: Int,
    val team2Score: Int,
    val team1Wickets: Int,
    val team2Wickets: Int,
    val team1Deliveries: List<SuperOverDelivery>,
    val team2Deliveries: List<SuperOverDelivery>,
    val isTeam1Batting: Boolean,
    val isComplete: Boolean,
    val winner: String?,
)

data class SuperOverDelivery(
    val ballNumber: Int,
    val runs: Int,
    val isWicket: Boolean,
    val isSix: Boolean,
    val isFour: Boolean,
    val isExtra: Boolean,
)

@Composable
fun SuperOverCard(
    superOverData: SuperOverData,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "superover")

    val flashPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flash",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(superOverData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
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
            // Header with flashing effect
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
                                    colors = listOf(
                                        WicketRed.copy(alpha = flashPulse),
                                        Color(0xFFFF5722).copy(alpha = flashPulse),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SUPER OVER",
                            color = WicketRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = if (superOverData.isComplete) "Complete" else "In Progress",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                }

                if (!superOverData.isComplete) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LiveBlue.copy(alpha = flashPulse * 0.3f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "● LIVE",
                            color = LiveBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scores display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Team 1
                SuperOverTeamScore(
                    teamName = superOverData.team1Name,
                    score = superOverData.team1Score,
                    wickets = superOverData.team1Wickets,
                    isBatting = superOverData.isTeam1Batting && !superOverData.isComplete,
                    isWinner = superOverData.winner == superOverData.team1Name,
                    flashPulse = flashPulse,
                )

                // VS
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "VS",
                        color = GoldPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )

                    if (superOverData.isComplete) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FINAL",
                            color = Color.Gray,
                            fontSize = 10.sp,
                        )
                    }
                }

                // Team 2
                SuperOverTeamScore(
                    teamName = superOverData.team2Name,
                    score = superOverData.team2Score,
                    wickets = superOverData.team2Wickets,
                    isBatting = !superOverData.isTeam1Batting && !superOverData.isComplete,
                    isWinner = superOverData.winner == superOverData.team2Name,
                    flashPulse = flashPulse,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ball-by-ball for both teams
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Team 1 deliveries
                SuperOverDeliveryRow(
                    teamName = superOverData.team1Name,
                    deliveries = superOverData.team1Deliveries,
                    isCurrentlyBatting = superOverData.isTeam1Batting && !superOverData.isComplete,
                    progress = animatedProgress.value,
                )

                // Team 2 deliveries
                if (superOverData.team2Deliveries.isNotEmpty() || !superOverData.isTeam1Batting) {
                    SuperOverDeliveryRow(
                        teamName = superOverData.team2Name,
                        deliveries = superOverData.team2Deliveries,
                        isCurrentlyBatting = !superOverData.isTeam1Batting && !superOverData.isComplete,
                        progress = animatedProgress.value,
                    )
                }
            }

            // Winner announcement
            if (superOverData.isComplete && superOverData.winner != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    GoldDark.copy(alpha = 0.3f),
                                    GoldPrimary.copy(alpha = 0.2f),
                                ),
                            ),
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🏆",
                            fontSize = 32.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${superOverData.winner} WINS!",
                            color = GoldLight,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Super Over thriller",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Required to win (if applicable)
            if (!superOverData.isComplete && !superOverData.isTeam1Batting && superOverData.team2Deliveries.size < 6) {
                Spacer(modifier = Modifier.height(12.dp))

                val required = superOverData.team1Score - superOverData.team2Score + 1
                val ballsRemaining = 6 - superOverData.team2Deliveries.size

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChartBlue.copy(alpha = 0.15f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${superOverData.team2Name} need $required from $ballsRemaining balls",
                        color = ChartBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuperOverTeamScore(
    teamName: String,
    score: Int,
    wickets: Int,
    isBatting: Boolean,
    isWinner: Boolean,
    flashPulse: Float,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isWinner -> GoldDark.copy(alpha = 0.2f)
                    isBatting -> LiveBlue.copy(alpha = 0.1f * flashPulse)
                    else -> Color.Transparent
                },
            )
            .padding(12.dp),
    ) {
        Text(
            text = teamName,
            color = if (isWinner) GoldLight else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$score",
                color = if (isWinner) GoldPrimary else Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "/$wickets",
                color = Color.Gray,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (isBatting) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "BATTING",
                color = LiveBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        } else if (isWinner) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "WINNER",
                color = GoldPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SuperOverDeliveryRow(
    teamName: String,
    deliveries: List<SuperOverDelivery>,
    isCurrentlyBatting: Boolean,
    progress: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2D2D44))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = teamName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (isCurrentlyBatting) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LiveBlue),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            repeat(6) { index ->
                val delivery = deliveries.getOrNull(index)
                val isVisible = delivery != null && index < (deliveries.size * progress).toInt() + 1

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                delivery == null -> Color(0xFF3D3D55)
                                delivery.isWicket -> WicketRed
                                delivery.isSix -> BoundarySix
                                delivery.isFour -> BoundaryFour
                                delivery.isExtra -> WarningYellow
                                delivery.runs == 0 -> Color(0xFF4D4D65)
                                else -> ChartBlue
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            delivery == null -> "•"
                            !isVisible -> "•"
                            delivery.isWicket -> "W"
                            else -> "${delivery.runs}"
                        },
                        color = when {
                            delivery?.isExtra == true -> Color.Black
                            else -> Color.White
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun SuperOverCardPreview() {
    SuperOverCard(
        superOverData = SuperOverData(
            team1Name = "India",
            team2Name = "Australia",
            team1Score = 18,
            team2Score = 14,
            team1Wickets = 1,
            team2Wickets = 2,
            team1Deliveries = listOf(
                SuperOverDelivery(1, 6, false, true, false, false),
                SuperOverDelivery(2, 1, false, false, false, false),
                SuperOverDelivery(3, 4, false, false, true, false),
                SuperOverDelivery(4, 0, true, false, false, false),
                SuperOverDelivery(5, 2, false, false, false, false),
                SuperOverDelivery(6, 4, false, false, true, false),
            ),
            team2Deliveries = listOf(
                SuperOverDelivery(1, 4, false, false, true, false),
                SuperOverDelivery(2, 0, true, false, false, false),
                SuperOverDelivery(3, 6, false, true, false, false),
                SuperOverDelivery(4, 0, true, false, false, false),
                SuperOverDelivery(5, 2, false, false, false, false),
                SuperOverDelivery(6, 2, false, false, false, false),
            ),
            isTeam1Batting = false,
            isComplete = true,
            winner = "India",
        ),
    )
}
