package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*
import io.sentry.Sentry

/**
 * Power play phase data
 */
data class PowerPlayPhase(
    val phaseName: String,
    val phaseNumber: Int,
    val startOver: Int,
    val endOver: Int,
    val runs: Int,
    val wickets: Int,
    val balls: Int,
    val boundaries: Int,
    val sixes: Int,
    val dots: Int
) {
    val runRate: Float
        get() = if (balls > 0) runs.toFloat() / (balls.toFloat() / 6f) else 0f
    
    val boundaryPercentage: Float
        get() = if (runs > 0) ((boundaries * 4 + sixes * 6).toFloat() / runs) * 100f else 0f
}

/**
 * Premium Power Play Stats Card
 * Shows detailed breakdown of scoring in different phases
 */
@Composable
fun PowerPlayStatsCard(
    phases: List<PowerPlayPhase>,
    teamName: String,
    teamColor: Color = GoldPrimary,
    matchFormat: String = "T20",
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "powerplay")
    
    // Animated glow for current phase
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = teamColor.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Animated lightning icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        teamColor.copy(alpha = glowAlpha),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = teamColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            "POWER PLAY STATS",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = matchFormat,
                            color = colors.textTertiary,
                            fontSize = 10.sp
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = teamColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, teamColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = teamName,
                        color = teamColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Phase tabs/cards
            phases.forEachIndexed { index, phase ->
                PowerPlayPhaseCard(
                    phase = phase,
                    teamColor = teamColor,
                    phaseColors = getPhaseColors(index)
                )
                if (index < phases.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Overall summary
            PowerPlaySummary(phases = phases, teamColor = teamColor)
        }
    }
}

/**
 * Get colors for different phases
 */
private fun getPhaseColors(index: Int): Pair<Color, Color> {
    try {
        return when (index) {
            0 -> StatusInfo to StatusInfo.copy(alpha = 0.1f)
            1 -> GoldPrimary to GoldPrimary.copy(alpha = 0.1f)
            2 -> StatusSuccess to StatusSuccess.copy(alpha = 0.1f)
            else -> TextPrimary to GlassBorderHighlight
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Individual power play phase card
 */
@Composable
private fun PowerPlayPhaseCard(
    phase: PowerPlayPhase,
    teamColor: Color,
    phaseColors: Pair<Color, Color>
) {
    val colors = BouncerDesignSystem.colors
    val (accentColor, backgroundColor) = phaseColors
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Phase header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "P${phase.phaseNumber}",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Column {
                        Text(
                            text = phase.phaseName,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Overs ${phase.startOver}-${phase.endOver}",
                            color = colors.textTertiary,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Score
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${phase.runs}",
                        color = accentColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "/${phase.wickets}",
                        color = colors.textSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PhaseStatItem(
                    value = String.format("%.2f", phase.runRate),
                    label = "Run Rate",
                    color = if (phase.runRate >= 8f) colors.statusSuccess else if (phase.runRate >= 6f) accentColor else colors.textSecondary
                )
                PhaseStatItem(
                    value = "${phase.boundaries}",
                    label = "Fours",
                    color = colors.statusInfo
                )
                PhaseStatItem(
                    value = "${phase.sixes}",
                    label = "Sixes",
                    color = colors.goldPrimary
                )
                PhaseStatItem(
                    value = "${phase.dots}",
                    label = "Dots",
                    color = colors.textTertiary
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Boundary percentage bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Boundary %",
                        color = colors.textTertiary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${String.format("%.1f", phase.boundaryPercentage)}%",
                        color = if (phase.boundaryPercentage >= 50f) colors.goldPrimary else accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.glassBorder.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (phase.boundaryPercentage / 100f).coerceIn(0f, 1f))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(accentColor, colors.goldPrimary)
                                ),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Phase stat item
 */
@Composable
private fun PhaseStatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 9.sp
        )
    }
}

/**
 * Power play summary
 */
@Composable
private fun PowerPlaySummary(phases: List<PowerPlayPhase>, teamColor: Color) {
    val colors = BouncerDesignSystem.colors
    val totalRuns = phases.sumOf { it.runs }
    val totalWickets = phases.sumOf { it.wickets }
    val totalBalls = phases.sumOf { it.balls }
    val totalBoundaries = phases.sumOf { it.boundaries }
    val totalSixes = phases.sumOf { it.sixes }
    val overallRunRate = if (totalBalls > 0) totalRuns.toFloat() / (totalBalls.toFloat() / 6f) else 0f
    
    val bestPhase = phases.maxByOrNull { it.runRate }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.glassBorderHighlight,
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "OVERALL SUMMARY",
                color = colors.textTertiary,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalRuns/$totalWickets",
                        color = teamColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Total Score",
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.2f", overallRunRate),
                        color = colors.statusInfo,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Run Rate",
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${totalBoundaries + totalSixes}",
                        color = colors.statusInfo,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Boundaries",
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            
            bestPhase?.let { phase ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = colors.statusSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Best Phase: ${phase.phaseName} (${String.format("%.2f", phase.runRate)} RPO)",
                        color = colors.statusSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Mini power play indicator
 */
@Composable
fun MiniPowerPlayIndicator(
    currentOver: Float,
    powerPlayOvers: List<Pair<Int, Int>>,
    teamColor: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    val isInPowerPlay = powerPlayOvers.any { (start, end) -> 
        currentOver >= start && currentOver <= end 
    }
    
    AnimatedVisibility(
        visible = isInPowerPlay,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = teamColor.copy(alpha = 0.15f),
            border = BorderStroke(0.5.dp, teamColor.copy(alpha = 0.5f)),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = teamColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "POWER PLAY",
                    color = teamColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Compact phase comparison bar
 */
@Composable
fun PhaseComparisonBar(
    phases: List<PowerPlayPhase>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(12.dp)
) {
    val totalRuns = phases.sumOf { it.runs }.coerceAtLeast(1)
    
    Row(
        modifier = modifier.clip(RoundedCornerShape(6.dp))
    ) {
        phases.forEachIndexed { index, phase ->
            val fraction = phase.runs.toFloat() / totalRuns
            val color = when (index) {
                0 -> StatusInfo
                1 -> GoldPrimary
                2 -> StatusSuccess
                else -> TextTertiary
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(fraction.coerceAtLeast(0.01f))
                    .background(color)
            )
        }
    }
}
