package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.GoldLight

/**
 * Match timeline event types
 */
sealed class PremiumTimelineEvent {
    data class Boundary(val runs: Int, val playerName: String, val over: String) : PremiumTimelineEvent()
    data class Wicket(val playerName: String, val howOut: String, val score: String, val over: String) : PremiumTimelineEvent()
    data class Milestone(val playerName: String, val milestone: String, val over: String) : PremiumTimelineEvent()
    data class PowerPlay(val type: String, val over: String) : PremiumTimelineEvent()
    data class DrinkBreak(val over: String) : PremiumTimelineEvent()
    data class InningsEnd(val team: String, val score: String) : PremiumTimelineEvent()
}

/**
 * Premium match timeline display
 */
@Composable
fun PremiumMatchTimeline(
    events: List<PremiumTimelineEvent>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(events, key = { it.hashCode() }) { event ->
            PremiumTimelineEventItem(event = event)
        }
    }
}

@Composable
private fun PremiumTimelineEventItem(event: PremiumTimelineEvent) {
    val infiniteTransition = rememberInfiniteTransition(label = "timeline")
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        // Event indicator
        when (event) {
            is PremiumTimelineEvent.Boundary -> {
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "boundaryGlow"
                )
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            spotColor = if (event.runs == 6) hierarchy.secondary.primary.copy(alpha = glowAlpha)
                            else colors.statusInfo.copy(alpha = glowAlpha)
                        )
                        .background(
                            if (event.runs == 6) hierarchy.secondary.primary else colors.statusInfo,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = event.runs.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.backgroundBase
                    )
                }
            }
            
            is PremiumTimelineEvent.Wicket -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.statusLive, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "W",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                }
            }
            
            is PremiumTimelineEvent.Milestone -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.statusInfo, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            is PremiumTimelineEvent.PowerPlay -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.statusInfo, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }
            
            is PremiumTimelineEvent.DrinkBreak -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.glassBorderHighlight, CircleShape)
                        .border(1.dp, colors.glassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalDrink,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            is PremiumTimelineEvent.InningsEnd -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = colors.backgroundBase,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Connecting line
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(colors.glassBorder.copy(alpha = 0.3f))
        )
        
        // Event label
        Text(
            text = when (event) {
                is PremiumTimelineEvent.Boundary -> event.over
                is PremiumTimelineEvent.Wicket -> event.over
                is PremiumTimelineEvent.Milestone -> event.over
                is PremiumTimelineEvent.PowerPlay -> event.over
                is PremiumTimelineEvent.DrinkBreak -> event.over
                is PremiumTimelineEvent.InningsEnd -> "END"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary
        )
    }
}

enum class CelebrationType {
    SIX, FOUR, WICKET, MILESTONE, VICTORY
}

private data class CelebrationParticle(
    val x: Float = Random.nextFloat(),
    val y: Float = Random.nextFloat() * 0.3f,
    val size: Dp = (4 + Random.nextInt(8)).dp,
    val color: Color = listOf(
        GoldPrimary,
        GoldLight,
        GoldPrimary,
        StatusInfo,
        StatusSuccess,
        StatusInfo
    ).random(),
    val angle: Float = Random.nextFloat() * 2f - 1f
)

/**
 * Win probability arc display
 */
@Composable
fun WinProbabilityArc(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: Color = BouncerDesignSystem.colors.statusSuccess,
    team2Color: Color = BouncerDesignSystem.colors.statusInfo,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val animatedProbability by animateFloatAsState(
        targetValue = team1Probability.coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "probability"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val arcSize = size.toPx() - strokeWidth
            
            // Team 2 arc (background)
            drawArc(
                color = team2Color,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Team 1 arc (foreground)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        team1Color.copy(alpha = 0.7f),
                        team1Color,
                        team1Color.copy(alpha = 0.8f)
                    )
                ),
                startAngle = -90f,
                sweepAngle = animatedProbability * 360f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        
        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${(animatedProbability * 100).toInt()}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = team1Color
            )
            Text(
                text = team1Name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
    }
}

/**
 * Match momentum indicator
 */
@Composable
fun MomentumIndicator(
    momentum: Float, // -1 to 1, negative = team2, positive = team1
    team1Name: String,
    team2Name: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val animatedMomentum by animateFloatAsState(
        targetValue = momentum.coerceIn(-1f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "momentum"
    )
    
    val indicatorPosition = (animatedMomentum + 1f) / 2f

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "MOMENTUM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )
            
            // Team names
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = team2Name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (animatedMomentum < 0) colors.statusInfo else colors.textTertiary
                )
                Text(
                    text = team1Name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (animatedMomentum > 0) colors.statusSuccess else colors.textTertiary
                )
            }
            
            // Momentum bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.statusInfo,
                                colors.glassBackground,
                                colors.statusSuccess
                            )
                        )
                    )
            ) {
                // Indicator
                Box(
                    modifier = Modifier
                        .offset(x = (indicatorPosition * 100).dp - 8.dp)
                        .size(16.dp)
                        .shadow(8.dp, CircleShape, spotColor = hierarchy.secondary.primary.copy(alpha = 0.5f))
                        .background(hierarchy.secondary.primary, CircleShape)
                        .border(2.dp, colors.textPrimary, CircleShape)
                )
            }
        }
    }
}

/**
 * Match prediction card
 */
@Composable
fun MatchPredictionCard(
    team1Name: String,
    team2Name: String,
    team1WinChance: Float,
    team1Logo: String? = null,
    team2Logo: String? = null,
    prediction: String = "",
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val team2WinChance = 1f - team1WinChance
    
    val animatedTeam1 by animateFloatAsState(
        targetValue = team1WinChance,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team1"
    )
    
    val animatedTeam2 by animateFloatAsState(
        targetValue = team2WinChance,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team2"
    )

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "WIN PREDICTION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.5.sp
            )
            
            // Teams comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(animatedTeam1 * 100).toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = if (team1WinChance > 0.5f) ChartGreen else colors.textPrimary
                    )
                    Text(
                        text = team1Name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                }
                
                // VS
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.glassBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textTertiary
                    )
                }
                
                // Team 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(animatedTeam2 * 100).toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = if (team2WinChance > 0.5f) colors.statusInfo else colors.textPrimary
                    )
                    Text(
                        text = team2Name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                }
            }
            
            // Progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(animatedTeam1.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(ChartGreen)
                )
                Box(
                    modifier = Modifier
                        .weight(animatedTeam2.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(colors.statusInfo)
                )
            }
            
            // Prediction text
            if (prediction.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            hierarchy.secondary.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = prediction,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = hierarchy.secondary.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Innings comparison card
 */
@Composable
fun InningsComparisonCard(
    innings1Score: Int,
    innings1Overs: Float,
    innings2Score: Int,
    innings2Overs: Float,
    innings1Label: String = "1st Innings",
    innings2Label: String = "2nd Innings",
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val maxScore = maxOf(innings1Score, innings2Score).coerceAtLeast(1)
    
    val animatedInnings1 by animateFloatAsState(
        targetValue = innings1Score.toFloat() / maxScore,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "innings1"
    )
    
    val animatedInnings2 by animateFloatAsState(
        targetValue = innings2Score.toFloat() / maxScore,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "innings2"
    )

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "INNINGS COMPARISON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )
            
            // Innings 1
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = innings1Label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "$innings1Score (${innings1Overs} ov)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ChartGreen
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.glassBorder.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedInnings1)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ChartGreen)
                    )
                }
            }
            
            // Innings 2
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = innings2Label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "$innings2Score (${innings2Overs} ov)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.statusInfo
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.glassBorder.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedInnings2)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.statusInfo)
                    )
                }
            }
            
            // Difference
            val difference = innings2Score - innings1Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when {
                        difference > 0 -> "Lead by $difference runs"
                        difference < 0 -> "Need ${-difference} runs"
                        else -> "Scores level"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        difference > 0 -> ChartGreen
                        difference < 0 -> colors.statusInfo
                        else -> hierarchy.secondary.primary
                    }
                )
            }
        }
    }
}
