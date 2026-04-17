package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium live commentary card with animations
 */
@Composable
fun LiveCommentaryCardEnhanced(
    overNumber: String,
    ballNumber: String,
    commentary: String,
    runs: String?,
    isWicket: Boolean,
    isBoundary: Boolean,
    bowlerName: String?,
    batsmanName: String?,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val eventColor = when {
        isWicket -> colors.statusLive
        runs == "6" -> hierarchy.secondary.primary
        runs == "4" -> colors.statusInfo
        runs == "0" || runs == "." -> DotBall
        else -> colors.textSecondary
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "commentary")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "commentaryGlow"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isWicket || isBoundary) {
                    Modifier.drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    eventColor.copy(alpha = glowPulse * 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface,
        border = if (isWicket || isBoundary) {
            BorderStroke(1.dp, eventColor.copy(alpha = 0.4f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Over/Ball indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Text(
                    text = overNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = hierarchy.secondary.primary
                )
                Text(
                    text = ".$ballNumber",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textMuted
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Runs badge
                runs?.let {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                eventColor.copy(alpha = 0.15f),
                                CircleShape
                            )
                            .border(1.dp, eventColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (it == ".") "0" else it,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = eventColor
                        )
                    }
                }
            }
            
            // Commentary content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Players
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bowlerName?.let {
                        Text(
                            text = "to $it",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textTertiary
                        )
                    }
                    batsmanName?.let {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                    }
                }
                
                // Commentary text
                Text(
                    text = commentary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textPrimary,
                    lineHeight = 18.sp
                )
                
                // Event badge
                if (isWicket) {
                    EventBadge(text = "WICKET", color = colors.statusLive)
                } else if (runs == "6") {
                    EventBadge(text = "SIX", color = hierarchy.secondary.primary)
                } else if (runs == "4") {
                    EventBadge(text = "FOUR", color = colors.statusInfo)
                }
            }
        }
    }
}

@Composable
private fun EventBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Animated recent balls strip with detailed view
 */
@Composable
fun EnhancedRecentBallsStrip(
    balls: List<BallEvent>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var selectedBall by remember { mutableStateOf<BallEvent?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Balls strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(balls) { ball ->
                AnimatedBallChip(
                    ball = ball,
                    isSelected = selectedBall == ball,
                    onClick = { selectedBall = if (selectedBall == ball) null else ball }
                )
            }
        }
        
        // Selected ball detail
        AnimatedVisibility(
            visible = selectedBall != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            selectedBall?.let { ball ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface1
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Over ${ball.over}.${ball.ballInOver}",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = ball.description,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                        }
                        
                        if (ball.batsmanName != null) {
                            Text(
                                text = ball.batsmanName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = hierarchy.secondary.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

data class BallEvent(
    val id: String,
    val over: Int,
    val ballInOver: Int,
    val runs: String,
    val isWicket: Boolean,
    val description: String,
    val batsmanName: String? = null,
    val bowlerName: String? = null
)

@Composable
private fun AnimatedBallChip(
    ball: BallEvent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val color = when {
        ball.isWicket -> colors.statusLive
        ball.runs == "6" -> hierarchy.secondary.primary
        ball.runs == "4" -> colors.statusInfo
        ball.runs == "0" || ball.runs == "." -> DotBall
        else -> colors.textSecondary
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "ballScale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "ball_${ball.id}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ballGlow"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(36.dp)
            .then(
                if (isSelected || ball.isWicket || ball.runs in listOf("6", "4")) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension * 0.8f
                        )
                    }
                } else Modifier
            )
            .background(
                color.copy(alpha = 0.15f),
                CircleShape
            )
            .border(
                if (isSelected) 2.dp else 1.dp,
                color.copy(alpha = if (isSelected) 0.8f else 0.4f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (ball.isWicket) {
            Text(
                text = "W",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        } else {
            Text(
                text = if (ball.runs == ".") "0" else ball.runs,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

/**
 * Match key moments card
 */
@Composable
fun KeyMomentsCard(
    moments: List<KeyMoment>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KEY MOMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = hierarchy.secondary.primary
                )
                
                Text(
                    text = "${moments.size} moments",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textMuted
                )
            }
            
            moments.take(5).forEachIndexed { index, moment ->
                KeyMomentItem(moment = moment, index = index)
                
                if (index < moments.size - 1 && index < 4) {
                    HorizontalDivider(
                        color = colors.glassBorder.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

data class KeyMoment(
    val id: String,
    val type: MomentType,
    val title: String,
    val description: String,
    val over: String,
    val impactScore: Float // 0-1
) {
    enum class MomentType {
        WICKET, BOUNDARY, MILESTONE, PARTNERSHIP, REVIEW, OTHER
    }
}

@Composable
private fun KeyMomentItem(
    moment: KeyMoment,
    index: Int
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var isExpanded by remember { mutableStateOf(false) }
    
    val momentColor = when (moment.type) {
        KeyMoment.MomentType.WICKET -> colors.statusLive
        KeyMoment.MomentType.BOUNDARY -> hierarchy.secondary.primary
        KeyMoment.MomentType.MILESTONE -> hierarchy.secondary.primary
        KeyMoment.MomentType.PARTNERSHIP -> colors.statusSuccess
        KeyMoment.MomentType.REVIEW -> colors.statusInfo
        KeyMoment.MomentType.OTHER -> colors.textSecondary
    }
    
    val momentIcon = when (moment.type) {
        KeyMoment.MomentType.WICKET -> Icons.Default.SportsCricket
        KeyMoment.MomentType.BOUNDARY -> Icons.Default.Whatshot
        KeyMoment.MomentType.MILESTONE -> Icons.Default.Star
        KeyMoment.MomentType.PARTNERSHIP -> Icons.Default.Handshake
        KeyMoment.MomentType.REVIEW -> Icons.Default.Visibility
        KeyMoment.MomentType.OTHER -> Icons.Default.FiberManualRecord
    }
    
    val animatedEnter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300, delayMillis = index * 50),
        label = "momentEnter"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = animatedEnter; translationX = (1f - animatedEnter) * 50f }
            .clickable { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(momentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = momentIcon,
                    contentDescription = null,
                    tint = momentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = moment.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Over ${moment.over}",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }
            
            // Impact indicator
            ImpactIndicator(score = moment.impactScore, color = momentColor)
        }
        
        // Expanded description
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = moment.description,
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 48.dp, top = 8.dp)
            )
        }
    }
}

@Composable
private fun ImpactIndicator(
    score: Float,
    color: Color
) {
    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background
            drawArc(
                color = GlassBorder,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Progress
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = score * 360f,
                useCenter = false,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        Text(
            text = "${(score * 10).toInt()}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Scorecard summary card
 */
@Composable
fun ScorecardSummaryCard(
    teamName: String,
    teamLogo: String?,
    score: String,
    overs: String,
    extras: String?,
    runRate: String?,
    topScorer: PlayerPerformance?,
    topBowler: PlayerPerformance?,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "scorecard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 16.dp,
            shape = RoundedCornerShape(24.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Box {
            // Shimmer effect
            Canvas(modifier = Modifier.matchParentSize()) {
                val shimmerStart = shimmerOffset * size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerStart, 0f),
                        end = Offset(shimmerStart + size.width * 0.3f, size.height)
                    )
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Team header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team logo
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.dp, hierarchy.secondary.primary.copy(alpha = 0.4f), CircleShape)
                            .clip(CircleShape)
                            .background(colors.surface1),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!teamLogo.isNullOrEmpty()) {
                            CoilImage(
                                model = teamLogo,
                                contentDescription = teamName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = teamName.take(3).uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = hierarchy.secondary.primary
                            )
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = teamName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            runRate?.let {
                                Text(
                                    text = "RR: $it",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                            extras?.let {
                                Text(
                                    text = "Extras: $it",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                        }
                    }
                    
                    // Score
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = score,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = hierarchy.secondary.primary
                        )
                        Text(
                            text = "($overs overs)",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                }
                
                // Divider
                HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.3f))
                
                // Top performers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    topScorer?.let {
                        PerformerMini(
                            label = "Top Scorer",
                            name = it.name,
                            stat = it.stat,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    topBowler?.let {
                        PerformerMini(
                            label = "Best Bowler",
                            name = it.name,
                            stat = it.stat,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

data class PlayerPerformance(
    val name: String,
    val stat: String
)

@Composable
private fun PerformerMini(
    label: String,
    name: String,
    stat: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMuted,
            letterSpacing = 0.5.sp
        )
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stat,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = hierarchy.secondary.primary
        )
    }
}
