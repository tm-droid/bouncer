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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.theme.*
import io.sentry.Sentry

/**
 * Result of a recent match (for form tracking)
 */
enum class FormResult {
    WIN,
    LOSS,
    DRAW,
    NO_RESULT
}

/**
 * A recent match entry for form display
 */
data class RecentMatchEntry(
    val id: String,
    val opponent: String,
    val opponentLogo: String? = null,
    val result: FormResult,
    val margin: String,
    val date: String,
    val venue: String,
    val format: String,
    val teamScore: String,
    val opponentScore: String
)

private fun getFormColor(result: FormResult): Color {
    try {
        return when (result) {
            FormResult.WIN -> StatusSuccess
            FormResult.LOSS -> StatusLive
            else -> TextMuted
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

private fun getFormText(result: FormResult): String {
    try {
        return when (result) {
            FormResult.WIN -> "W"
            FormResult.LOSS -> "L"
            FormResult.DRAW -> "D"
            FormResult.NO_RESULT -> "NR"
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * MEGA UI UPGRADE: TeamFormCard (v2.0).
 * Technical team dossier with neon status mapping and broadcast aesthetics.
 */
@Composable
fun TeamFormCard(
    teamName: String,
    teamLogo: String?,
    recentMatches: List<RecentMatchEntry>,
    teamColor: Color = GoldDark,
    modifier: Modifier = Modifier,
    onMatchClick: ((RecentMatchEntry) -> Unit)? = null
) {
    val winRate = if (recentMatches.isNotEmpty()) {
        (recentMatches.count { it.result == FormResult.WIN }.toFloat() / recentMatches.size * 100).toInt()
    } else 0
    
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.08f).copy(alpha = 0.8f))
            .border(
                0.5.dp,
                Brush.linearGradient(listOf(colors.goldPrimary.copy(alpha = 0.3f), colors.glassSurface)),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Header: Identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, teamColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CoilImage(
                            model = teamLogo ?: TeamLogoManager.getLogoUrl(teamName),
                            contentDescription = teamName,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    Column {
                        Text(
                            text = teamName.uppercase(),
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "HISTORICAL FORM ANALYSIS",
                            color = colors.goldDark,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(teamColor.copy(alpha = 0.1f))
                        .border(0.5.dp, teamColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("$winRate% WIN RATE", color = teamColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            
            // Neon Form Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RECENT:", color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                recentMatches.take(8).forEach { match ->
                    TechnicalFormDot(match.result)
                }
            }
            
            // Historical Feed
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("RECENT ENGAGEMENTS", color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recentMatches) { match ->
                        TechnicalRecentMatchItem(match, onMatchClick)
                    }
                }
            }
            
            // Analytic Summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FormStatMini("WON", recentMatches.count { it.result == FormResult.WIN }.toString(), colors.statusSuccess)
                    VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = Color.White)
                    FormStatMini("LOST", recentMatches.count { it.result == FormResult.LOSS }.toString(), colors.statusLive)
                    VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = Color.White)
                    FormStatMini("N/R", recentMatches.count { it.result == FormResult.NO_RESULT }.toString(), colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun TechnicalFormDot(result: FormResult) {
    val color = getFormColor(result)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(getFormText(result), color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TechnicalRecentMatchItem(match: RecentMatchEntry, onClick: ((RecentMatchEntry) -> Unit)?) {
    val colors = BouncerDesignSystem.colors
    val color = getFormColor(match.result)
    Box(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glassSurface)
            .border(0.5.dp, colors.glassSurface, RoundedCornerShape(16.dp))
            .clickable { onClick?.invoke(match) }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(match.format.uppercase(), color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(
                    text = if (match.result == FormResult.WIN) "WIN" else "LOSS",
                    color = color,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
            
            Text(
                text = "vs ${match.opponent.uppercase()}",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Column {
                Text("${match.teamScore} - ${match.opponentScore}", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(match.margin, color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FormStatMini(label: String, value: String, color: Color) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
