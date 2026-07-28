package com.tanush.bouncer.ui.components

import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.MatchProjections
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.tanush.bouncer.ui.theme.GoldPrimary

private fun String.toPlayerInitials(): String =
    split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

@Composable
private fun GlassSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = GlassSurface,
        border = border,
        content = content
    )
}

@Composable
private fun PlayerAvatar(
    name: String,
    imageUrl: String?,
    highlighted: Boolean,
    highlightColor: Color,
    glow: Float = 0f,
    size: Dp = 48.dp,
    textColor: Color
) {
    val shadowMod = if (highlighted && glow > 0f) {
        Modifier.shadow(8.dp, CircleShape, spotColor = highlightColor.copy(alpha = glow))
    } else Modifier
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassSurface)
            .then(shadowMod),
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
                text = name.toPlayerInitials(),
                color = textColor,
                fontSize = (size.value * 0.33).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun DrawScope.drawMiniBars(
    values: List<Float>,
    colorFor: (Float) -> Color,
    alpha: Float = 1f
) {
    if (values.isEmpty()) return
    val gap = 2.dp.toPx()
    val barWidth = size.width / values.size - gap
    val maxVal = values.max().coerceAtLeast(1f)
    values.forEachIndexed { index, v ->
        val barHeight = (v / maxVal * size.height).coerceAtLeast(2.dp.toPx())
        val x = index * (barWidth + gap)
        drawRoundRect(
            color = colorFor(v).copy(alpha = alpha),
            topLeft = Offset(x, size.height - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

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
    val colors = BouncerDesignSystem.colors
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
            FlipDigit(
                currentDigit = digit,
                previousDigit = previousDigit,
                isFlipping = digit != previousDigit && isFlipping,
                size = digitSize.dp
            )
        }

        if (wickets != null) {
            Text(
                text = "/",
                color = colors.textSecondary,
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
    val hierarchy = BouncerDesignSystem.hierarchy
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
            .background(GlassSurface, RoundedCornerShape(8.dp))
            .border(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (rotation > 90f) previousDigit.toString() else currentDigit.toString(),
            color = hierarchy.secondary.primary,
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
    val hierarchy = BouncerDesignSystem.hierarchy
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
        color = GlassSurface
    ) {
        Box {
            ShimmerOverlay(shimmer = shimmer, accentColor = hierarchy.secondary.primary)
            ScoreDisplayContent(
                runs = runs,
                wickets = wickets,
                overs = overs,
                teamName = teamName
            )
        }
    }
}

@Composable
private fun ShimmerOverlay(shimmer: Float, accentColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startX = shimmer * 1000f - 200f,
                    endX = shimmer * 1000f + 200f
                )
            )
    )
}

@Composable
private fun ScoreDisplayContent(
    runs: Int,
    wickets: Int,
    overs: String,
    teamName: String
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (teamName.isNotEmpty()) {
            Text(
                text = teamName.uppercase(),
                color = colors.textSecondary,
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
            color = colors.textMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
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
    scrollSpeed: Int = 50
) {
    val colors = BouncerDesignSystem.colors
    val scrollState = rememberScrollState()
    val isAutoScrolling by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isAutoScrolling, partnerships) {
        if (isAutoScrolling && partnerships.isNotEmpty()) {
            while (true) {
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
                scope.launch {
                    scrollState.animateScrollTo(0, animationSpec = tween(1000, easing = FastOutSlowInEasing))
                }.join()
                delay(2000)
            }
        }
    }

    GlassSurfaceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            PartnershipsHeader(count = partnerships.size, colors = colors)
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

@Composable
private fun PartnershipsHeader(count: Int, colors: BouncerColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PARTNERSHIPS",
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "$count total",
            color = colors.textTertiary,
            fontSize = 10.sp
        )
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val runRate = if (partnership.balls > 0)
        String.format("%.2f", partnership.runs.toFloat() / partnership.balls * 6)
    else "0.00"

    Surface(
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlight) hierarchy.secondary.primary.copy(alpha = 0.1f) else GlassSurface,
        border = if (isHighlight)
            BorderStroke(1.dp, hierarchy.secondary.primary.copy(alpha = 0.3f))
        else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            PartnershipHeaderRow(
                partnershipNumber = partnershipNumber,
                runs = partnership.runs,
                balls = partnership.balls,
                isHighlight = isHighlight
            )
            Spacer(modifier = Modifier.height(8.dp))
            PartnershipContributionBar(
                player1Runs = partnership.player1Runs,
                totalRuns = partnership.runs,
                infoColor = colors.statusInfo
            )
            Spacer(modifier = Modifier.height(8.dp))
            PartnershipPlayersRow(partnership = partnership, infoColor = colors.statusInfo)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "RR: $runRate",
                color = colors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PartnershipHeaderRow(
    partnershipNumber: Int,
    runs: Int,
    balls: Int,
    isHighlight: Boolean
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$partnershipNumber",
            color = colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$runs",
                color = if (isHighlight) hierarchy.secondary.primary else colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "($balls)",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PartnershipContributionBar(
    player1Runs: Int,
    totalRuns: Int,
    infoColor: Color
) {
    val contribution = if (totalRuns > 0)
        player1Runs.toFloat() / totalRuns
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
                .background(infoColor)
        )
    }
}

@Composable
private fun PartnershipPlayersRow(
    partnership: EnhancedPartnershipInfo,
    infoColor: Color
) {
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
            color = infoColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
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
    lineColor: Color = GoldPrimary,
    showCurrentValue: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
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
        SparklineCanvas(
            strikeRates = strikeRates,
            currentRate = currentRate,
            minRate = minRate,
            maxRate = maxRate,
            animatedProgress = animatedProgress,
            lineColor = lineColor,
            dotInnerColor = colors.surface2,
            modifier = Modifier
                .weight(1f)
                .height(height)
        )

        if (showCurrentValue) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.1f", currentRate),
                    color = lineColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SR",
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SparklineCanvas(
    strikeRates: List<Float>,
    currentRate: Float,
    minRate: Float,
    maxRate: Float,
    animatedProgress: Float,
    lineColor: Color,
    dotInnerColor: Color,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
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

        fillPath.lineTo(width * animatedProgress, chartHeight)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.3f),
                    lineColor.copy(alpha = 0.05f)
                )
            )
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        if (strikeRates.isNotEmpty()) {
            val lastX = width * animatedProgress
            val lastY = chartHeight - ((currentRate - minRate) / range * chartHeight)
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
            drawCircle(color = dotInnerColor, radius = 2.dp.toPx(), center = Offset(lastX, lastY))
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
    val hierarchy = BouncerDesignSystem.hierarchy

    val infiniteTransition = rememberInfiniteTransition(label = "batsmanCard")
    val strikeGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "strikeGlow"
    )

    GlassSurfaceCard(
        modifier = modifier,
        border = if (isOnStrike)
            BorderStroke(2.dp, hierarchy.secondary.primary.copy(alpha = strikeGlow))
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BatsmanAvatarColumn(
                name = name,
                imageUrl = imageUrl,
                isOnStrike = isOnStrike,
                strikeGlow = strikeGlow
            )
            BatsmanInfoColumn(
                name = name,
                isOnStrike = isOnStrike,
                isOut = isOut,
                dismissalInfo = dismissalInfo,
                recentScores = recentScores,
                modifier = Modifier.weight(1f)
            )
            BatsmanStatsColumn(
                runs = runs,
                balls = balls,
                fours = fours,
                sixes = sixes
            )
        }
    }
}

@Composable
private fun BatsmanAvatarColumn(
    name: String,
    imageUrl: String?,
    isOnStrike: Boolean,
    strikeGlow: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box {
        PlayerAvatar(
            name = name,
            imageUrl = imageUrl,
            highlighted = isOnStrike,
            highlightColor = hierarchy.secondary.primary,
            glow = strikeGlow,
            textColor = if (isOnStrike) hierarchy.secondary.primary else colors.textSecondary
        )

        if (isOnStrike) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .background(hierarchy.secondary.primary, CircleShape)
                    .border(1.5.dp, GlassSurface, CircleShape)
            )
        }
    }
}

@Composable
private fun BatsmanInfoColumn(
    name: String,
    isOnStrike: Boolean,
    isOut: Boolean,
    dismissalInfo: String?,
    recentScores: List<Int>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = name,
                color = if (isOut) colors.textSecondary else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isOnStrike) {
                Text(
                    text = "*",
                    color = hierarchy.secondary.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        when {
            isOut && dismissalInfo != null -> {
                Text(
                    text = dismissalInfo,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            recentScores.isNotEmpty() -> {
                MiniFormSparkline(
                    scores = recentScores,
                    modifier = Modifier.height(16.dp)
                )
            }
        }
    }
}

private fun runsColor(
    runs: Int,
    accent: Color,
    success: Color,
    primary: Color
): Color = when {
    runs >= 100 -> accent
    runs >= 50 -> success
    else -> primary
}

@Composable
private fun BatsmanStatsColumn(
    runs: Int,
    balls: Int,
    fours: Int,
    sixes: Int
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val strikeRate = if (balls > 0) (runs * 100.0 / balls) else 0.0

    Column(horizontalAlignment = Alignment.End) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$runs",
                color = runsColor(
                    runs = runs,
                    accent = hierarchy.secondary.primary,
                    success = colors.statusSuccess,
                    primary = colors.textPrimary
                ),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "($balls)",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fours > 0) {
                Text(
                    text = "${fours}x4",
                    color = colors.statusInfo,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (sixes > 0) {
                Text(
                    text = "${sixes}x6",
                    color = hierarchy.secondary.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "SR ${String.format("%.1f", strikeRate)}",
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MiniFormSparkline(
    scores: List<Int>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val maxScore = scores.maxOrNull()?.coerceAtLeast(50) ?: 50
    val values = scores.map { it.toFloat() }

    Canvas(modifier = modifier.width(60.dp)) {
        val gap = 2.dp.toPx()
        val barWidth = size.width / values.size - gap
        values.forEachIndexed { index, v ->
            val barHeight = (v / maxScore * size.height).coerceAtLeast(2.dp.toPx())
            val x = index * (barWidth + gap)
            val color = when {
                v >= 50 -> colors.statusSuccess
                v >= 30 -> colors.statusInfo
                v >= 10 -> colors.textSecondary
                else -> colors.textMuted
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
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
    val colors = BouncerDesignSystem.colors
    // "3.4 overs" is 3 overs and 4 balls, not 3.4 of them — dividing by the decimal overstates
    // economy by ~3% mid-over.
    val economy = CricketConstants.ballsFromOvers(overs)
        ?.let { MatchProjections.currentRunRate(runs, it)?.toFloat() }
        ?: 0f

    val infiniteTransition = rememberInfiniteTransition(label = "bowlerCard")
    val bowlerGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "bowlerGlow"
    )

    GlassSurfaceCard(
        modifier = modifier,
        border = if (isCurrentBowler) BorderStroke(2.dp, ChartCyan.copy(alpha = bowlerGlow)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlayerAvatar(
                name = name,
                imageUrl = imageUrl,
                highlighted = isCurrentBowler,
                highlightColor = ChartCyan,
                glow = bowlerGlow,
                textColor = if (isCurrentBowler) ChartCyan else colors.textSecondary
            )

            BowlerInfoColumn(
                name = name,
                overs = overs,
                economy = economy,
                economyHistory = economyHistory,
                modifier = Modifier.weight(1f)
            )

            BowlerStatsColumn(
                wickets = wickets,
                runs = runs,
                maidens = maidens,
                economy = economy
            )
        }
    }
}

@Composable
private fun BowlerInfoColumn(
    name: String,
    overs: String,
    economy: Float,
    economyHistory: List<Float>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(modifier = modifier) {
        Text(
            text = name,
            color = colors.textPrimary,
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
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun wicketsColor(
    wickets: Int,
    accent: Color,
    success: Color,
    primary: Color
): Color = when {
    wickets >= 5 -> accent
    wickets >= 3 -> success
    else -> primary
}

private fun economyColor(
    economy: Float,
    success: Color,
    neutral: Color,
    bad: Color
): Color = when {
    economy < 4 -> success
    economy < 8 -> neutral
    else -> bad
}

@Composable
private fun BowlerStatsColumn(
    wickets: Int,
    runs: Int,
    maidens: Int,
    economy: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    Column(horizontalAlignment = Alignment.End) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "$wickets",
                color = wicketsColor(
                    wickets = wickets,
                    accent = hierarchy.secondary.primary,
                    success = colors.statusSuccess,
                    primary = colors.textPrimary
                ),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "-$runs",
                color = colors.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (maidens > 0) {
                Text(
                    text = "M:$maidens",
                    color = hierarchy.secondary.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Econ ${String.format("%.2f", economy)}",
                color = economyColor(
                    economy = economy,
                    success = colors.statusSuccess,
                    neutral = colors.textSecondary,
                    bad = colors.statusLive
                ),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EconomyMiniGraph(
    economies: List<Float>,
    currentEconomy: Float,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val maxEcon = economies.maxOrNull()?.coerceAtLeast(12f) ?: 12f
    val goodThreshold = 6f

    Canvas(modifier = modifier) {
        val gap = 2.dp.toPx()
        val barWidth = size.width / economies.size - gap

        economies.forEachIndexed { index, econ ->
            val barHeight = (econ / maxEcon * size.height).coerceAtLeast(2.dp.toPx())
            val x = index * (barWidth + gap)
            val color = when {
                econ < goodThreshold -> colors.statusSuccess
                econ < 8f -> colors.statusWarning
                else -> colors.statusLive
            }
            drawRoundRect(
                color = color.copy(alpha = 0.7f),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

        val thresholdY = size.height - (goodThreshold / maxEcon * size.height)
        drawLine(
            color = colors.statusSuccess.copy(alpha = 0.5f),
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    GlassSurfaceCard(modifier = modifier, cornerRadius = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(GlassSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$overNumber",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                balls.take(6).forEach { ball ->
                    MicroBallChip(ball = ball)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$runs",
                    color = hierarchy.secondary.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )

                if (wickets > 0) {
                    Box(
                        modifier = Modifier
                            .background(colors.statusLive.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${wickets}W",
                            color = colors.statusLive,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private data class BallChipColors(val bg: Color, val text: Color)

@Composable
private fun ballChipColors(ball: String): BallChipColors {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    return when {
        ball.contains("W", ignoreCase = true) -> BallChipColors(colors.statusLive, Color.White)
        ball == "6" -> BallChipColors(hierarchy.secondary.primary, colors.backgroundBase)
        ball == "4" -> BallChipColors(colors.statusInfo, colors.backgroundBase)
        ball == "0" || ball == "." -> BallChipColors(DotBall, colors.textMuted)
        else -> BallChipColors(GlassSurface, colors.textPrimary)
    }
}

@Composable
private fun MicroBallChip(ball: String) {
    val chipColors = ballChipColors(ball)

    Box(
        modifier = Modifier
            .size(22.dp)
            .background(chipColors.bg, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (ball == "0" || ball == ".") "•" else ball.take(1),
            color = chipColors.text,
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
    val colors = BouncerDesignSystem.colors
    val scrollState = rememberScrollState()

    GlassSurfaceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FALL OF WICKETS",
                color = colors.textMuted,
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
                        WicketTimelineConnector(connectorColor = colors.statusLive)
                    }
                }
            }
        }
    }
}

@Composable
private fun WicketTimelineConnector(connectorColor: Color) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(2.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        connectorColor.copy(alpha = 0.5f),
                        connectorColor.copy(alpha = 0.2f)
                    )
                )
            )
    )
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
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.statusLive.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, colors.statusLive.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${wicket.score}",
                color = colors.statusLive,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "$wicketNumber-${wicket.score}",
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = wicket.playerName.split(" ").lastOrNull() ?: wicket.playerName,
            color = colors.textMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = wicket.overs,
            color = colors.textTertiary,
            fontSize = 9.sp
        )
    }
}
