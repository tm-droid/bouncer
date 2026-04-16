package com.tanush.bouncer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.core.BouncerCard
import com.tanush.bouncer.ui.components.core.MatchScoreRow
import com.tanush.bouncer.ui.components.core.StatusBadge
import com.tanush.bouncer.ui.components.core.resolveMatchStatusType
import com.tanush.bouncer.ui.components.core.statusColorFor
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
                    rrr > crr + 3 -> "Run rate pressure building"
                    rrr < crr -> "Cruising towards target"
                    else -> "Match evenly poised"
                }
            }
            // Momentum based on recent balls
            recentBoundaries >= 3 -> "Batting momentum high"
            recentWickets >= 2 -> "Bowling on fire"
            // First innings CRR analysis
            crr != null -> {
                when {
                    crr > 10 -> "Explosive scoring pace"
                    crr > 8 -> "Strong run rate"
                    crr < 5 -> "Bowlers dominating"
                    else -> "Steady progression"
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
    val matchPalette = rememberMatchTeamPalette(team1?.name, team2?.name)
    val team1Colors = matchPalette.home
    val team2Colors = matchPalette.away
    val activeTeamColors = when {
        team1?.isBatting == true -> team1Colors
        team2?.isBatting == true -> team2Colors
        else -> team1Colors
    }
    
    // Prefer live AI commentary from ViewModel; fall back to local heuristic
    val aiInsight = liveAiCommentary ?: remember(match) { generateAiInsight(match) }
    
    val statusType = remember(match.status, isLive) { resolveMatchStatusType(match.status, isLive) }
    
    // Press scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    
    BouncerCard(
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
            ) { onClick() }
            .semantics {
                role = Role.Button
                contentDescription = buildString {
                    append(match.matchTitle)
                    if (match.currentStatus.isNotBlank()) {
                        append(". ")
                        append(match.currentStatus)
                    }
                }
            },
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
                            .background(statusColorFor(statusType))
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
                
                StatusBadge(statusType = statusType)
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                MatchScoreRow(
                    team = team1,
                    score = score1Display,
                    teamTint = team1Colors.primary,
                    emphasized = team1?.isBatting == true
                )
                MatchScoreRow(
                    team = team2,
                    score = score2Display,
                    teamTint = team2Colors.primary,
                    emphasized = team2?.isBatting == true
                )
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
                                activeTeamColors.onSurface.copy(alpha = 0.92f),
                                activeTeamColors.primary.copy(alpha = 0.76f)
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
                    color = activeTeamColors.accent,
                    border = BorderStroke(BorderWidth.hairline, activeTeamColors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.micro),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.micro)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Insight",
                            tint = activeTeamColors.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = aiInsight,
                            color = activeTeamColors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
