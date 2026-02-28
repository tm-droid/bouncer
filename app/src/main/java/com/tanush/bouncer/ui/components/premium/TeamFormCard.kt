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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*

/**
 * Team form card showing recent match results
 *
 * TODO: Integration Requirements:
 * - Fetch team's recent match history from API
 * - Show W/L/D sequence (form guide)
 * - Calculate win percentage and trend
 * - Add format-specific form filtering
 * - Display venue/home-away form splits
 * - Navigate to match details on tap
 * - Compare with opponent's form
 * - Show form rating/score
 * - Add historical form comparison
 * - Cache form data for quick access
 */

data class TeamMatchRecord(
    val opponent: String,
    val matchResult: MatchOutcome,
    val scoreFor: String,
    val scoreAgainst: String,
    val venue: String,
    val date: String,
    val format: String,
)

enum class MatchOutcome {
    WIN, LOSS, DRAW, NO_RESULT, TIE
}

@Composable
fun TeamFormCard(
    teamName: String,
    recentMatches: List<TeamMatchRecord>,
    winPercentage: Float,
    currentStreak: Int,
    isWinningStreak: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(recentMatches) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

    val wins = recentMatches.count { it.matchResult == MatchOutcome.WIN }
    val losses = recentMatches.count { it.matchResult == MatchOutcome.LOSS }

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
                                    colors = listOf(ShinyGoldMid, ShinyGoldDeep),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "TEAM FORM",
                            color = ShinyGoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = teamName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Win percentage
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${(winPercentage * animatedProgress.value).toInt()}%",
                        color = ShinyGoldMid,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "win rate",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Form bubbles (W/L/D sequence)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recentMatches.takeLast(5).reversed().forEachIndexed { index, match ->
                    val bubbleColor = when (match.matchResult) {
                        MatchOutcome.WIN -> SuccessGreen
                        MatchOutcome.LOSS -> WicketRed
                        MatchOutcome.DRAW, MatchOutcome.TIE -> WarningYellow
                        MatchOutcome.NO_RESULT -> Color.Gray
                    }

                    val bubbleText = when (match.matchResult) {
                        MatchOutcome.WIN -> "W"
                        MatchOutcome.LOSS -> "L"
                        MatchOutcome.DRAW -> "D"
                        MatchOutcome.TIE -> "T"
                        MatchOutcome.NO_RESULT -> "NR"
                    }

                    val bubbleAlpha = (animatedProgress.value * 5 - index).coerceIn(0f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { alpha = bubbleAlpha }
                                .clip(CircleShape)
                                .background(bubbleColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = bubbleText,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "v ${match.opponent.take(3)}",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            modifier = Modifier.graphicsLayer { alpha = bubbleAlpha },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FormStatColumn(
                    value = "$wins",
                    label = "Wins",
                    color = SuccessGreen,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color(0xFF3D3D55)),
                )

                FormStatColumn(
                    value = "$losses",
                    label = "Losses",
                    color = WicketRed,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color(0xFF3D3D55)),
                )

                FormStatColumn(
                    value = "$currentStreak",
                    label = if (isWinningStreak) "Win Streak" else "Loss Streak",
                    color = if (isWinningStreak) SuccessGreen else WicketRed,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent match details
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "RECENT MATCHES",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                recentMatches.takeLast(3).reversed().forEach { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2D2D44))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Result indicator
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when (match.matchResult) {
                                        MatchOutcome.WIN -> SuccessGreen.copy(alpha = 0.2f)
                                        MatchOutcome.LOSS -> WicketRed.copy(alpha = 0.2f)
                                        else -> Color.Gray.copy(alpha = 0.2f)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (match.matchResult) {
                                    MatchOutcome.WIN -> "W"
                                    MatchOutcome.LOSS -> "L"
                                    MatchOutcome.DRAW -> "D"
                                    MatchOutcome.TIE -> "T"
                                    MatchOutcome.NO_RESULT -> "NR"
                                },
                                color = when (match.matchResult) {
                                    MatchOutcome.WIN -> SuccessGreen
                                    MatchOutcome.LOSS -> WicketRed
                                    else -> Color.Gray
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "vs ${match.opponent}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = match.format,
                                    color = ShinyGoldMid,
                                    fontSize = 10.sp,
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${match.scoreFor} vs ${match.scoreAgainst}",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                )
                                Text(
                                    text = match.date,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormStatColumn(
    value: String,
    label: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun TeamFormCardPreview() {
    val matches = listOf(
        TeamMatchRecord("Australia", MatchOutcome.WIN, "186/4", "182/8", "Mumbai", "Nov 15", "T20I"),
        TeamMatchRecord("England", MatchOutcome.WIN, "224/2", "215/7", "Delhi", "Nov 12", "T20I"),
        TeamMatchRecord("Pakistan", MatchOutcome.LOSS, "145/10", "152/5", "Dubai", "Nov 8", "T20I"),
        TeamMatchRecord("South Africa", MatchOutcome.WIN, "198/3", "195/6", "Chennai", "Nov 5", "T20I"),
        TeamMatchRecord("New Zealand", MatchOutcome.WIN, "178/5", "175/8", "Kolkata", "Nov 2", "T20I"),
    )

    TeamFormCard(
        teamName = "India",
        recentMatches = matches,
        winPercentage = 80f,
        currentStreak = 2,
        isWinningStreak = true,
    )
}
