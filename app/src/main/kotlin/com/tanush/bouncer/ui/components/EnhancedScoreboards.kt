package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * FlipDigitScore with flip animation for digit changes
 * Displays score with animated flip transition when digits change
 */
@Composable
fun FlipDigitScore(
    score: Int,
    wickets: Int? = null,
    modifier: Modifier = Modifier,
    digitSize: Int = 40,
    showAnimation: Boolean = true
) {
    val scoreString = score.toString().padStart(3, '0')
    var previousScore by remember { mutableStateOf(score) }
    var isFlipping by remember { mutableStateOf(false) }
    
    LaunchedEffect(score) {
        if (score != previousScore && showAnimation) {
            isFlipping = true
            delay(300)
            previousScore = score
            isFlipping = false
        } else {
            previousScore = score
        }
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scoreString.forEachIndexed { index, digit ->
            val previousDigit = previousScore.toString().padStart(3, '0').getOrElse(index) { '0' }
            val shouldFlip = digit != previousDigit && isFlipping
            
            FlipDigit(
                currentDigit = digit,
                previousDigit = previousDigit,
                isFlipping = shouldFlip,
                size = digitSize.dp
            )
        }
        
        // Wickets display
        if (wickets != null) {
            Text(
                text = "/",
                color = TextSecondary,
                fontSize = (digitSize * 0.8).sp,
                fontWeight = FontWeight.Light
            )
            FlipDigit(
                currentDigit = wickets.toString().first(),
                previousDigit = wickets.toString().first(),
                isFlipping = false,
                size = digitSize.dp
            )
        }
    }
}

@Composable
private fun FlipDigit(
    currentDigit: Char,
    previousDigit: Char,
    isFlipping: Boolean,
    size: Dp
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipping) 180f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "flipRotation"
    )
    
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                rotationX = rotation
                cameraDistance = 12f * density
            }
            .background(
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .border(
                0.5.dp,
                ShinyGoldDeep.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (rotation > 90f) previousDigit.toString() else currentDigit.toString(),
            color = ShinyGoldDeep,
            fontSize = (size.value * 0.6).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer {
                rotationX = if (rotation > 90f) 180f else 0f
            }
        )
    }
}

/**
 * AnimatedScoreDisplay with cascade digit animations
 * Alternative animated score display with sequential animations
 */
@Composable
fun AnimatedScoreDisplay(
    runs: Int,
    wickets: Int,
    overs: String,
    teamName: String = "",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scoreDisplay")
    
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val scoreDescription = "$teamName: $runs runs for $wickets wickets in $overs overs"
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = scoreDescription },
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Box {
            // Shimmer overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ShinyGoldDeep.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startX = shimmer * 1000f - 200f,
                            endX = shimmer * 1000f + 200f
                        )
                    )
            )
            
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (teamName.isNotEmpty()) {
                    Text(
                        text = teamName.uppercase(),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    FlipDigitScore(
                        score = runs,
                        wickets = wickets,
                        digitSize = 48
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "OVERS: $overs",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * AutoScrollingPartnership - Horizontal auto-scrolling partnership details
 * Displays partnership information with auto-scrolling animation
 */
@Composable
fun AutoScrollingPartnership(
    partnerships: List<EnhancedPartnershipInfo>,
    modifier: Modifier = Modifier,
    scrollSpeed: Int = 50 // pixels per second
) {
    val scrollState = rememberScrollState()
    var isAutoScrolling by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(isAutoScrolling, partnerships) {
        if (isAutoScrolling && partnerships.isNotEmpty()) {
            while (true) {
                // Scroll forward
                scope.launch {
                    scrollState.animateScrollTo(
                        scrollState.maxValue,
                        animationSpec = tween(
                            durationMillis = (scrollState.maxValue / scrollSpeed * 1000).coerceAtLeast(2000),
                            easing = LinearEasing
                        )
                    )
                }.join()
                
                delay(2000)
                
                // Scroll back
                scope.launch {
                    scrollState.animateScrollTo(
                        0,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing)
                    )
                }.join()
                
                delay(2000)
            }
        }
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PARTNERSHIPS",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = "${partnerships.size} total",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                partnerships.forEachIndexed { index, partnership ->
                    PartnershipItem(
                        partnership = partnership,
                        partnershipNumber = index + 1,
                        isHighlight = partnership.runs >= 50
                    )
                }
            }
        }
    }
}

data class EnhancedPartnershipInfo(
    val player1: String,
    val player1Runs: Int,
    val player2: String,
    val player2Runs: Int,
    val runs: Int,
    val balls: Int
)

@Composable
private fun PartnershipItem(
    partnership: EnhancedPartnershipInfo,
    partnershipNumber: Int,
    isHighlight: Boolean = false
) {
    val runRate = if (partnership.balls > 0) 
        (partnership.runs.toFloat() / partnership.balls * 6).let { String.format("%.2f", it) }
    else "0.00"
    
    Surface(
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlight) ShinyGoldDeep.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.08f),
        border = if (isHighlight) 
            androidx.compose.foundation.BorderStroke(1.dp, ShinyGoldDeep.copy(alpha = 0.3f))
        else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#$partnershipNumber",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${partnership.runs}",
                        color = if (isHighlight) ShinyGoldDeep else TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "(${partnership.balls})",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Contribution bar
            val contribution = if (partnership.runs > 0) 
                partnership.player1Runs.toFloat() / partnership.runs 
            else 0.5f
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(contribution.coerceAtLeast(0.1f))
                        .fillMaxHeight()
                        .background(ChartGreen)
                )
                Box(
                    modifier = Modifier
                        .weight((1f - contribution).coerceAtLeast(0.1f))
                        .fillMaxHeight()
                        .background(ChartBlue)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${partnership.player1.take(8)}... ${partnership.player1Runs}",
                    color = ChartGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${partnership.player2Runs} ${partnership.player2.take(8)}...",
                    color = ChartBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "RR: $runRate",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * StrikeRateSparkline - Compact performance graph
 * Displays a mini sparkline chart for strike rate over time
 */
@Composable
fun StrikeRateSparkline(
    strikeRates: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    lineColor: Color = ShinyGoldDeep,
    showCurrentValue: Boolean = true
) {
    if (strikeRates.isEmpty()) return
    
    val maxRate = strikeRates.maxOrNull() ?: 200f
    val minRate = strikeRates.minOrNull() ?: 0f
    val currentRate = strikeRates.lastOrNull() ?: 0f
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "sparklineProgress"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(height)
        ) {
            val width = size.width
            val chartHeight = size.height
            val range = (maxRate - minRate).coerceAtLeast(1f)
            
            val path = Path()
            val fillPath = Path()
            
            strikeRates.forEachIndexed { index, rate ->
                val x = (index.toFloat() / (strikeRates.size - 1).coerceAtLeast(1)) * width * animatedProgress
                val y = chartHeight - ((rate - minRate) / range * chartHeight)
                
                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, chartHeight)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            
            // Complete fill path
            fillPath.lineTo(width * animatedProgress, chartHeight)
            fillPath.close()
            
            // Draw gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.3f),
                        lineColor.copy(alpha = 0.05f)
                    )
                )
            )
            
            // Draw line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Draw current point
            if (strikeRates.isNotEmpty()) {
                val lastX = width * animatedProgress
                val lastY = chartHeight - ((currentRate - minRate) / range * chartHeight)
                
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
                drawCircle(
                    color = CardSurface,
                    radius = 2.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }
        
        if (showCurrentValue) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = String.format("%.1f", currentRate),
                    color = lineColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SR",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * BatsmanMiniCard with photo, stats, and form indicator
 * Compact batsman display for scorecards and lists
 */
@Composable
fun BatsmanMiniCard(
    name: String,
    runs: Int,
    balls: Int,
    fours: Int = 0,
    sixes: Int = 0,
    imageUrl: String? = null,
    isOnStrike: Boolean = false,
    isOut: Boolean = false,
    dismissalInfo: String? = null,
    recentScores: List<Int> = emptyList(),
    modifier: Modifier = Modifier
) {
    val strikeRate = if (balls > 0) (runs * 100.0 / balls) else 0.0
    
    val infiniteTransition = rememberInfiniteTransition(label = "batsmanCard")
    val strikeGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strikeGlow"
    )
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isOut) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f),
        border = if (isOnStrike) 
            androidx.compose.foundation.BorderStroke(
                2.dp, 
                ShinyGoldDeep.copy(alpha = strikeGlow)
            ) 
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Player avatar
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .then(
                            if (isOnStrike) Modifier.shadow(8.dp, CircleShape, spotColor = ShinyGoldDeep.copy(alpha = strikeGlow))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        CoilImage(
                            model = imageUrl,
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                            color = if (isOnStrike) ShinyGoldDeep else TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Strike indicator dot
                if (isOnStrike) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd)
                            .background(ShinyGoldDeep, CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                    )
                }
            }
            
            // Name and dismissal info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        color = if (isOut) TextSecondary else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (isOnStrike) {
                        Text(
                            text = "*",
                            color = ShinyGoldDeep,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                if (isOut && dismissalInfo != null) {
                    Text(
                        text = dismissalInfo,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (recentScores.isNotEmpty()) {
                    // Form sparkline
                    MiniFormSparkline(
                        scores = recentScores,
                        modifier = Modifier.height(16.dp)
                    )
                }
            }
            
            // Stats
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$runs",
                        color = when {
                            runs >= 100 -> ShinyGoldDeep
                            runs >= 50 -> SuccessGreen
                            else -> TextPrimary
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "($balls)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (fours > 0) {
                        Text(
                            text = "${fours}x4",
                            color = BoundaryFour,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (sixes > 0) {
                        Text(
                            text = "${sixes}x6",
                            color = BoundarySix,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "SR ${String.format("%.1f", strikeRate)}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniFormSparkline(
    scores: List<Int>,
    modifier: Modifier = Modifier
) {
    val maxScore = scores.maxOrNull()?.coerceAtLeast(50) ?: 50
    
    Canvas(modifier = modifier.width(60.dp)) {
        val barWidth = size.width / scores.size - 2.dp.toPx()
        
        scores.forEachIndexed { index, score ->
            val barHeight = (score.toFloat() / maxScore * size.height).coerceAtLeast(2.dp.toPx())
            val x = index * (barWidth + 2.dp.toPx())
            
            val color = when {
                score >= 50 -> SuccessGreen
                score >= 30 -> ChartBlue
                score >= 10 -> TextSecondary
                else -> TextMuted
            }
            
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

/**
 * BowlerMiniCard with economy graph
 * Compact bowler display with economy visualization
 */
@Composable
fun BowlerMiniCard(
    name: String,
    overs: String,
    maidens: Int,
    runs: Int,
    wickets: Int,
    imageUrl: String? = null,
    isCurrentBowler: Boolean = false,
    economyHistory: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    val economy = run {
        val oversFloat = overs.toFloatOrNull() ?: 0f
        if (oversFloat > 0) runs / oversFloat else 0f
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "bowlerCard")
    val bowlerGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bowlerGlow"
    )
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = if (isCurrentBowler) 
            androidx.compose.foundation.BorderStroke(2.dp, ChartCyan.copy(alpha = bowlerGlow)) 
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Player avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .then(
                        if (isCurrentBowler) Modifier.shadow(8.dp, CircleShape, spotColor = ChartCyan.copy(alpha = bowlerGlow))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    CoilImage(
                        model = imageUrl,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                        color = if (isCurrentBowler) ChartCyan else TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Name and economy graph
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (economyHistory.isNotEmpty()) {
                    EconomyMiniGraph(
                        economies = economyHistory,
                        currentEconomy = economy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )
                } else {
                    Text(
                        text = "$overs overs",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            
            // Stats
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$wickets",
                        color = when {
                            wickets >= 5 -> ShinyGoldDeep
                            wickets >= 3 -> SuccessGreen
                            else -> TextPrimary
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "-$runs",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (maidens > 0) {
                        Text(
                            text = "M:$maidens",
                            color = ShinyGoldMid,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Econ ${String.format("%.2f", economy)}",
                        color = when {
                            economy < 4 -> SuccessGreen
                            economy < 8 -> TextSecondary
                            else -> ErrorRed
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EconomyMiniGraph(
    economies: List<Float>,
    currentEconomy: Float,
    modifier: Modifier = Modifier
) {
    val maxEcon = economies.maxOrNull()?.coerceAtLeast(12f) ?: 12f
    val goodThreshold = 6f
    
    Canvas(modifier = modifier) {
        val barWidth = size.width / economies.size - 2.dp.toPx()
        
        economies.forEachIndexed { index, econ ->
            val barHeight = (econ / maxEcon * size.height).coerceAtLeast(2.dp.toPx())
            val x = index * (barWidth + 2.dp.toPx())
            
            val color = when {
                econ < goodThreshold -> SuccessGreen
                econ < 8f -> WarningYellow
                else -> ErrorRed
            }
            
            drawRoundRect(
                color = color.copy(alpha = 0.7f),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
        
        // Good economy threshold line
        val thresholdY = size.height - (goodThreshold / maxEcon * size.height)
        drawLine(
            color = SuccessGreen.copy(alpha = 0.5f),
            start = Offset(0f, thresholdY),
            end = Offset(size.width, thresholdY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        )
    }
}

/**
 * OverSummaryRow - Compact over-by-over summary
 * Shows over summary with runs, wickets and ball-by-ball breakdown
 */
@Composable
fun OverSummaryRow(
    overNumber: Int,
    balls: List<String>,
    runs: Int,
    wickets: Int = 0,
    bowlerName: String = "",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Over number
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$overNumber",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Balls
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                balls.take(6).forEach { ball ->
                    MicroBallChip(ball = ball)
                }
            }
            
            // Summary
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$runs",
                    color = ShinyGoldDeep,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                
                if (wickets > 0) {
                    Box(
                        modifier = Modifier
                            .background(WicketRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${wickets}W",
                            color = WicketRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MicroBallChip(ball: String) {
    val (bgColor, textColor) = when {
        ball.contains("W", ignoreCase = true) -> WicketRed to Color.White
        ball == "6" -> BoundarySix to BlackBackground
        ball == "4" -> BoundaryFour to BlackBackground
        ball == "0" || ball == "." -> DotBall to TextMuted
        else -> Color.White.copy(alpha = 0.05f) to TextPrimary
    }
    
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(bgColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (ball) {
                "0", "." -> "•"
                else -> ball.take(1)
            },
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * WicketFallTimeline - Visual timeline of wicket falls
 */
@Composable
fun WicketFallTimeline(
    wickets: List<WicketFallInfo>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FALL OF WICKETS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                wickets.forEachIndexed { index, wicket ->
                    WicketFallItem(
                        wicket = wicket,
                        wicketNumber = index + 1
                    )
                    
                    if (index < wickets.size - 1) {
                        // Connector line
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .align(Alignment.CenterVertically)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            WicketRed.copy(alpha = 0.5f),
                                            WicketRed.copy(alpha = 0.2f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

data class WicketFallInfo(
    val playerName: String,
    val score: Int,
    val overs: String,
    val howOut: String = ""
)

@Composable
private fun WicketFallItem(
    wicket: WicketFallInfo,
    wicketNumber: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        // Score at fall
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(WicketRed.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, WicketRed.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${wicket.score}",
                color = WicketRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Wicket number
        Text(
            text = "$wicketNumber-${wicket.score}",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Player name
        Text(
            text = wicket.playerName.split(" ").lastOrNull() ?: wicket.playerName,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Overs
        Text(
            text = wicket.overs,
            color = TextTertiary,
            fontSize = 9.sp
        )
    }
}
