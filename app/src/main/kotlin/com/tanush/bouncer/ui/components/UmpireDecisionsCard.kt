package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * DRS Review data
 */
data class DRSReview(
    val reviewNumber: Int,
    val teamName: String,
    val playerName: String,
    val overNumber: Float,
    val reviewType: ReviewType,
    val originalDecision: String,
    val outcome: ReviewOutcome,
    val description: String? = null
)

enum class ReviewType {
    LBW, CAUGHT, STUMPED, RUN_OUT, NO_BALL, OTHER
}

enum class ReviewOutcome {
    OVERTURNED, UPHELD, UMPIRE_CALL
}

data class DRSStats(
    val team1Name: String,
    val team1ReviewsRemaining: Int,
    val team1ReviewsUsed: Int,
    val team1Successful: Int,
    val team2Name: String,
    val team2ReviewsRemaining: Int,
    val team2ReviewsUsed: Int,
    val team2Successful: Int,
    val reviews: List<DRSReview> = emptyList()
)

/**
 * Premium Umpire Decisions / DRS Card
 * Shows DRS reviews and umpire decisions
 */
@Composable
fun UmpireDecisionsCard(
    drsStats: DRSStats,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "drs")
    
    // Radar sweep effect
    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarSweep"
    )
    
    // Pulse effect
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = colors.statusInfo.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with animated DRS icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DRS REVIEWS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = colors.goldPrimary
                    )
                    Text(
                        text = "Decision Review System",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
                
                // Animated DRS radar icon
                val statusInfoColor = colors.statusInfo
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.minDimension / 2 - 4.dp.toPx()
                        
                        // Outer ring
                        drawCircle(
                            color = statusInfoColor.copy(alpha = 0.3f),
                            radius = radius,
                            center = center,
                            style = Stroke(2.dp.toPx())
                        )
                        
                        // Inner rings
                        for (i in 1..2) {
                            drawCircle(
                                color = statusInfoColor.copy(alpha = 0.15f),
                                radius = radius * (i / 3f),
                                center = center,
                                style = Stroke(1.dp.toPx())
                            )
                        }
                        
                        // Sweep line
                        val angle = Math.toRadians(radarSweep.toDouble())
                        val endX = center.x + (radius * kotlin.math.cos(angle)).toFloat()
                        val endY = center.y + (radius * kotlin.math.sin(angle)).toFloat()
                        
                        drawLine(
                            color = statusInfoColor.copy(alpha = pulseAlpha),
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 2.dp.toPx()
                        )
                        
                        // Sweep fade trail
                        val path = android.graphics.Path()
                        path.moveTo(center.x, center.y)
                        
                        for (i in 0..30) {
                            val trailAngle = Math.toRadians((radarSweep - i * 2).toDouble())
                            val x = center.x + (radius * kotlin.math.cos(trailAngle)).toFloat()
                            val y = center.y + (radius * kotlin.math.sin(trailAngle)).toFloat()
                            path.lineTo(x, y)
                        }
                        path.lineTo(center.x, center.y)
                        path.close()
                        
                        drawPath(
                            path = path.asComposePath(),
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    statusInfoColor.copy(alpha = 0.3f),
                                    statusInfoColor.copy(alpha = 0f),
                                    Color.Transparent,
                                    Color.Transparent
                                ),
                                center = center
                            )
                        )
                        
                        // Center dot
                        drawCircle(
                            color = statusInfoColor,
                            radius = 3.dp.toPx(),
                            center = center
                        )
                    }
                }
            }
            
            // Team reviews remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TeamReviewsCard(
                    teamName = drsStats.team1Name,
                    reviewsRemaining = drsStats.team1ReviewsRemaining,
                    reviewsUsed = drsStats.team1ReviewsUsed,
                    successful = drsStats.team1Successful,
                    modifier = Modifier.weight(1f)
                )
                
                TeamReviewsCard(
                    teamName = drsStats.team2Name,
                    reviewsRemaining = drsStats.team2ReviewsRemaining,
                    reviewsUsed = drsStats.team2ReviewsUsed,
                    successful = drsStats.team2Successful,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Recent reviews list
            if (drsStats.reviews.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "REVIEW HISTORY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.textSecondary
                    )
                    
                    drsStats.reviews.take(5).forEach { review ->
                        DRSReviewItem(review = review)
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamReviewsCard(
    teamName: String,
    reviewsRemaining: Int,
    reviewsUsed: Int,
    successful: Int,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val successRate = if (reviewsUsed > 0) {
        (successful.toFloat() / reviewsUsed * 100).toInt()
    } else 0
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.glassBorder.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = colors.glassBorder.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = teamName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1
            )
            
            // Reviews remaining indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(reviewsRemaining + reviewsUsed) { index ->
                    val isUsed = index >= reviewsRemaining
                    Box(
                        modifier = Modifier
                            .size(if (isUsed) 12.dp else 14.dp)
                            .background(
                                color = if (isUsed) 
                                    colors.statusLive.copy(alpha = 0.3f) 
                                else 
                                    colors.statusSuccess.copy(alpha = 0.7f),
                                shape = CircleShape
                            )
                            .then(
                                if (!isUsed) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = colors.statusSuccess,
                                        shape = CircleShape
                                    )
                                } else Modifier
                            )
                    )
                }
            }
            
            Text(
                text = "$reviewsRemaining remaining",
                fontSize = 10.sp,
                color = if (reviewsRemaining > 0) colors.statusSuccess else colors.statusLive
            )
            
            if (reviewsUsed > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$successRate%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            successRate >= 50 -> colors.statusSuccess
                            successRate >= 30 -> colors.statusWarning
                            else -> colors.statusLive
                        }
                    )
                    Text(
                        text = "success",
                        fontSize = 9.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun DRSReviewItem(
    review: DRSReview,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val outcomeColor = when (review.outcome) {
        ReviewOutcome.OVERTURNED -> colors.statusSuccess
        ReviewOutcome.UPHELD -> colors.statusLive
        ReviewOutcome.UMPIRE_CALL -> colors.statusWarning
    }
    
    val outcomeText = when (review.outcome) {
        ReviewOutcome.OVERTURNED -> "OVERTURNED"
        ReviewOutcome.UPHELD -> "UPHELD"
        ReviewOutcome.UMPIRE_CALL -> "UMPIRE'S CALL"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = outcomeColor.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = outcomeColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Outcome icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = outcomeColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.5.dp,
                            color = outcomeColor.copy(alpha = 0.5f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (review.outcome) {
                        ReviewOutcome.OVERTURNED -> {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = outcomeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        ReviewOutcome.UPHELD -> {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = outcomeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        ReviewOutcome.UMPIRE_CALL -> {
                            Text(
                                text = "UC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = outcomeColor
                            )
                        }
                    }
                }
                
                Column {
                    Text(
                        text = "${review.teamName} - ${review.playerName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${review.reviewType.name} • Over ${review.overNumber}",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = outcomeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = outcomeColor
                )
                Text(
                    text = "Review #${review.reviewNumber}",
                    fontSize = 9.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/**
 * Compact DRS display for match header
 */
@Composable
fun CompactDRSDisplay(
    team1Name: String,
    team1Reviews: Int,
    team2Name: String,
    team2Reviews: Int,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .background(
                color = colors.statusInfo.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = colors.statusInfo.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DRS",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = colors.statusInfo
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = team1Name.take(3).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
            Text(
                text = "$team1Reviews",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (team1Reviews > 0) colors.statusSuccess else colors.statusLive
            )
        }
        
        Text(
            text = "|",
            fontSize = 12.sp,
            color = colors.glassBorder
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = team2Name.take(3).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
            Text(
                text = "$team2Reviews",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (team2Reviews > 0) colors.statusSuccess else colors.statusLive
            )
        }
    }
}
