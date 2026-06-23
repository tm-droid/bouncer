package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * PowerPlay Analysis Card
 *
 * TODO: Integration Requirements:
 * - Track powerplay phases in real-time (PP1, PP2, PP3)
 * - Calculate phase-specific run rates and stats
 * - Compare with average powerplay scores at venue
 * - Show boundary percentage per phase
 * - Add historical powerplay comparison
 * - Highlight aggressive vs conservative approach
 * - Track wickets lost per phase
 * - Add phase-specific bowling analysis
 * - Navigate to detailed over-by-over view
 * - Cache powerplay stats for match review
 */

data class PowerPlayPhase(
    val name: String,
    val overs: String,
    val runs: Int,
    val wickets: Int,
    val runRate: Double,
    val boundaries: Int,
    val sixes: Int,
    val dotBalls: Int,
    val isActive: Boolean = false,
)

@Composable
fun PowerPlayAnalysisCard(
    teamName: String,
    phases: List<PowerPlayPhase>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "powerplay")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(phases) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
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
                                    colors = listOf(colors.statusInfo, colors.statusInfo.copy(alpha = 0.6f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "POWERPLAY ANALYSIS",
                            color = hierarchy.secondary.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = teamName,
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Phase breakdown
            phases.forEachIndexed { index, phase ->
                PowerPlayPhaseRow(
                    phase = phase,
                    progress = animatedProgress.value,
                    maxRuns = phases.maxOf { it.runs },
                    pulseAlpha = if (phase.isActive) pulseAlpha else 0f,
                )

                if (index < phases.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capture colors for Canvas scope
            val goldDark = hierarchy.secondary.primary
            val goldPrimary = hierarchy.secondary.primary
            val statusInfoColor = colors.statusInfo

            // Run rate comparison graph
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            ) {
                val maxRate = phases.maxOf { it.runRate }.toFloat().coerceAtLeast(1f)
                val barWidth = size.width / (phases.size * 2 + 1)

                phases.forEachIndexed { index, phase ->
                    val barHeight = (phase.runRate.toFloat() / maxRate * size.height * 0.8f * animatedProgress.value)
                    val x = barWidth + index * barWidth * 2

                    // Glow effect
                    if (phase.isActive) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    statusInfoColor.copy(alpha = 0.4f),
                                    Color.Transparent,
                                ),
                                center = Offset(x + barWidth / 2, size.height - barHeight / 2),
                                radius = barHeight,
                            ),
                            topLeft = Offset(x - 20f, size.height - barHeight - 20f),
                            size = Size(barWidth + 40f, barHeight + 40f),
                        )
                    }

                    // Bar
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = if (phase.isActive) {
                                listOf(goldDark, goldPrimary)
                            } else {
                                listOf(statusInfoColor, statusInfoColor.copy(alpha = 0.6f))
                            },
                        ),
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                    )

                    // Rate label
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 4f,
                        center = Offset(x + barWidth / 2, size.height - barHeight - 10f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phase labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                phases.forEach { phase ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = phase.name,
                            color = if (phase.isActive) hierarchy.secondary.primary else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (phase.isActive) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = phase.overs,
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerPlayPhaseRow(
    phase: PowerPlayPhase,
    progress: Float,
    maxRuns: Int,
    pulseAlpha: Float,
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (phase.isActive) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = pulseAlpha * 0.3f),
                            Color.Transparent,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.glassSurface,
                            Color.Transparent,
                        ),
                    )
                },
            )
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Phase info
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (phase.isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(hierarchy.secondary.primary),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = phase.name,
                        color = if (phase.isActive) hierarchy.secondary.primary else colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = phase.overs,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
            }

            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBubble(
                    value = "${phase.runs}/${phase.wickets}",
                    label = "Score",
                    isHighlighted = phase.isActive,
                )

                StatBubble(
                    value = String.format("%.2f", phase.runRate),
                    label = "RR",
                    isHighlighted = phase.runRate > 8.0,
                )

                StatBubble(
                    value = "${phase.boundaries}/${phase.sixes}",
                    label = "4s/6s",
                    isHighlighted = (phase.boundaries + phase.sixes) > 5,
                )
            }
        }
    }
}

@Composable
private fun StatBubble(
    value: String,
    label: String,
    isHighlighted: Boolean,
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (isHighlighted) hierarchy.secondary.primary else colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 10.sp,
        )
    }
}
