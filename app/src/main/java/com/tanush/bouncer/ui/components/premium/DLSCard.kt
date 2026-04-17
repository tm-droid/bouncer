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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.CardDefaults.cardElevation
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * DLS (Duckworth-Lewis-Stern) Card
 *
 * TODO: Integration Requirements:
 * - Implement official DLS calculation algorithm
 * - Fetch resource tables from ICC specifications
 * - Calculate par scores at each over during chase
 * - Show revised target scenarios for further delays
 * - Add real-time updates when rain interruptions occur
 * - Display resource percentage remaining
 * - Compare DLS target vs actual match situation
 * - Add historical DLS-affected match outcomes
 * - Show fairness analysis of revised target
 * - Cache DLS tables for offline calculation
 */

data class DLSInfo(
    val targetRevised: Int,
    val originalTarget: Int,
    val par1Score: Int,
    val par2Score: Int,
    val resourcesRemaining: Float,
    val isSecondInningsAffected: Boolean,
    val oversReduced: Float,
    val originalOvers: Float,
)

@Composable
fun DLSCard(
    dlsInfo: DLSInfo,
    currentScore: Int,
    currentWickets: Int,
    currentOvers: Float,
    chaseTeam: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "dls")

    val resourcePulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(dlsInfo) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    val runsNeeded = dlsInfo.targetRevised - currentScore
    val isAbovePar = currentScore >= dlsInfo.par1Score

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Capture color for Canvas scope
            val statusInfoColor = colors.statusInfo
            
            // Rain effect background
            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val centerX = size.width / 2

                // Rain drops animation placeholder
                for (i in 0..15) {
                    val x = (i * 30f) % size.width
                    val y = (i * 20f + resourcePulse * 100) % size.height

                    drawCircle(
                        color = statusInfoColor.copy(alpha = 0.1f),
                        radius = 3f,
                        center = Offset(x, y),
                    )
                }
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
                                        colors = listOf(colors.statusInfo, Color(0xFF26C6DA)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "DLS METHOD",
                                color = colors.statusInfo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = "Revised Target",
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Revised target
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${dlsInfo.targetRevised}",
                            color = hierarchy.secondary.primary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "from ${dlsInfo.originalTarget}",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Par scores banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isAbovePar) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.statusSuccess.copy(alpha = 0.2f),
                                        colors.statusSuccess.copy(alpha = 0.1f),
                                    ),
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.statusLive.copy(alpha = 0.2f),
                                        colors.statusLive.copy(alpha = 0.1f),
                                    ),
                                )
                            },
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "PAR SCORE NOW",
                            color = if (isAbovePar) colors.statusSuccess else colors.statusLive,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${dlsInfo.par1Score}",
                                color = colors.textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = " / ${dlsInfo.par2Score}",
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }

                    // Current position
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isAbovePar) "AHEAD" else "BEHIND",
                            color = if (isAbovePar) colors.statusSuccess else colors.statusLive,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "by ${kotlin.math.abs(currentScore - dlsInfo.par1Score)} runs",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }

                    // Current score
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$currentScore/$currentWickets",
                            color = if (isAbovePar) colors.statusSuccess else colors.statusLive,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "$currentOvers overs",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resources remaining
                Text(
                    text = "RESOURCES",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.glassBorder),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = dlsInfo.resourcesRemaining * animatedProgress.value)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(colors.statusInfo, hierarchy.secondary.primary),
                                ),
                            ),
                    )

                    // Percentage text
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${(dlsInfo.resourcesRemaining * 100).toInt()}% remaining",
                            color = colors.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "${((1 - dlsInfo.resourcesRemaining) * 100).toInt()}% used",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Match info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.glassSurface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DLSStatItem(
                        label = "Overs Now",
                        value = "${dlsInfo.originalOvers - dlsInfo.oversReduced}",
                        color = colors.statusInfo,
                    )

                    DLSStatItem(
                        label = "Reduced",
                        value = "${dlsInfo.oversReduced}",
                        color = colors.statusLive,
                    )

                    DLSStatItem(
                        label = "Need",
                        value = "$runsNeeded",
                        color = hierarchy.secondary.primary,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status message
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.statusInfo.copy(alpha = 0.1f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "🌧️ ",
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "$chaseTeam need $runsNeeded runs from ${dlsInfo.originalOvers - dlsInfo.oversReduced - currentOvers} overs",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DLSStatItem(
    label: String,
    value: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 10.sp,
        )
    }
}
