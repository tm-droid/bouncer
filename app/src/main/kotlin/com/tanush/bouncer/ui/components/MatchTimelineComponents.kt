package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Premium timeline for match events with animated connectors
 */
@Composable
fun MatchEventTimeline(
    events: List<TimelineEvent>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "timeline")
    val pulseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
            TimelineItem(
                event = event,
                isFirst = index == 0,
                isLast = index == events.lastIndex,
                pulseOffset = pulseOffset
            )
        }
    }
}

data class TimelineEvent(
    val id: String,
    val type: EventType,
    val title: String,
    val description: String,
    val timestamp: String,
    val over: String? = null,
    val playerName: String? = null,
    val runs: Int? = null,
    val teamLogo: String? = null
) {
    enum class EventType {
        WICKET, SIX, FOUR, FIFTY, HUNDRED, HAT_TRICK, 
        INNINGS_START, INNINGS_END, MATCH_START, MATCH_END,
        MILESTONE, OTHER
    }
}

@Composable
private fun TimelineItem(
    event: TimelineEvent,
    isFirst: Boolean,
    isLast: Boolean,
    pulseOffset: Float
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val eventColor = when (event.type) {
        TimelineEvent.EventType.WICKET -> colors.statusLive
        TimelineEvent.EventType.SIX -> colors.statusWarning
        TimelineEvent.EventType.FOUR -> colors.statusInfo
        TimelineEvent.EventType.FIFTY, TimelineEvent.EventType.HUNDRED -> hierarchy.secondary.primary
        TimelineEvent.EventType.HAT_TRICK -> colors.statusInfo
        TimelineEvent.EventType.INNINGS_START, TimelineEvent.EventType.INNINGS_END -> colors.statusInfo
        TimelineEvent.EventType.MATCH_START, TimelineEvent.EventType.MATCH_END -> colors.statusSuccess
        TimelineEvent.EventType.MILESTONE -> hierarchy.secondary.primary
        TimelineEvent.EventType.OTHER -> colors.textTertiary
    }
    
    val eventIcon = when (event.type) {
        TimelineEvent.EventType.WICKET -> Icons.Default.SportsCricket
        TimelineEvent.EventType.SIX -> Icons.Default.Whatshot
        TimelineEvent.EventType.FOUR -> Icons.Default.FlashOn
        TimelineEvent.EventType.FIFTY, TimelineEvent.EventType.HUNDRED -> Icons.Default.Star
        TimelineEvent.EventType.HAT_TRICK -> Icons.Default.AutoAwesome
        TimelineEvent.EventType.INNINGS_START -> Icons.Default.PlayArrow
        TimelineEvent.EventType.INNINGS_END -> Icons.Default.Stop
        TimelineEvent.EventType.MATCH_START -> Icons.Default.Flag
        TimelineEvent.EventType.MATCH_END -> Icons.Default.EmojiEvents
        TimelineEvent.EventType.MILESTONE -> Icons.Default.Celebration
        TimelineEvent.EventType.OTHER -> Icons.Default.FiberManualRecord
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timeline connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Top connector line
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors.glassBorder,
                                    eventColor.copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }
            
            // Event node
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .drawBehind {
                        // Glow effect
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    eventColor.copy(alpha = 0.3f + pulseOffset * 0.2f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension * 0.8f
                        )
                    }
                    .background(eventColor.copy(alpha = 0.15f), CircleShape)
                    .border(
                        1.5.dp,
                        eventColor.copy(alpha = 0.6f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = eventIcon,
                    contentDescription = null,
                    tint = eventColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Bottom connector line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    eventColor.copy(alpha = 0.5f),
                                    colors.glassBorder
                                )
                            )
                        )
                )
            }
        }
        
        // Event content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    if (event.playerName != null) {
                        Text(
                            text = event.playerName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = eventColor
                        )
                    }
                }
                
                // Timestamp/Over info
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = event.timestamp,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textMuted
                    )
                    if (event.over != null) {
                        Text(
                            text = "Over ${event.over}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textTertiary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = event.description,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Player spotlight card with premium animations
 */
@Composable
fun PlayerSpotlightCard(
    playerName: String,
    playerRole: String,
    imageUrl: String?,
    stats: List<TimelinePlayerStat>,
    modifier: Modifier = Modifier,
    accentColor: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spotlight")
    val shimmerOffset = 0.5f
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val colors = BouncerDesignSystem.colors

    Surface(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = accentColor.copy(alpha = 0.3f)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface
    ) {
        Box {
            // Background effect
            Canvas(modifier = Modifier.matchParentSize()) {
                // Glow behind player image
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.4f),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.15f, size.height * 0.4f)
                )
                
                // Shimmer
                val shimmerStart = shimmerOffset * size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerStart, 0f),
                        end = Offset(shimmerStart + size.width * 0.3f, size.height)
                    )
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Player image
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.maxDimension * 0.7f
                            )
                        }
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    accentColor,
                                    accentColor.copy(alpha = 0.3f)
                                )
                            ),
                            CircleShape
                        )
                        .clip(CircleShape)
                        .background(colors.surface1),
                    contentAlignment = Alignment.Center
                ) {
                    if (!imageUrl.isNullOrEmpty()) {
                        CoilImage(
                            model = imageUrl,
                            contentDescription = playerName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = accentColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Player info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = playerName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = playerRole,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor
                        )
                    }
                    
                    // Stats grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        stats.take(3).forEach { stat ->
                            StatMiniCard(stat = stat, accentColor = accentColor)
                        }
                    }
                }
            }
        }
    }
}

data class TimelinePlayerStat(
    val label: String,
    val value: String
)

@Composable
private fun StatMiniCard(
    stat: TimelinePlayerStat,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stat.value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary
        )
        Text(
            text = stat.label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = accentColor
        )
    }
}

/**
 * Head-to-head comparison card
 */
@Composable
fun HeadToHeadCard(
    team1Name: String,
    team2Name: String,
    team1Logo: String?,
    team2Logo: String?,
    team1Wins: Int,
    team2Wins: Int,
    draws: Int,
    team1Color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    team2Color: Color = BouncerDesignSystem.colors.statusInfo,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val totalMatches = team1Wins + team2Wins + draws
    val team1Percentage = if (totalMatches > 0) team1Wins.toFloat() / totalMatches else 0f
    val team2Percentage = if (totalMatches > 0) team2Wins.toFloat() / totalMatches else 0f
    
    val animatedTeam1 by animateFloatAsState(
        targetValue = team1Percentage,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team1Animate"
    )
    val animatedTeam2 by animateFloatAsState(
        targetValue = team2Percentage,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team2Animate"
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "HEAD TO HEAD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = hierarchy.secondary.primary
            )
            
            // Teams comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamLogoCircle(
                        logo = team1Logo,
                        teamName = team1Name,
                        color = team1Color,
                        size = 60.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = team1Wins.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = team1Color
                    )
                    Text(
                        text = "Wins",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textMuted
                    )
                }
                
                // Stats
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = totalMatches.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Matches",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textMuted
                    )
                    if (draws > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$draws Draws",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textTertiary
                        )
                    }
                }
                
                // Team 2
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamLogoCircle(
                        logo = team2Logo,
                        teamName = team2Name,
                        color = team2Color,
                        size = 60.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = team2Wins.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = team2Color
                    )
                    Text(
                        text = "Wins",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textMuted
                    )
                }
            }
            
            // Win ratio bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.glassBorder)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Team 1 portion
                    Box(
                        modifier = Modifier
                            .weight(animatedTeam1.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(team1Color)
                    )
                    
                    // Draws portion (if any)
                    if (draws > 0 && totalMatches > 0) {
                        Box(
                            modifier = Modifier
                                .weight((draws.toFloat() / totalMatches).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(colors.textMuted)
                        )
                    }
                    
                    // Team 2 portion
                    Box(
                        modifier = Modifier
                            .weight(animatedTeam2.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(team2Color)
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamLogoCircle(
    logo: String?,
    teamName: String,
    color: Color,
    size: Dp
) {
    val colors = BouncerDesignSystem.colors
    
    Box(
        modifier = Modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    radius = this.size.maxDimension * 0.7f
                )
            }
            .border(1.5.dp, color.copy(alpha = 0.5f), CircleShape)
            .clip(CircleShape)
            .background(colors.surface1),
        contentAlignment = Alignment.Center
    ) {
        if (!logo.isNullOrEmpty()) {
            CoilImage(
                model = logo,
                contentDescription = teamName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = teamName.take(3).uppercase(),
                fontSize = (size.value * 0.25f).sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

/**
 * Animated run meter visualization
 */
@Composable
fun RunMeter(
    currentRuns: Int,
    targetRuns: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = BouncerDesignSystem.hierarchy.secondary.primary
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val progress = (currentRuns.toFloat() / targetRuns).coerceIn(0f, 1f)
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "runProgress"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "runMeter")
    val pulseAlpha = 0.55f

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "TARGET CHASE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = accentColor
        )
        
        // Capture color before non-composable Canvas scope
        val glassBorderColor = colors.glassBorder
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height)
                
                // Background arc
                drawArc(
                    color = glassBorderColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                
                // Progress arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.5f),
                            accentColor,
                            accentColor
                        )
                    ),
                    startAngle = 180f,
                    sweepAngle = 180f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                
                // Glow at progress point
                val progressAngle = (180f + 180f * animatedProgress) * PI.toFloat() / 180f
                val glowX = center.x + radius * cos(progressAngle)
                val glowY = center.y + radius * sin(progressAngle)
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = pulseAlpha),
                            Color.Transparent
                        ),
                        center = Offset(glowX, glowY),
                        radius = 30.dp.toPx()
                    ),
                    radius = 30.dp.toPx(),
                    center = Offset(glowX, glowY)
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Text(
                    text = currentRuns.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = BouncerThemeV2.colors.onSurface
                )
                Text(
                    text = "/ $targetRuns",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textMuted
                )
            }
        }
        
        Text(
            text = "Need ${targetRuns - currentRuns} more runs to win",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary
        )
    }
}
