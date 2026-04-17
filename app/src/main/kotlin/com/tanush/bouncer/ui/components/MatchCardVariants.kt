package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback
import io.sentry.Sentry
import com.tanush.bouncer.ui.theme.GoldPrimary

@Composable
fun CompactMatchCard(
    match: CricketMatch,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    val statusInfo = getCompactMatchStatus(match.status)
    val isLive = statusInfo == "LIVE"
    
    val infiniteTransition = rememberInfiniteTransition(label = "compactLive")
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "compactLivePulse"
    )
    
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.micro)
            .shadow(
                elevation = if (isLive) Elevation.high else Elevation.medium,
                shape = RoundedCornerShape(CornerRadius.md),
                spotColor = if (isLive) colors.statusInfo.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(CornerRadius.md),
        color = colors.glassSurface,
        border = androidx.compose.foundation.BorderStroke(
            BorderWidth.hairline, 
            if (isLive) colors.statusInfo.copy(alpha = 0.3f) else colors.glassBorder.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .clickable {
                    haptic.click()
                    onClick()
                }
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Team 1
            CompactTeamInfo(team = team1, modifier = Modifier.weight(1.2f))
            
            // Center Status
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = if (isLive) colors.statusInfo.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isLive) androidx.compose.foundation.BorderStroke(1.dp, colors.statusInfo.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .graphicsLayer { alpha = livePulse }
                                    .background(colors.statusLive, CircleShape)
                            )
                        }
                        Text(
                            text = statusInfo,
                            color = if (isLive) colors.textPrimary else colors.textTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "VS",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
            
            // Team 2
            CompactTeamInfo(team = team2, modifier = Modifier.weight(1.2f), isRight = true)
        }
    }
}

@Composable
private fun CompactTeamInfo(
    team: CricketMatch.Team?,
    modifier: Modifier = Modifier,
    isRight: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 128, 128)
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start
    ) {
        if (!isRight) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.glassBorderHighlight, CircleShape)
                    .border(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.2f), CircleShape)
                    .padding(6.dp)
            ) {
                CoilImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        Column(horizontalAlignment = if (isRight) Alignment.End else Alignment.Start) {
            Text(
                text = team?.acronym ?: team?.name?.take(3)?.uppercase() ?: "TBD",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = team?.score?.substringBefore("(")?.trim()?.takeIf { it.isNotEmpty() && it != "Data Not Found" } ?: "—",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        if (isRight) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.glassBorderHighlight, CircleShape)
                    .border(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.2f), CircleShape)
                    .padding(6.dp)
            ) {
                CoilImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun ExpandableMatchCard(
    match: CricketMatch,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val haptic = rememberHapticFeedback()
    
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "expandRotation"
    )
    
    Surface(
        modifier = modifier
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .shadow(
                elevation = if (isExpanded) Elevation.high else Elevation.medium,
                shape = RoundedCornerShape(CornerRadius.md),
                spotColor = Color.Black.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(CornerRadius.md),
        color = colors.glassSurface,
        border = androidx.compose.foundation.BorderStroke(BorderWidth.hairline, colors.glassBorder.copy(alpha = 0.3f))
    ) {
        Column {
            // Header/Main part (always visible)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.click()
                        onClick()
                    }
                    .padding(top = 4.dp)
            ) {
                // We use our improved MatchCard here but stripped down if needed, 
                // but for simplicity let's just use the Enhanced version
                EnhancedMatchCard(
                    match = match,
                    onClick = { onClick() },
                    elevation = 0.dp,
                    modifier = Modifier.padding(bottom = 0.dp)
                )
            }
            
            // Expand toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.click()
                        isExpanded = !isExpanded
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isExpanded) "LESS INFO" else "MORE INFO",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation)
                    )
                }
            }
            
            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quick Stats/Info Grid
                    Row(modifier = Modifier.fillMaxWidth()) {
                        InfoBox(label = "FORMAT", value = match.matchType, modifier = Modifier.weight(1f))
                        InfoBox(label = "DATE", value = match.date, modifier = Modifier.weight(1f))
                    }
                    
                    InfoBox(
                        label = "VENUE", 
                        value = match.venue.takeIf { it.isNotEmpty() } ?: "TBD",
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (match.currentStatus.isNotEmpty()) {
                        Surface(
                            color = GoldPrimary.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "LIVE SUMMARY",
                                    color = GoldPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = match.currentStatus,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = BouncerDesignSystem.colors
    Column(modifier = modifier) {
        Text(
            text = label,
            color = colors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun getCompactMatchStatus(status: String): String {
    try {
        val s = status.lowercase()
        return when {
            s.contains("won") || s.contains("complete") || s.contains("result") -> "DONE"
            s.contains("delayed") || s.contains("rain") -> "DELAY"
            s.contains("abandoned") -> "ABD"
            s.contains("break") || s.contains("tea") || s.contains("lunch") -> "BREAK"
            s.contains("upcoming") || s.contains("starts") || status.isEmpty() -> "SOON"
            else -> "LIVE"
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
