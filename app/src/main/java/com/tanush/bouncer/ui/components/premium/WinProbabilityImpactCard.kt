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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.tanush.bouncer.ui.theme.BoundaryFour
import com.tanush.bouncer.ui.theme.BoundarySix
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Win Probability Impact Card
 *
 * TODO: Integration Requirements:
 * - Integrate with win probability ML model
 * - Track WP changes after each ball/event
 * - Identify highest impact moments
 * - Show WP swing visualization
 * - Rank events by impact magnitude
 * - Link to video replays of key moments
 * - Add real-time WP updates via WebSocket
 * - Compare with historical match patterns
 * - Navigate to moment details on tap
 * - Cache impact data for match analysis
 */

data class ImpactMoment(
    val description: String,
    val over: String,
    val impact: Float, // -1.0 to 1.0 (negative favors team2, positive favors team1)
    val team1WinProbBefore: Float,
    val team1WinProbAfter: Float,
    val eventType: String, // "Wicket", "Boundary", "Drop", "Run Out", etc.
)

@Composable
fun WinProbabilityImpactCard(
    team1Name: String,
    team2Name: String,
    impactMoments: List<ImpactMoment>,
    currentTeam1WinProb: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "impact")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(impactMoments) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFE040FB), Color(0xFF7C4DFF)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "WIN PROBABILITY IMPACT",
                            color = colors.goldDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "Key Moments",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current probability bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = team1Name,
                    color = colors.statusInfo,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(50.dp),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f)),
                ) {
                    // Team 1 bar
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = currentTeam1WinProb * animatedProgress.value)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(colors.statusInfo, colors.statusInfo.copy(alpha = 0.6f)),
                                ),
                            ),
                    )

                    // Center line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .align(Alignment.Center)
                            .background(Color.White.copy(alpha = 0.5f)),
                    )

                    // Percentages
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${(currentTeam1WinProb * 100).toInt()}%",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${((1 - currentTeam1WinProb) * 100).toInt()}%",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    text = team2Name,
                    color = colors.statusLive,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(50.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Impact moments timeline
            impactMoments.sortedByDescending { kotlin.math.abs(it.impact) }.take(5).forEachIndexed { index, moment ->
                ImpactMomentRow(
                    moment = moment,
                    team1Name = team1Name,
                    team2Name = team2Name,
                    progress = animatedProgress.value,
                    pulseAlpha = pulseAlpha,
                    index = index,
                )

                if (index < minOf(impactMoments.size - 1, 4)) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ImpactMomentRow(
    moment: ImpactMoment,
    team1Name: String,
    team2Name: String,
    progress: Float,
    pulseAlpha: Float,
    index: Int,
) {
    val colors = BouncerDesignSystem.colors
    val delayedProgress = ((progress - index * 0.1f) / 0.9f).coerceIn(0f, 1f)
    val impactAbs = kotlin.math.abs(moment.impact)
    val favorsTeam1 = moment.impact > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = delayedProgress
                translationX = (1 - delayedProgress) * 30f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    impactAbs > 0.15f && favorsTeam1 -> Brush.horizontalGradient(
                        colors = listOf(colors.statusInfo.copy(alpha = 0.15f * pulseAlpha), Color.Transparent),
                    )
                    impactAbs > 0.15f && !favorsTeam1 -> Brush.horizontalGradient(
                        colors = listOf(colors.statusLive.copy(alpha = 0.15f * pulseAlpha), Color.Transparent),
                    )
                    else -> Brush.horizontalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                    )
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Impact indicator
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (favorsTeam1) colors.statusInfo.copy(alpha = 0.2f) else colors.statusLive.copy(alpha = 0.2f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (favorsTeam1) "↑" else "↓",
                color = if (favorsTeam1) colors.statusInfo else colors.statusLive,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Moment details
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventTypeBadge(eventType = moment.eventType)

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Over ${moment.over}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = moment.description,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Win probability change
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${(moment.team1WinProbBefore * 100).toInt()}%",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )

                Text(
                    text = " → ",
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                )

                Text(
                    text = "${(moment.team1WinProbAfter * 100).toInt()}%",
                    color = if (favorsTeam1) colors.statusSuccess else colors.statusLive,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "(${if (favorsTeam1) "+" else ""}${(moment.impact * 100).toInt()}%)",
                    color = if (favorsTeam1) colors.statusSuccess else colors.statusLive,
                    fontSize = 10.sp,
                )
            }
        }

        // Impact magnitude
        ImpactMagnitudeIndicator(impact = impactAbs)
    }
}

@Composable
private fun EventTypeBadge(eventType: String) {
    val colors = BouncerDesignSystem.colors
    val (color, icon) = when (eventType) {
        "Wicket" -> Pair(colors.statusLive, "W")
        "Boundary" -> Pair(colors.statusWarning, "4")
        "Six" -> Pair(colors.statusWarning, "6")
        "Drop" -> Pair(colors.statusWarning, "!")
        "Run Out" -> Pair(colors.statusLive, "RO")
        else -> Pair(colors.goldDark, "•")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = eventType,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ImpactMagnitudeIndicator(impact: Float) {
    val colors = BouncerDesignSystem.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { i ->
            val threshold = (i + 1) * 0.06f
            val isFilled = impact >= threshold

            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = (8 + i * 3).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isFilled) {
                            when {
                                impact > 0.2f -> colors.statusLive
                                impact > 0.1f -> colors.statusWarning
                                else -> colors.statusSuccess
                            }
                        } else {
                            Color.White.copy(alpha = 0.1f)
                        },
                    ),
            )
        }
    }
}
