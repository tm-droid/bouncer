package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import io.sentry.Sentry

/**
 * Types of commentary events
 */
enum class CommentaryEventType {
    REGULAR,
    DOT_BALL,
    SINGLE,
    DOUBLE,
    THREE,
    FOUR,
    SIX,
    WICKET,
    WIDE,
    NO_BALL,
    LEG_BYE,
    OVER_END,
    INNINGS_END,
    MILESTONE,
    REVIEW
}

/**
 * A live commentary entry
 */
data class CommentaryEntry(
    val id: String,
    val over: String,  // e.g., "18.4"
    val text: String,
    val type: CommentaryEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val batsmanName: String? = null,
    val bowlerName: String? = null,
    val runs: Int? = null,
    val isNew: Boolean = false
)

/**
 * Get the color for a commentary event type
 */
@Composable
private fun getEventColor(type: CommentaryEventType): Color {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    return when (type) {
        CommentaryEventType.SIX -> hierarchy.secondary.primary
        CommentaryEventType.FOUR -> StatusInfo
        CommentaryEventType.WICKET -> hierarchy.secondary.primary
        CommentaryEventType.MILESTONE -> hierarchy.secondary.primary
        CommentaryEventType.WIDE, CommentaryEventType.NO_BALL -> WarningOrange
        CommentaryEventType.OVER_END -> TextTertiary
        CommentaryEventType.INNINGS_END -> hierarchy.secondary.primary
        CommentaryEventType.REVIEW -> colors.statusInfo
        else -> TextSecondary
    }
}

/**
 * Get the icon for a commentary event type
 */
@Composable
private fun getEventIcon(type: CommentaryEventType) = when (type) {
    CommentaryEventType.SIX -> Icons.Default.Looks6
    CommentaryEventType.FOUR -> Icons.Default.Looks4
    CommentaryEventType.WICKET -> Icons.Default.Close
    CommentaryEventType.MILESTONE -> Icons.Default.EmojiEvents
    CommentaryEventType.WIDE -> Icons.Default.SwapHoriz
    CommentaryEventType.NO_BALL -> Icons.Default.Block
    CommentaryEventType.OVER_END -> Icons.Default.Refresh
    CommentaryEventType.INNINGS_END -> Icons.Default.Flag
    CommentaryEventType.REVIEW -> Icons.Default.Visibility
    CommentaryEventType.DOT_BALL -> Icons.Default.FiberManualRecord
    else -> null
}

/**
 * Premium Live Commentary Bubbles
 * Chat-style live commentary with animations
 */
@Composable
fun LiveCommentaryBubbles(
    entries: List<CommentaryEntry>,
    modifier: Modifier = Modifier,
    onEntryClick: ((CommentaryEntry) -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val listState = rememberLazyListState()
    
    // Auto-scroll to newest entry
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.15f))
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Animated live indicator
                    LiveIndicator()
                    
                    Text(
                        "LIVE COMMENTARY",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Text(
                    "${entries.size} updates",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Commentary list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries.reversed(), key = { it.id }) { entry ->
                    CommentaryBubble(
                        entry = entry,
                        onClick = onEntryClick?.let { { it(entry) } }
                    )
                }
            }
        }
    }
}

/**
 * Animated live indicator dot
 */
@Composable
private fun LiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "liveIndicator")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(20.dp * scale)
                .background(
                    color = StatusLive.copy(alpha = 0.2f * alpha),
                    shape = CircleShape
                )
        )
        
        // Inner dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = StatusLive.copy(alpha = alpha),
                    shape = CircleShape
                )
        )
    }
}

/**
 * Individual commentary bubble
 */
@Composable
private fun CommentaryBubble(
    entry: CommentaryEntry,
    onClick: (() -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var isVisible by remember { mutableStateOf(!entry.isNew) }
    
    LaunchedEffect(entry.id) {
        if (entry.isNew) {
            delay(50)
            isVisible = true
        }
    }
    
    val eventColor = getEventColor(entry.type)
    val isHighlight = entry.type in listOf(
        CommentaryEventType.SIX,
        CommentaryEventType.FOUR,
        CommentaryEventType.WICKET,
        CommentaryEventType.MILESTONE
    )
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(200))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Over number badge
            OverBadge(
                over = entry.over,
                type = entry.type
            )
            
            // Commentary content
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation = if (isHighlight) 8.dp else 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = if (isHighlight) eventColor.copy(alpha = 0.3f) else Color.Transparent
                    ),
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = if (isHighlight) {
                    eventColor.copy(alpha = 0.1f)
                } else {
                    GlassSurface
                },
                border = BorderStroke(
                    0.5.dp,
                    if (isHighlight) eventColor.copy(alpha = 0.3f) else hierarchy.secondary.primary.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    // Event type header for highlights
                    if (isHighlight) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            getEventIcon(entry.type)?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = eventColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = when (entry.type) {
                                    CommentaryEventType.SIX -> "SIX!"
                                    CommentaryEventType.FOUR -> "FOUR!"
                                    CommentaryEventType.WICKET -> "WICKET!"
                                    CommentaryEventType.MILESTONE -> "MILESTONE"
                                    else -> ""
                                },
                                color = eventColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    
                    // Main commentary text
                    Text(
                        text = entry.text,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = 20.sp
                    )
                    
                    // Player names if available
                    if (entry.batsmanName != null || entry.bowlerName != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            entry.batsmanName?.let { name ->
                                Text(
                                    text = name,
                                    color = TextTertiary,
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                            entry.bowlerName?.let { name ->
                                Text(
                                    text = name,
                                    color = TextTertiary,
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
            
            // Runs indicator for scoring events
            if (entry.runs != null && entry.runs > 0) {
                RunsIndicator(runs = entry.runs, type = entry.type)
            }
        }
    }
}

/**
 * Over number badge
 */
@Composable
private fun OverBadge(
    over: String,
    type: CommentaryEventType
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val eventColor = getEventColor(type)
    val isHighlight = type in listOf(
        CommentaryEventType.SIX,
        CommentaryEventType.FOUR,
        CommentaryEventType.WICKET
    )
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isHighlight) eventColor.copy(alpha = 0.15f) else GlassSurface,
        border = BorderStroke(
            0.5.dp,
            if (isHighlight) eventColor.copy(alpha = 0.3f) else hierarchy.secondary.primary.copy(alpha = 0.1f)
        ),
        modifier = Modifier.width(44.dp)
    ) {
        Text(
            text = over,
            color = if (isHighlight) eventColor else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Runs indicator circle
 */
@Composable
private fun RunsIndicator(
    runs: Int,
    type: CommentaryEventType
) {
    val color = getEventColor(type)
    
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = runs.toString(),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Compact commentary ticker for header/summary
 */
@Composable
fun CommentaryTicker(
    latestEntry: CommentaryEntry?,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "tickerShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = GlassSurface,
        border = BorderStroke(
            0.5.dp,
            Brush.linearGradient(
                colors = listOf(
                    hierarchy.secondary.primary.copy(alpha = 0.2f * shimmerOffset),
                    hierarchy.secondary.primary.copy(alpha = 0.1f),
                    hierarchy.secondary.primary.copy(alpha = 0.2f * (1 - shimmerOffset))
                ),
                start = Offset(shimmerOffset * 300, 0f),
                end = Offset(shimmerOffset * 300 + 200, 44f)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live indicator
            LiveIndicator()
            
            // Latest commentary text
            if (latestEntry != null) {
                val eventColor = getEventColor(latestEntry.type)
                
                Text(
                    text = "${latestEntry.over}: ",
                    color = eventColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = latestEntry.text,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "Waiting for updates...",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Helper function to parse commentary text and determine event type
 */
fun parseCommentaryType(text: String, runs: Int?): CommentaryEventType {
    try {
        val lowerText = text.lowercase()
    
        return when {
            lowerText.contains("wicket") || lowerText.contains("out") || 
            lowerText.contains("caught") || lowerText.contains("bowled") ||
            lowerText.contains("lbw") || lowerText.contains("run out") ||
            lowerText.contains("stumped") -> CommentaryEventType.WICKET
        
            lowerText.contains("six") || runs == 6 -> CommentaryEventType.SIX
            lowerText.contains("four") || runs == 4 -> CommentaryEventType.FOUR
        
            lowerText.contains("century") || lowerText.contains("hundred") ||
            lowerText.contains("fifty") || lowerText.contains("milestone") -> CommentaryEventType.MILESTONE
        
            lowerText.contains("wide") -> CommentaryEventType.WIDE
            lowerText.contains("no ball") || lowerText.contains("no-ball") -> CommentaryEventType.NO_BALL
            lowerText.contains("leg bye") -> CommentaryEventType.LEG_BYE
        
            lowerText.contains("review") || lowerText.contains("drs") -> CommentaryEventType.REVIEW
            lowerText.contains("end of over") -> CommentaryEventType.OVER_END
            lowerText.contains("innings") && (lowerText.contains("end") || lowerText.contains("all out")) -> CommentaryEventType.INNINGS_END
        
            runs == 0 || lowerText.contains("dot ball") -> CommentaryEventType.DOT_BALL
            runs == 1 -> CommentaryEventType.SINGLE
            runs == 2 -> CommentaryEventType.DOUBLE
            runs == 3 -> CommentaryEventType.THREE
        
            else -> CommentaryEventType.REGULAR
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
