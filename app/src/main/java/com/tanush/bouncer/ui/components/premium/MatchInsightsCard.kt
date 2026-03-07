package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Match insights card showing key analysis points
 */

data class MatchInsightDetail(
    val title: String,
    val value: String,
    val subtext: String,
    val insightType: InsightType,
    val trend: InsightTrend,
)

enum class InsightType {
    BATTING, BOWLING, PARTNERSHIP, POWERPLAY, DEATH_OVERS, FIELDING, MOMENTUM
}

enum class InsightTrend {
    POSITIVE, NEGATIVE, NEUTRAL
}

@Composable
fun MatchInsightsCard(
    matchTitle: String,
    currentPhase: String,
    insights: List<MatchInsightDetail>,
    keyHighlight: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "insight")
    val lightPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(insights) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                                    colors = listOf(
                                        colors.statusWarning.copy(alpha = lightPulse),
                                        Color(0xFFFF9800).copy(alpha = lightPulse),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "MATCH INSIGHTS",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = currentPhase,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.statusInfo.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "LIVE",
                        color = colors.statusInfo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Key highlight banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = 0.3f),
                                colors.goldDark.copy(alpha = 0.1f),
                            ),
                        ),
                    )
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Insight",
                        tint = colors.goldLight,
                        modifier = Modifier.size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "KEY INSIGHT",
                            color = colors.goldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = keyHighlight,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Insights grid
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                insights.chunked(2).forEach { rowInsights ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowInsights.forEach { insight ->
                            InsightGridItem(
                                insight = insight,
                                progress = animatedProgress.value,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Fill empty space if odd number
                        if (rowInsights.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phase analysis
            PhaseAnalysisRow(currentPhase = currentPhase, progress = animatedProgress.value)
        }
    }
}

@Composable
private fun InsightGridItem(
    insight: MatchInsightDetail,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val trendColor = when (insight.trend) {
        InsightTrend.POSITIVE -> colors.statusSuccess
        InsightTrend.NEGATIVE -> colors.statusLive
        InsightTrend.NEUTRAL -> colors.textTertiary
    }

    val typeLabel = when (insight.insightType) {
        InsightType.BATTING -> "BAT"
        InsightType.BOWLING -> "BWL"
        InsightType.PARTNERSHIP -> "PTR"
        InsightType.POWERPLAY -> "PP"
        InsightType.DEATH_OVERS -> "DO"
        InsightType.FIELDING -> "FLD"
        InsightType.MOMENTUM -> "MTM"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = typeLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.goldPrimary,
                letterSpacing = 1.sp,
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(trendColor),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = insight.title,
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = insight.value,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = insight.subtext,
            color = trendColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PhaseAnalysisRow(
    currentPhase: String,
    progress: Float,
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .padding(14.dp),
    ) {
        Text(
            text = "MATCH PHASES",
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                "Powerplay" to (currentPhase.contains("Powerplay")),
                "Middle" to (currentPhase.contains("Middle")),
                "Death" to (currentPhase.contains("Death") || currentPhase.contains("Final")),
            ).forEach { (phase, isActive) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) {
                                    Brush.linearGradient(
                                        colors = listOf(colors.goldDark, colors.goldPrimary),
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(colors.glassBorder, colors.glassSurface),
                                    )
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (phase) {
                                "Powerplay" -> "PP"
                                "Middle" -> "MO"
                                "Death" -> "DO"
                                else -> "--"
                            },
                            color = if (isActive) Color.Black else colors.textTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = phase,
                        color = if (isActive) colors.goldLight else colors.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.3f)
                        .background(
                            if (currentPhase.contains("Powerplay")) {
                                colors.goldDark
                            } else if (progress > 0.3f) {
                                colors.statusSuccess.copy(alpha = 0.5f)
                            } else {
                                colors.glassSurface
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.4f)
                        .background(
                            if (currentPhase.contains("Middle")) {
                                colors.goldDark
                            } else if (progress > 0.7f) {
                                colors.statusSuccess.copy(alpha = 0.5f)
                            } else {
                                colors.glassSurface
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.3f)
                        .background(
                            if (currentPhase.contains("Death") || currentPhase.contains("Final")) {
                                colors.goldDark
                            } else {
                                colors.glassSurface
                            },
                        ),
                )
            }
        }
    }
}
