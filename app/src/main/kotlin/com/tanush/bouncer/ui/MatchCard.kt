package com.tanush.bouncer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.*

/**
 * Generate AI insight based on match state (iOS Parity)
 */
private fun generateAiInsight(match: CricketMatch): String? {
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
}

/**
 * Match Card - Clean iOS-Inspired Design
 * Clean, readable match card with proper hierarchy and score display.
 */
@Composable
fun MatchCard(
    match: CricketMatch,
    onClick: () -> Unit = {}
) {
    val isLive = match.isLive
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    // Parse scores with proper fallback
    val scoreInfo1 = parseScore(team1?.score)
    val scoreInfo2 = parseScore(team2?.score)
    val score1Display = scoreInfo1.toDisplayString()
    val score2Display = scoreInfo2.toDisplayString()
    
    // AI Insight for live matches (iOS Parity)
    val aiInsight = remember(match) { generateAiInsight(match) }
    
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
    
    Surface(
        modifier = Modifier
            .padding(horizontal = Spacing.sm, vertical = Spacing.micro)
            .fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = GlassSurface,
        border = BorderStroke(BorderWidth.hairline, Gold400.copy(alpha = 0.12f)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    shape = RoundedCornerShape(50.dp),
                    color = if (isLive) Color(0xFF22C55E).copy(alpha = 0.10f)
                            else statusColor.copy(alpha = 0.10f),
                    border = BorderStroke(
                        BorderWidth.hairline,
                        if (isLive) Color(0xFF22C55E).copy(alpha = 0.20f)
                        else statusColor.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                    .background(Color(0xFF22C55E), CircleShape)
                            )
                        }
                        Text(
                            statusText, 
                            color = if (isLive) Color(0xFF22C55E) else statusColor, 
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Teams Display
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Teams Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TeamRowSimple(team = team1)
                    TeamRowSimple(team = team2)
                }
                
                // Scores Column — bold white scores
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = score1Display, 
                        color = Color.White, 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 20.sp
                    )
                    Text(
                        text = score2Display, 
                        color = if (team2?.isBatting == true) Color.White else Color.White.copy(alpha = 0.6f), 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 20.sp
                    )
                }
            }

            // Status Footer — etched gold gradient text
            if (match.currentStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = match.currentStatus,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                Color(0xFFD4AF37).copy(alpha = 0.7f)
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
                    Color.White.copy(alpha = 0.10f),
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
            color = if (team?.isBatting == true) Color.White else TextSecondary, 
            fontWeight = if (team?.isBatting == true) FontWeight.Bold else FontWeight.Medium, 
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
