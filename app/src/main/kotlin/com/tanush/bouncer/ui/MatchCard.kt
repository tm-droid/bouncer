package com.tanush.bouncer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.*
import io.sentry.Sentry

/**
 * Generate AI insight based on match state (iOS Parity)
 */
private fun generateAiInsight(match: CricketMatch): String? {
    try {
        if (!match.isLive) return null
    
        val team1 = match.teams.firstOrNull()
        val team2 = match.teams.getOrNull(1)
        val score = match.score
    
        // Parse current run rate
        val crr = score?.currentRunRate?.toFloatOrNull()
        val rrr = score?.requiredRunRate?.toFloatOrNull()
    
        // Extract recent balls for momentum analysis
        val recentBalls = match.recentBalls ?: emptyList()
        val recentBoundaries = recentBalls.count { it == "4" || it == "6" }
        val recentWickets = recentBalls.count { it.lowercase() == "w" }
    
        return when {
            // Chase situation - RRR analysis
            rrr != null && crr != null -> {
                when {
                    rrr > crr + 3 -> "🔥 Run rate pressure building"
                    rrr < crr -> "✨ Cruising towards target"
                    else -> "⚖️ Match evenly poised"
                }
            }
            // Momentum based on recent balls
            recentBoundaries >= 3 -> "🚀 Batting momentum high"
            recentWickets >= 2 -> "⚡ Bowling on fire"
            // First innings CRR analysis
            crr != null -> {
                when {
                    crr > 10 -> "💥 Explosive scoring pace"
                    crr > 8 -> "🔥 Strong run rate"
                    crr < 5 -> "🎯 Bowlers dominating"
                    else -> "📊 Steady progression"
                }
            }
            // Default for live matches
            else -> null
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Match Card - Clean iOS-Inspired Design
 * Clean, readable match card with proper hierarchy and score display.
 * @param liveAiCommentary Optional AI commentary from the ViewModel that overrides
 *                         the built-in heuristic insight when available.
 */
@Composable
fun MatchCard(
    match: CricketMatch,
    onClick: () -> Unit = {},
    liveAiCommentary: String? = null
) {
    val isLive = match.isLive
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    // Parse scores with proper fallback
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    val score1Display = scoreInfo1.toDisplayString()
    val score2Display = scoreInfo2.toDisplayString()
    
    // Prefer live AI commentary from ViewModel; fall back to local heuristic
    val aiInsight = liveAiCommentary ?: remember(match) { generateAiInsight(match) }
    
    val statusColor = when {
        isLive -> SuccessGreen
        match.status.lowercase().contains("completed") || match.status.lowercase().contains("won") -> ChartCyan
        match.status.lowercase().contains("upcoming") -> LiveBlue
        match.status.lowercase().contains("delayed") -> WarningOrange
        else -> TextSecondary
    }
    
    val statusText = when {
        isLive -> "Live"
        match.status.lowercase().contains("completed") || match.status.lowercase().contains("won") -> "Completed"
        match.status.lowercase().contains("upcoming") -> "Upcoming"
        match.status.lowercase().contains("delayed") -> "Delayed"
        else -> "Scheduled"
    }
    
    // Press scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    
    GlassCard(
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        importance = if (isLive) BouncerImportance.CRITICAL else BouncerImportance.STANDARD,
        cornerRadius = CornerRadius.lg
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 14.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(statusColor)
                    )
                    Text(
                        match.matchTitle, 
                        color = TextSecondary, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Status Badge — glass pill with pulsing dot for live
                Surface(
                    shape = RoundedCornerShape(CornerRadius.pill),
                    color = if (isLive) SuccessGreen.copy(alpha = 0.10f)
                            else statusColor.copy(alpha = 0.10f),
                    border = BorderStroke(
                        BorderWidth.hairline,
                        if (isLive) SuccessGreen.copy(alpha = 0.20f)
                        else statusColor.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        if (isLive) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f, 
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    tween(800, easing = LinearEasing), 
                                    RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .graphicsLayer { this.alpha = alpha }
                                    .background(SuccessGreen, CircleShape)
                            )
                        }
                        Text(
                            statusText, 
                            color = if (isLive) SuccessGreen else statusColor, 
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Teams Display
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Teams Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TeamRowSimple(team = team1)
                    TeamRowSimple(team = team2)
                }
                
                // Scores Column — bold gold scores
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = score1Display, 
                        color = if (team1?.isBatting == true) GoldPrimary else TextSecondary, 
                        fontWeight = FontWeight.Black, 
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = score2Display, 
                        color = if (team2?.isBatting == true) GoldPrimary else TextSecondary, 
                        fontWeight = FontWeight.Black, 
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            // Status Footer — etched gold gradient text
            if (match.currentStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = match.currentStatus,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TextPrimary.copy(alpha = 0.9f),
                                GoldPrimary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // AI Insight Preview (iOS Parity)
            if (aiInsight != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Surface(
                    shape = RoundedCornerShape(CornerRadius.xs),
                    color = ShinyGoldDeep.copy(alpha = 0.08f),
                    border = BorderStroke(BorderWidth.hairline, ShinyGoldDeep.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.micro),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.micro)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Insight",
                            tint = ShinyGoldDeep,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = aiInsight,
                            color = ShinyGoldDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamRowSimple(team: CricketMatch.Team?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Team Logo — glass circle with subtle border
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GlassSurface)
                .border(
                    BorderWidth.hairline,
                    GlassBorderHighlight,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 72, 72)
            CoilImage(
                model = logoUrl, 
                contentDescription = null, 
                modifier = Modifier.size(26.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        // Team Name
        Text(
            text = team?.name ?: "TBD", 
            color = if (team?.isBatting == true) TextPrimary else TextSecondary, 
            fontWeight = if (team?.isBatting == true) FontWeight.Bold else FontWeight.Medium, 
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
