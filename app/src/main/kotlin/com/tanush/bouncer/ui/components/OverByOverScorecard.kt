package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * A single ball's result in an over
 */
data class BallResult(
    val ballNumber: Int,
    val runs: Int,
    val isWicket: Boolean = false,
    val isWide: Boolean = false,
    val isNoBall: Boolean = false,
    val isLegBye: Boolean = false,
    val isBye: Boolean = false,
    val batsmanName: String? = null,
    val bowlerName: String? = null,
    val description: String? = null
)

/**
 * Complete over summary
 */
data class OverSummary(
    val overNumber: Int,
    val balls: List<BallResult>,
    val runs: Int,
    val wickets: Int,
    val bowlerName: String,
    val cumulativeScore: String, // e.g., "45/2"
    val runRate: Float
)

/**
 * Get the color for a ball result
 */
private fun getBallColor(ball: BallResult): Color {
    return when {
        ball.isWicket -> WicketRed
        ball.runs == 6 -> BoundarySix
        ball.runs == 4 -> BoundaryFour
        ball.isWide || ball.isNoBall -> ErrorRed.copy(alpha = 0.7f)
        ball.runs == 0 -> TextTertiary.copy(alpha = 0.5f)
        else -> TextSecondary
    }
}

/**
 * Get the display text for a ball result
 */
private fun getBallText(ball: BallResult): String {
    return when {
        ball.isWicket -> "W"
        ball.isWide -> "WD"
        ball.isNoBall -> "NB"
        ball.isLegBye -> "LB"
        ball.isBye -> "B"
        else -> ball.runs.toString()
    }
}

/**
 * Premium Over-by-Over Scorecard
 * Shows detailed over-by-over breakdown with ball-by-ball visualization
 */
@Composable
fun OverByOverScorecard(
    overs: List<OverSummary>,
    teamName: String,
    teamColor: Color = ShinyGoldDeep,
    modifier: Modifier = Modifier,
    onOverClick: ((OverSummary) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    
    val infiniteTransition = rememberInfiniteTransition(label = "scorecardShimmer")
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
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            0.5.dp,
            Brush.linearGradient(
                colors = listOf(
                    ShinyGoldLight.copy(alpha = 0.2f * shimmerOffset),
                    CardBorder.copy(alpha = 0.3f),
                    ShinyGoldLight.copy(alpha = 0.2f * (1 - shimmerOffset))
                ),
                start = Offset(shimmerOffset * 400, 0f),
                end = Offset(shimmerOffset * 400 + 200, 100f)
            )
        )
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "OVER-BY-OVER",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = teamColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, teamColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = teamName,
                        color = teamColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Overs list
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(overs, key = { it.overNumber }) { over ->
                    OverCard(
                        over = over,
                        teamColor = teamColor,
                        onClick = onOverClick?.let { { it(over) } }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary stats
            OversSummaryStats(overs = overs, teamColor = teamColor)
        }
    }
}

/**
 * Individual over card
 */
@Composable
private fun OverCard(
    over: OverSummary,
    teamColor: Color,
    onClick: (() -> Unit)?
) {
    val hasWicket = over.wickets > 0
    val hasBoundary = over.balls.any { it.runs >= 4 }
    
    val cardColor = when {
        hasWicket -> WicketRed.copy(alpha = 0.1f)
        over.runs >= 12 -> BoundarySix.copy(alpha = 0.1f)
        over.runs >= 8 -> BoundaryFour.copy(alpha = 0.1f)
        over.runs == 0 -> SuccessGreen.copy(alpha = 0.08f) // Maiden
        else -> Color.White.copy(alpha = 0.08f)
    }
    
    val borderColor = when {
        hasWicket -> WicketRed.copy(alpha = 0.3f)
        over.runs >= 12 -> BoundarySix.copy(alpha = 0.3f)
        over.runs == 0 -> SuccessGreen.copy(alpha = 0.3f)
        else -> CardBorder.copy(alpha = 0.2f)
    }
    
    Surface(
        modifier = Modifier
            .width(120.dp)
            .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Over number header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Over ${over.overNumber}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Runs badge
                Surface(
                    shape = CircleShape,
                    color = when {
                        over.runs >= 12 -> BoundarySix.copy(alpha = 0.2f)
                        over.runs >= 8 -> BoundaryFour.copy(alpha = 0.2f)
                        over.runs == 0 -> SuccessGreen.copy(alpha = 0.2f)
                        else -> teamColor.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = over.runs.toString(),
                        color = when {
                            over.runs >= 12 -> BoundarySix
                            over.runs >= 8 -> BoundaryFour
                            over.runs == 0 -> SuccessGreen
                            else -> teamColor
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Ball-by-ball visualization
            BallRow(balls = over.balls)
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Bowler name
            Text(
                text = over.bowlerName,
                color = TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            
            // Cumulative score
            Text(
                text = over.cumulativeScore,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Ball-by-ball visualization row
 */
@Composable
private fun BallRow(balls: List<BallResult>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        balls.take(6).forEachIndexed { index, ball ->
            BallChip(ball = ball)
        }
        
        // Show extras indicator if there are more than 6 balls
        if (balls.size > 6) {
            Surface(
                shape = CircleShape,
                color = ErrorRed.copy(alpha = 0.15f),
                modifier = Modifier.size(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+${balls.size - 6}",
                        color = ErrorRed,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Individual ball chip
 */
@Composable
private fun BallChip(ball: BallResult) {
    val color = getBallColor(ball)
    val text = getBallText(ball)
    
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.size(18.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = color,
                fontSize = if (text.length > 1) 6.sp else 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Summary statistics for all overs
 */
@Composable
private fun OversSummaryStats(
    overs: List<OverSummary>,
    teamColor: Color
) {
    val totalRuns = overs.sumOf { it.runs }
    val totalWickets = overs.sumOf { it.wickets }
    val maidenOvers = overs.count { it.runs == 0 }
    val bestOver = overs.maxByOrNull { it.runs }
    val worstOver = overs.filter { it.wickets > 0 }.maxByOrNull { it.wickets }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryStatItem(
            label = "Total",
            value = "$totalRuns/$totalWickets",
            color = teamColor
        )
        
        SummaryStatItem(
            label = "Overs",
            value = "${overs.size}",
            color = TextSecondary
        )
        
        SummaryStatItem(
            label = "Maidens",
            value = "$maidenOvers",
            color = SuccessGreen
        )
        
        if (bestOver != null) {
            SummaryStatItem(
                label = "Best Over",
                value = "${bestOver.runs} (${bestOver.overNumber})",
                color = BoundarySix
            )
        }
    }
}

/**
 * Individual summary stat item
 */
@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Compact ball result indicator for use in other components
 */
@Composable
fun CompactBallIndicator(
    result: String,
    modifier: Modifier = Modifier
) {
    val color = when {
        result == "W" -> WicketRed
        result == "6" -> BoundarySix
        result == "4" -> BoundaryFour
        result == "WD" || result == "NB" -> ErrorRed.copy(alpha = 0.7f)
        result == "0" || result == "." -> TextTertiary.copy(alpha = 0.5f)
        else -> TextSecondary
    }
    
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (result == ".") "0" else result,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Recent overs strip - compact horizontal strip showing last N overs
 */
@Composable
fun RecentOversStrip(
    overs: List<OverSummary>,
    count: Int = 5,
    modifier: Modifier = Modifier
) {
    val recentOvers = overs.takeLast(count)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last ${recentOvers.size} overs:",
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentOvers.forEach { over ->
                    val hasWicket = over.wickets > 0
                    val color = when {
                        hasWicket -> WicketRed
                        over.runs >= 10 -> BoundarySix
                        over.runs >= 6 -> BoundaryFour
                        over.runs == 0 -> SuccessGreen
                        else -> TextSecondary
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${over.runs}" + if (hasWicket) "/W" else "",
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
