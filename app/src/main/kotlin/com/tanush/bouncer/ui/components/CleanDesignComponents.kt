package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.*
import io.sentry.Sentry

/**
 * Clean Design Components - iOS-Inspired UI System
 * 
 * Features:
 * - Refined typography with better hierarchy
 * - Cleaner cards with proper breathing room
 * - Subtle animations without overwhelming effects
 * - Professional gold accents used sparingly
 */

// Note: SectionHeader is defined in EliteUIComponents.kt

// ============================================================================
// HERO LIVE MATCH CARD - iOS-Inspired
// ============================================================================

@Composable
private fun TeamScoreDisplay(
    team: CricketMatch.Team?,
    alignment: Alignment.Horizontal
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Team Logo
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(GlassSurface)
                .border(
                    width = if (team?.isBatting == true) 2.dp else 0.5.dp,
                    color = if (team?.isBatting == true) hierarchy.secondary.primary else GlassBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 112, 112)
            CoilImage(
                model = logoUrl,
                contentDescription = team?.name,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        // Team Name
        Text(
            text = team?.acronym ?: "TBD",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Score
        Text(
            text = team?.score?.ifEmpty { "-" } ?: "-",
            color = if (team?.isBatting == true) hierarchy.secondary.primary else colors.textSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun getMatchStatusInfo(match: CricketMatch): Pair<String, Color> {
    try {
        val status = match.status.lowercase()
        return when {
            match.isLive -> "Live" to StatusSuccess
            status.contains("completed") || status.contains("won") || status.contains("result") -> "Completed" to ChartCyan
            status.contains("upcoming") || status.contains("preview") -> "Upcoming" to StatusInfo
            status.contains("delayed") || status.contains("postponed") -> "Delayed" to StatusWarning
            status.contains("break") || status.contains("lunch") || status.contains("tea") || status.contains("stumps") -> "Break" to StatusWarning
            status.contains("abandoned") -> "Abandoned" to StatusLive
            else -> "Scheduled" to TextSecondary
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

// ============================================================================
// MATCH CAROUSEL CARD - For horizontal scroll
// ============================================================================

@Composable
private fun TeamScoreRow(
    team: CricketMatch.Team?,
    isBatting: Boolean
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 32dp circular logo
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            CoilImage(
                model = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 64, 64),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.space2))

        // Team name — h4, max 140dp
        Text(
            text = team?.name ?: "TBD",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBatting) FontWeight.Bold else FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 140.dp)
        )

        // Score — mono-lg, right aligned
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = team?.score?.ifEmpty { "-" } ?: "-",
                color = if (isBatting) hierarchy.secondary.primary else colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            if (!team?.overs.isNullOrEmpty()) {
                Text(
                    text = "(${team?.overs} ov)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun BallByBallRow(balls: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        balls.forEach { ball ->
            val (bgColor, textColor) = when {
                ball.equals("W", ignoreCase = true) -> StatusWicket to Color.White
                ball == "4" -> StatusBoundary to Color.White
                ball == "6" -> StatusSix to Color.White
                ball.contains("wd", ignoreCase = true) || ball.contains("nb", ignoreCase = true) ->
                    StatusWarning.copy(alpha = 0.2f) to StatusWarning
                ball == "0" -> Color.Transparent to TextTertiary
                else -> Surface3 to TextSecondary
            }

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .then(
                        if (ball == "0") Modifier.border(1.dp, Surface3, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ball,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun MiniProbabilityBar(
    team1Prob: Float,
    team2Prob: Float,
    team1Color: Color,
    team2Color: Color
) {
    val total = team1Prob + team2Prob
    if (total <= 0) return
    val fraction1 = team1Prob / total

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // 4dp bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(fraction1)
                    .fillMaxHeight()
                    .background(team1Color)
            )
            Box(
                modifier = Modifier
                    .weight(1f - fraction1)
                    .fillMaxHeight()
                    .background(team2Color)
            )
        }

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${team1Prob.toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = team1Color
            )
            Text(
                text = "${team2Prob.toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = team2Color
            )
        }
    }
}

// ============================================================================
// STATUS FILTER CHIPS
// ============================================================================
