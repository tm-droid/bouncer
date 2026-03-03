package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import io.sentry.Sentry

/**
 * Data class for a single commentary ball
 */
data class CommentaryBall(
    val id: String,
    val over: Float,
    val ballNumber: Int,
    val result: String,          // "0", "1", "2", "3", "4", "6", "W", "WD", "NB"
    val commentary: String,
    val batsmanName: String? = null,
    val bowlerName: String? = null,
    val isHighlight: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Premium Live Commentary Card with animated ball-by-ball updates
 */
@Composable
fun LiveCommentaryCard(
    commentary: List<CommentaryBall>,
    modifier: Modifier = Modifier,
    title: String = "Live Commentary",
    maxHeight: Int = 400,
    showOverHeader: Boolean = true
) {
    val listState = rememberLazyListState()
    var newBallAnimation by remember { mutableStateOf(false) }
    var previousCommentarySize by remember { mutableIntStateOf(commentary.size) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "commentaryAnim")
    
    val liveIndicatorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveIndicator"
    )
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Detect new balls
    LaunchedEffect(commentary.size) {
        if (commentary.size > previousCommentarySize) {
            newBallAnimation = true
            listState.animateScrollToItem(0)
            delay(1000)
            newBallAnimation = false
        }
        previousCommentarySize = commentary.size
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = GoldPrimary.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
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
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White, GoldPrimary, GoldDark)
                            )
                        )
                    )
                    
                    // Live indicator
                    Box(
                        modifier = Modifier
                            .background(
                                LiveRedPulse.copy(alpha = liveIndicatorAlpha * 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                LiveRedPulse,
                                                LiveRedPulse.copy(alpha = 0.5f)
                                            )
                                        ),
                                        CircleShape
                                    )
                                    .graphicsLayer {
                                        scaleX = 0.8f + liveIndicatorAlpha * 0.4f
                                        scaleY = 0.8f + liveIndicatorAlpha * 0.4f
                                    }
                            )
                            Text(
                                text = "LIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiveRedPulse,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                Text(
                    text = "${commentary.size} balls",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recent balls quick view
            if (commentary.isNotEmpty()) {
                RecentBallsStrip(
                    balls = commentary.take(12),
                    shimmerOffset = shimmerOffset
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Commentary list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val groupedByOver = commentary.groupBy { it.over.toInt() }
                    .toSortedMap(reverseOrder())
                
                groupedByOver.forEach { (over, balls) ->
                    if (showOverHeader) {
                        item(key = "over_$over") {
                            OverHeader(
                                over = over,
                                runs = balls.sumOf { getRunsFromResult(it.result) },
                                wickets = balls.count { it.result == "W" }
                            )
                        }
                    }
                    
                    items(
                        items = balls.sortedByDescending { it.ballNumber },
                        key = { it.id }
                    ) { ball ->
                        val isNew = commentary.indexOf(ball) == 0 && newBallAnimation
                        
                        CommentaryBallItem(
                            ball = ball,
                            isNew = isNew,
                            shimmerOffset = shimmerOffset
                        )
                    }
                }
            }
            
            // Scroll indicator
            if (commentary.size > 5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll for more",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Scroll for more",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentBallsStrip(
    balls: List<CommentaryBall>,
    shimmerOffset: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                GlassSurface,
                RoundedCornerShape(12.dp)
            )
            .border(0.5.dp, GoldPrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        balls.take(8).forEachIndexed { index, ball ->
            val isLatest = index == 0
            
            BallBadge(
                result = ball.result,
                isLatest = isLatest,
                shimmerOffset = shimmerOffset
            )
        }
        
        if (balls.size > 8) {
            Text(
                text = "+${balls.size - 8}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun BallBadge(
    result: String,
    isLatest: Boolean,
    shimmerOffset: Float
) {
    val colors = BouncerDesignSystem.colors
    val color = getBallColor(result)
    val size = if (isLatest) 36.dp else 32.dp
    val isWicket = result == "W"

    // Subtle pulse animation for wicket balls
    val wicketPulseAlpha = if (isWicket) {
        val infiniteTransition = rememberInfiniteTransition(label = "wicketPulse")
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wicketPulseAlpha"
        ).value
    } else 1f

    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (isLatest) {
                    Modifier.border(
                        2.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                colors.goldLight.copy(alpha = 0.8f),
                                GoldPrimary.copy(alpha = 0.6f),
                                colors.goldLight.copy(alpha = 0.8f)
                            )
                        ),
                        CircleShape
                    )
                } else if (isWicket) {
                    Modifier
                        .border(
                            1.5.dp,
                            GoldPrimary.copy(alpha = 0.8f * wicketPulseAlpha),
                            CircleShape
                        )
                        .shadow(4.dp, CircleShape, spotColor = GoldPrimary.copy(alpha = 0.3f * wicketPulseAlpha))
                } else Modifier
            )
            .background(
                color.copy(alpha = if (isWicket) 0.2f * wicketPulseAlpha else 0.15f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = getBallDisplayText(result),
            fontSize = if (isLatest) 14.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = wicketPulseAlpha)
        )

        // Shimmer for latest
        if (isLatest) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startX = (shimmerOffset - 0.3f) * 200f,
                            endX = shimmerOffset * 200f
                        )
                    )
            )
        }
    }
}

@Composable
private fun OverHeader(
    over: Int,
    runs: Int,
    wickets: Int
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        colors.goldPrimary.copy(alpha = 0.15f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Over $over",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.goldPrimary
                )
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$runs runs",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
            if (wickets > 0) {
                Text(
                    text = "$wickets wkt${if (wickets > 1) "s" else ""}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.statusLive
                )
            }
        }
    }
    
    HorizontalDivider(
        color = GoldPrimary.copy(alpha = 0.12f),
        thickness = 0.5.dp
    )
}

@Composable
private fun CommentaryBallItem(
    ball: CommentaryBall,
    isNew: Boolean,
    shimmerOffset: Float
) {
    val enterTransition = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        enterTransition.targetState = true
    }
    
    val color = getBallColor(ball.result)
    val isHighlight = ball.result in listOf("4", "6", "W")
    
    AnimatedVisibility(
        visibleState = enterTransition,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 2 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .then(
                    if (isNew) {
                        Modifier
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.15f),
                                        color.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                color.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp)
                            )
                    } else if (isHighlight) {
                        Modifier
                            .background(
                                color.copy(alpha = 0.05f),
                                RoundedCornerShape(10.dp)
                            )
                    } else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Over.ball indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(40.dp)
                ) {
                    Text(
                        text = "${ball.over.toInt()}.${ball.ballNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Ball result badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .then(
                                if (ball.result == "W") Modifier.border(
                                    1.dp, GoldPrimary.copy(alpha = 0.6f), CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getBallDisplayText(ball.result),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
                
                // Commentary text
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Player names
                    if (ball.bowlerName != null || ball.batsmanName != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ball.bowlerName?.let {
                                Text(
                                    text = it,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusInfo
                                )
                            }
                            Text(
                                text = "to",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            ball.batsmanName?.let {
                                Text(
                                    text = it,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    
                    // Commentary
                    Text(
                        text = ball.commentary,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// Helper functions
private fun getBallColor(result: String): Color = when (result) {
    "0" -> TextMuted
    "1", "2", "3" -> TextPrimary
    "4" -> StatusInfo
    "6" -> GoldPrimary
    "W" -> GoldPrimary
    "WD", "NB" -> StatusWarning
    "LB", "B" -> StatusInfo
    else -> TextSecondary
}

private fun getBallDisplayText(result: String): String = when (result) {
    "0" -> "."
    "W" -> "W"
    "WD" -> "WD"
    "NB" -> "NB"
    "LB" -> "LB"
    "B" -> "B"
    else -> result
}

private fun getRunsFromResult(result: String): Int = when (result) {
    "0" -> 0
    "1", "LB", "B" -> 1
    "2" -> 2
    "3" -> 3
    "4" -> 4
    "6" -> 6
    "WD", "NB" -> 1
    "W" -> 0
    else -> result.toIntOrNull() ?: 0
}
