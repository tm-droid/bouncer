package com.tanush.bouncer.ui.components.glass

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Compact stat display in a glass pill.
 * Used for inline stats like "SR 142.8", "CRR 6.54", etc.
 *
 * @param label Stat label (e.g., "SR", "CRR", "RRR")
 * @param value Stat value (e.g., "142.8")
 * @param modifier Modifier for the pill
 */
@Composable
fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    
    val shape = RoundedCornerShape(percent = 50)
    
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.glassBackground)
            .border(1.dp, colors.glassBorder, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = colors.textTertiary
        )
        Text(
            text = value,
            style = typography.tabularNumbers,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Glass card displaying a single stat with label, value, and optional delta.
 *
 * @param label Stat label
 * @param value Stat value
 * @param delta Optional change indicator (e.g., "+2.5", "-0.3")
 * @param deltaPositive Whether delta is positive (affects color)
 * @param modifier Modifier for the card
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaPositive: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    GlassCardSmall(
        modifier = modifier,
        borderOpacity = 0.1f
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = typography.labelSmall,
                color = colors.textTertiary
            )
            
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = value,
                    style = typography.statValue,
                    color = colors.textPrimary
                )
                
                if (delta != null) {
                    Text(
                        text = delta,
                        style = typography.labelSmall,
                        color = if (deltaPositive) colors.statusSuccess else colors.statusLive
                    )
                }
            }
        }
    }
}

/**
 * Milestone tracker card showing progress toward a milestone.
 * Displays "Approaching 50" style messages with progress bar.
 *
 * @param playerName Player approaching milestone
 * @param milestoneValue Target milestone (e.g., 50, 100)
 * @param currentValue Current score
 * @param modifier Modifier for the card
 */
@Composable
fun MilestoneTracker(
    playerName: String,
    milestoneValue: Int,
    currentValue: Int,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val progress = (currentValue.toFloat() / milestoneValue).coerceIn(0f, 1f)
    val runsToGo = (milestoneValue - currentValue).coerceAtLeast(0)
    
    // Pulse animation when close to milestone
    val infiniteTransition = rememberInfiniteTransition(label = "milestone")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    GlassCardSmall(
        modifier = modifier,
        useGoldBorder = true,
        borderOpacity = if (runsToGo <= 10) 0.4f * pulseAlpha else 0.25f
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "APPROACHING",
                        style = typography.labelSmall,
                        color = hierarchy.secondary.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Player name and milestone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$playerName $milestoneValue",
                    style = typography.headingSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "$runsToGo to go",
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar
            ProgressBar(
                progress = progress,
                trackColor = Color.Black.copy(alpha = 0.2f),
                progressColor = hierarchy.secondary.primary,
                showGlow = true,
                height = 4.dp
            )
        }
    }
}

/**
 * Current batting partnership display card.
 *
 * @param runs Total partnership runs
 * @param balls Total balls faced
 * @param batter1Name First batter name
 * @param batter1Runs First batter contribution
 * @param batter2Name Second batter name
 * @param batter2Runs Second batter contribution
 * @param modifier Modifier for the card
 */
@Composable
fun PartnershipCard(
    runs: Int,
    balls: Int,
    batter1Name: String,
    batter1Runs: Int,
    batter2Name: String,
    batter2Runs: Int,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    GlassCardSmall(
        modifier = modifier,
        borderOpacity = 0.12f
    ) {
        Column {
            // Header
            Text(
                text = "PARTNERSHIP",
                style = typography.labelSmall,
                color = hierarchy.secondary.primary.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Main partnership display
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = runs.toString(),
                    style = typography.scoreSecondary,
                    color = colors.textPrimary
                )
                Text(
                    text = "($balls balls)",
                    style = typography.labelSmall,
                    color = colors.textMuted
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Contribution bar
            val total = (batter1Runs + batter2Runs).coerceAtLeast(1)
            val batter1Ratio = batter1Runs.toFloat() / total
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(batter1Ratio.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(hierarchy.secondary.primary)
                )
                Box(
                    modifier = Modifier
                        .weight((1f - batter1Ratio).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(colors.statusInfo)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Player contributions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = batter1Name,
                        style = typography.labelSmall,
                        color = hierarchy.secondary.primary
                    )
                    Text(
                        text = "$batter1Runs",
                        style = typography.labelMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = batter2Name,
                        style = typography.labelSmall,
                        color = colors.statusInfo
                    )
                    Text(
                        text = "$batter2Runs",
                        style = typography.labelMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Last wicket information card.
 *
 * @param batterName Name of dismissed batter
 * @param howOut Dismissal description
 * @param runs Runs scored
 * @param balls Balls faced
 * @param partnershipRuns Partnership at time of dismissal
 * @param modifier Modifier for the card
 */
@Composable
fun LastWicketCard(
    batterName: String,
    howOut: String,
    runs: Int,
    balls: Int,
    partnershipRuns: Int? = null,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
   GlassCardSmall(
        modifier = modifier,
        borderOpacity = 0.1f
    ) {
        Column {
            // Header
            Text(
                text = "LAST WICKET",
                style = typography.labelSmall,
                color = colors.textTertiary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Batter info
            Text(
                text = "$batterName $howOut",
                style = typography.headingSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Score details
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$runs ($balls)",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
                
                if (partnershipRuns != null) {
                    Text(
                        text = "•",
                        style = typography.bodySmall,
                        color = colors.textMuted
                    )
                    Text(
                        text = "P'ship: $partnershipRuns",
                        style = typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

/**
 * Glass-themed horizontal progress bar.
 *
 * @param progress Progress value (0.0-1.0)
 * @param modifier Modifier for the bar
 * @param height Height of the bar
 * @param trackColor Background track color
 * @param progressColor Fill color
 * @param showGlow Whether to show glow effect on progress
 * @param teamColor Optional team color for the progress
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = Color.Black.copy(alpha = 0.3f),
    progressColor: Color? = null,
    showGlow: Boolean = true,
    teamColor: Color? = null
) {
    val colors = BouncerDesignSystem.colors
    val fillColor = progressColor ?: teamColor ?: colors.goldPrimary
    val clampedProgress = progress.coerceIn(0f, 1f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(percent = 50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedProgress)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            fillColor.copy(alpha = 0.7f),
                            fillColor
                        )
                    )
                )
                .drawBehind {
                    if (showGlow) {
                        // Edge glow
                        drawCircle(
                            color = fillColor.copy(alpha = 0.5f),
                            radius = size.height * 1.5f,
                            center = Offset(size.width, size.height / 2)
                        )
                    }
                }
        )
    }
}

/**
 * Dual team progress bar (e.g., win probability).
 *
 * @param team1Progress Team 1 progress (0.0-1.0)
 * @param team1Color Team 1 color
 * @param team2Color Team 2 color
 * @param modifier Modifier for the bar
 * @param height Height of the bar
 */
@Composable
fun DualProgressBar(
    team1Progress: Float,
    team1Color: Color,
    team2Color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val clampedProgress = team1Progress.coerceIn(0f, 1f)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
    ) {
        Box(
            modifier = Modifier
                .weight(clampedProgress.coerceAtLeast(0.01f))
                .fillMaxHeight()
                .background(team1Color)
        )
        Box(
            modifier = Modifier
                .weight((1f - clampedProgress).coerceAtLeast(0.01f))
                .fillMaxHeight()
                .background(team2Color)
        )
    }
}

/**
 * Win probability display with team progress bars.
 *
 * @param team1Name Team 1 short name
 * @param team1Probability Team 1 win probability (0-100)
 * @param team1Color Team 1 color
 * @param team2Name Team 2 short name
 * @param team2Color Team 2 color
 * @param modifier Modifier for the component
 */
@Composable
fun WinProbabilityCard(
    team1Name: String,
    team1Probability: Int,
    team1Color: Color,
    team2Name: String,
    team2Color: Color,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val team2Probability = 100 - team1Probability
    
    GlassCardSmall(
        modifier = modifier,
        borderOpacity = 0.1f
    ) {
        Column {
            // Header
            Text(
                text = "WIN PROBABILITY",
                style = typography.labelSmall,
                color = colors.textTertiary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            DualProgressBar(
                team1Progress = team1Probability / 100f,
                team1Color = team1Color,
                team2Color = team2Color,
                height = 8.dp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Team labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = team1Name,
                        style = typography.labelMedium,
                        color = team1Color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$team1Probability%",
                        style = typography.labelMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$team2Probability%",
                        style = typography.labelMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = team2Name,
                        style = typography.labelMedium,
                        color = team2Color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Required run rate comparison card.
 *
 * @param currentRunRate Current run rate
 * @param requiredRunRate Required run rate
 * @param runsNeeded Runs needed to win
 * @param ballsRemaining Balls remaining
 * @param modifier Modifier for the card
 */
@Composable
fun RunRateCard(
    currentRunRate: Float,
    requiredRunRate: Float,
    runsNeeded: Int,
    ballsRemaining: Int,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val isOnTrack = currentRunRate >= requiredRunRate
    
    GlassCardSmall(
        modifier = modifier,
        borderOpacity = 0.1f
    ) {
        Column {
            // Run rates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "CRR",
                        style = typography.labelSmall,
                        color = colors.textTertiary
                    )
                    Text(
                        text = String.format("%.2f", currentRunRate),
                        style = typography.statValue,
                        color = colors.textPrimary
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "RRR",
                        style = typography.labelSmall,
                        color = colors.textTertiary
                    )
                    Text(
                        text = String.format("%.2f", requiredRunRate),
                        style = typography.statValue,
                        color = if (isOnTrack) colors.statusSuccess else colors.statusLive
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Equation
            Text(
                text = "Need $runsNeeded runs in $ballsRemaining balls",
                style = typography.bodySmall,
                color = if (isOnTrack) colors.textSecondary else colors.statusLive.copy(alpha = 0.8f)
            )
        }
    }
}
