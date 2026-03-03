package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * AI prediction insights card showing match outcome predictions
 *
 * TODO: API Integration
 * - Connect to prediction ML model service for probability calculations
 * - Implement prediction update API for ball-by-ball probability refresh
 * - Add historical prediction accuracy tracking and display
 * - Connect key factors analysis to match analytics engine
 *
 * TODO: Features
 * - Add prediction breakdown by innings/phases
 * - Show prediction history for current match
 * - Implement user prediction vs AI prediction comparison
 * - Add explanation tooltips for each factor's impact
 *
 * TODO: UI/UX
 * - Animate probability changes on live updates
 * - Add confidence indicator visualization
 * - Show prediction source and model version
 */

data class PredictionStats(
    val team1Name: String,
    val team2Name: String,
    val team1WinProbability: Float,
    val team2WinProbability: Float,
    val drawProbability: Float,
    val confidence: Float, // 0.0 - 1.0
    val keyFactors: List<PredictionFactor>,
    val predictionSource: String,
)

data class PredictionFactor(
    val factor: String,
    val impact: Float, // -1.0 to 1.0
    val description: String,
)

@Composable
fun PredictionInsightsCard(
    prediction: PredictionStats,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "prediction")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    val favoriteTeam = if (prediction.team1WinProbability > prediction.team2WinProbability) 1 else 2
    val colors = BouncerDesignSystem.colors
    val goldDarkC = colors.goldDark
    val favoriteColor = if (favoriteTeam == 1) colors.statusInfo else colors.statusLive
    val favoriteProbability = maxOf(prediction.team1WinProbability, prediction.team2WinProbability)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Shimmer and glow background
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Glow effect around the favorite
                drawCircle(
                    color = favoriteColor.copy(alpha = 0.1f * glowAlpha),
                    radius = 200f,
                    center = if (favoriteTeam == 1) {
                        Offset(size.width * 0.2f, size.height * 0.3f)
                    } else {
                        Offset(size.width * 0.8f, size.height * 0.3f)
                    },
                )

                // Shimmer
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        goldDarkC.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * shimmerOffset, 0f),
                    end = Offset(size.width * (shimmerOffset + 0.3f), size.height),
                )
                drawRect(brush = shimmerBrush)
            }

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
                                        colors = listOf(colors.goldPrimary, colors.goldDark),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsScore,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "AI PREDICTION",
                                color = colors.goldDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = prediction.predictionSource,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    // Confidence badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    prediction.confidence >= 0.8f -> colors.statusSuccess.copy(alpha = 0.2f)
                                    prediction.confidence >= 0.6f -> colors.statusWarning.copy(alpha = 0.2f)
                                    else -> Color.White.copy(alpha = 0.1f)
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "${(prediction.confidence * 100).toInt()}% confident",
                            color = when {
                                prediction.confidence >= 0.8f -> colors.statusSuccess
                                prediction.confidence >= 0.6f -> colors.statusWarning
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Win probability display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Team 1
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = prediction.team1Name,
                            color = colors.statusInfo,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    if (favoriteTeam == 1) {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                colors.statusInfo.copy(alpha = 0.3f),
                                                Color.Transparent,
                                            ),
                                        )
                                    } else {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.1f),
                                                Color.Transparent,
                                            ),
                                        )
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(prediction.team1WinProbability * 100).toInt()}%",
                                    color = if (favoriteTeam == 1) colors.statusInfo else Color.White.copy(alpha = 0.7f),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (favoriteTeam == 1) {
                                    Text(
                                        text = "FAVORITE",
                                        color = colors.statusInfo,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // Draw probability
                    if (prediction.drawProbability > 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "DRAW",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                            )
                            Text(
                                text = "${(prediction.drawProbability * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Team 2
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = prediction.team2Name,
                            color = colors.statusLive,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    if (favoriteTeam == 2) {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                colors.statusLive.copy(alpha = 0.3f),
                                                Color.Transparent,
                                            ),
                                        )
                                    } else {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.1f),
                                                Color.Transparent,
                                            ),
                                        )
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(prediction.team2WinProbability * 100).toInt()}%",
                                    color = if (favoriteTeam == 2) colors.statusLive else Color.White.copy(alpha = 0.7f),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (favoriteTeam == 2) {
                                    Text(
                                        text = "FAVORITE",
                                        color = colors.statusLive,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Key factors
                Text(
                    text = "KEY FACTORS",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                prediction.keyFactors.forEach { factor ->
                    PredictionFactorRow(factor = factor)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PredictionFactorRow(
    factor: PredictionFactor,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val impactColor = when {
        factor.impact > 0.3f -> colors.statusSuccess
        factor.impact > 0 -> colors.statusInfo
        factor.impact < -0.3f -> colors.statusLive
        factor.impact < 0 -> colors.statusWarning
        else -> Color.White.copy(alpha = 0.5f)
    }

    val impactText = when {
        factor.impact > 0.5f -> "Major boost"
        factor.impact > 0.2f -> "Slight edge"
        factor.impact > 0 -> "Minor plus"
        factor.impact < -0.5f -> "Big concern"
        factor.impact < -0.2f -> "Slight worry"
        factor.impact < 0 -> "Minor minus"
        else -> "Neutral"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(impactColor),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = factor.factor,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = factor.description,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(impactColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = impactText,
                color = impactColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
