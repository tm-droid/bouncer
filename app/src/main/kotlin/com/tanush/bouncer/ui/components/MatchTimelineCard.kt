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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Types of key moments in a cricket match
 */
enum class MatchMomentType {
    WICKET,
    BOUNDARY_FOUR,
    BOUNDARY_SIX,
    FIFTY,
    CENTURY,
    HAT_TRICK,
    FIVE_WICKETS,
    INNINGS_START,
    INNINGS_END,
    DRINKS_BREAK,
    PARTNERSHIP_50,
    PARTNERSHIP_100,
    POWERPLAY_START,
    POWERPLAY_END,
    STRATEGIC_TIMEOUT,
    NEW_BALL,
    REVIEW_SUCCESSFUL,
    REVIEW_UNSUCCESSFUL,
    RUN_OUT,
    DROPPED_CATCH,
    STUMPING
}

/**
 * A key moment in the match
 */
data class MatchMoment(
    val id: String,
    val type: MatchMomentType,
    val over: Float,
    val description: String,
    val team: String,
    val player: String? = null,
    val score: String? = null,
    val impact: Float = 0f // -1 to 1, negative = bad for batting team
)

/**
 * Get icon for a moment type
 */
@Composable
private fun getMomentIcon(type: MatchMomentType): ImageVector {
    return when (type) {
        MatchMomentType.WICKET -> Icons.Default.Close
        MatchMomentType.BOUNDARY_FOUR -> Icons.Default.Looks4
        MatchMomentType.BOUNDARY_SIX -> Icons.Default.Looks6
        MatchMomentType.FIFTY -> Icons.Default.EmojiEvents
        MatchMomentType.CENTURY -> Icons.Default.EmojiEvents
        MatchMomentType.HAT_TRICK -> Icons.Default.AutoAwesome
        MatchMomentType.FIVE_WICKETS -> Icons.Default.Star
        MatchMomentType.INNINGS_START -> Icons.Default.PlayArrow
        MatchMomentType.INNINGS_END -> Icons.Default.Stop
        MatchMomentType.DRINKS_BREAK -> Icons.Default.LocalCafe
        MatchMomentType.PARTNERSHIP_50 -> Icons.Default.Handshake
        MatchMomentType.PARTNERSHIP_100 -> Icons.Default.Handshake
        MatchMomentType.POWERPLAY_START -> Icons.Default.Bolt
        MatchMomentType.POWERPLAY_END -> Icons.Outlined.Bolt
        MatchMomentType.STRATEGIC_TIMEOUT -> Icons.Default.Timer
        MatchMomentType.NEW_BALL -> Icons.Default.SportsCricket
        MatchMomentType.REVIEW_SUCCESSFUL -> Icons.Default.CheckCircle
        MatchMomentType.REVIEW_UNSUCCESSFUL -> Icons.Default.Cancel
        MatchMomentType.RUN_OUT -> Icons.AutoMirrored.Filled.DirectionsRun
        MatchMomentType.DROPPED_CATCH -> Icons.Default.ErrorOutline
        MatchMomentType.STUMPING -> Icons.Default.SportsCricket
    }
}

/**
 * Get color for a moment type
 */
private fun getMomentColor(type: MatchMomentType): Color {
    return when (type) {
        MatchMomentType.WICKET -> WicketRed
        MatchMomentType.BOUNDARY_FOUR -> BoundaryFour
        MatchMomentType.BOUNDARY_SIX -> BoundarySix
        MatchMomentType.FIFTY -> ShinyGoldDeep
        MatchMomentType.CENTURY -> ShinyGoldDeep
        MatchMomentType.HAT_TRICK -> ChartPurple
        MatchMomentType.FIVE_WICKETS -> ChartPurple
        MatchMomentType.INNINGS_START -> ChartBlue
        MatchMomentType.INNINGS_END -> TextTertiary
        MatchMomentType.DRINKS_BREAK -> ChartCyan
        MatchMomentType.PARTNERSHIP_50 -> SuccessGreen
        MatchMomentType.PARTNERSHIP_100 -> ShinyGoldDeep
        MatchMomentType.POWERPLAY_START -> WarningOrange
        MatchMomentType.POWERPLAY_END -> WarningOrange
        MatchMomentType.STRATEGIC_TIMEOUT -> TextTertiary
        MatchMomentType.NEW_BALL -> ChartBlue
        MatchMomentType.REVIEW_SUCCESSFUL -> SuccessGreen
        MatchMomentType.REVIEW_UNSUCCESSFUL -> ErrorRed
        MatchMomentType.RUN_OUT -> WicketRed
        MatchMomentType.DROPPED_CATCH -> WarningOrange
        MatchMomentType.STUMPING -> WicketRed
    }
}

/**
 * Premium Match Timeline Card
 * Shows key moments in chronological order
 */
@Composable
fun MatchTimelineCard(
    moments: List<MatchMoment>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "timelineShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
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
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "MATCH TIMELINE",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${moments.size} Events",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            if (moments.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "No key moments yet",
                            color = TextTertiary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Timeline
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(moments.sortedBy { it.over }) { moment ->
                        TimelineMomentItem(
                            moment = moment,
                            isFirst = moment == moments.minByOrNull { it.over },
                            isLast = moment == moments.maxByOrNull { it.over }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineMomentItem(
    moment: MatchMoment,
    isFirst: Boolean,
    isLast: Boolean
) {
    val color = getMomentColor(moment.type)
    val icon = getMomentIcon(moment.type)
    
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable { isExpanded = !isExpanded }
    ) {
        // Over indicator
        Text(
            "Ov ${moment.over}",
            color = TextTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Timeline with node
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left connector line
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(CardBorder.copy(alpha = 0.4f))
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Node
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.2f),
                                color.copy(alpha = 0.05f)
                            )
                        ),
                        CircleShape
                    )
                    .border(2.dp, color.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Right connector line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(CardBorder.copy(alpha = 0.4f))
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Description
        Text(
            moment.description,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp
        )
        
        // Expanded details
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (moment.player != null) {
                    Text(
                        moment.player,
                        color = color,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (moment.score != null) {
                    Text(
                        moment.score,
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

/**
 * Vertical Match Timeline for detailed view
 */
@Composable
fun VerticalMatchTimeline(
    moments: List<MatchMoment>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        moments.sortedBy { it.over }.forEachIndexed { index, moment ->
            VerticalTimelineItem(
                moment = moment,
                isFirst = index == 0,
                isLast = index == moments.lastIndex
            )
        }
    }
}

@Composable
private fun VerticalTimelineItem(
    moment: MatchMoment,
    isFirst: Boolean,
    isLast: Boolean
) {
    val color = getMomentColor(moment.type)
    val icon = getMomentIcon(moment.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline track
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Top connector
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(CardBorder.copy(alpha = 0.4f))
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Node
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .shadow(8.dp, CircleShape, spotColor = color.copy(alpha = 0.3f))
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .border(2.dp, color.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            // Bottom connector
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(CardBorder.copy(alpha = 0.4f))
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Content
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 8.dp),
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, color.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        moment.description,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (moment.player != null) {
                            Text(
                                moment.player,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "Over ${moment.over}",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
                
                if (moment.score != null) {
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            moment.score,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mini timeline indicator for match cards
 */
@Composable
fun MiniMatchTimeline(
    moments: List<MatchMoment>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CardDarkBlue),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        moments.takeLast(10).forEach { moment ->
            val color = getMomentColor(moment.type)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color.copy(alpha = 0.7f))
            )
        }
    }
}
