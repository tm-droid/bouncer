package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback
import kotlin.math.*
import com.tanush.bouncer.ui.theme.GoldPrimary

private const val RADAR_AXES = 5
private val RADAR_ANGLE_STEP = 360f / RADAR_AXES

private fun String.toPlayerInitials(): String =
    split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

@Composable
private fun PlayerAvatar(
    name: String,
    imageUrl: String?,
    size: Dp,
    borderWidth: Dp,
    borderColor: Color,
    background: Color,
    initialsColor: Color,
    initialsSize: androidx.compose.ui.unit.TextUnit,
    initialsWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .border(borderWidth, borderColor, CircleShape)
            .clip(CircleShape)
            .background(background),
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
                color = initialsColor,
                fontSize = initialsSize,
                fontWeight = initialsWeight
            )
        }
    }
}

/**
 * FlipCardPlayerStats - 3D flip card between front (photo) and back (stats)
 * Interactive player card with flip animation for detailed stats view
 */
@Composable
fun FlipCardPlayerStats(
    playerName: String,
    playerRole: String,
    imageUrl: String? = null,
    runs: Int = 0,
    average: Float = 0f,
    strikeRate: Float = 0f,
    wickets: Int = 0,
    bowlingAverage: Float = 0f,
    economy: Float = 0f,
    matches: Int = 0,
    isCaptain: Boolean = false,
    isKeeper: Boolean = false,
    onPlayerClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFlipped by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "flipRotation"
    )

    val cardDescription = "Player card for $playerName, $playerRole. Tap to ${if (isFlipped) "see photo" else "see stats"}."

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = cardDescription }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.click()
                isFlipped = !isFlipped
            }
    ) {
        if (rotation <= 90f) {
            FlipCardFront(
                playerName = playerName,
                playerRole = playerRole,
                imageUrl = imageUrl,
                matches = matches,
                isCaptain = isCaptain,
                isKeeper = isKeeper
            )
        } else {
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                FlipCardBack(
                    playerName = playerName,
                    runs = runs,
                    average = average,
                    strikeRate = strikeRate,
                    wickets = wickets,
                    bowlingAverage = bowlingAverage,
                    economy = economy,
                    onPlayerClick = onPlayerClick
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun rememberShimmerProgress(durationMillis: Int, label: String): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "${label}_progress"
    )
}

@Composable
private fun ShimmerOverlay(
    shimmer: Float,
    accent: Color,
    cornerRadius: Dp,
    alpha: Float,
    spread: Float,
    height: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = alpha),
                        Color.Transparent
                    ),
                    start = Offset(shimmer * 500f - spread, 0f),
                    end = Offset(shimmer * 500f + spread, height)
                )
            )
    )
}

@Composable
private fun FlipCardFrontBackground(accent: Color, shimmer: Float) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.surface1.copy(alpha = 0.8f),
                        colors.glassSurface
                    )
                )
            )
    )

    ShimmerOverlay(
        shimmer = shimmer,
        accent = accent,
        cornerRadius = 24.dp,
        alpha = 0.1f,
        spread = 150f,
        height = 300f,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun FlipCardFrontInfo(
    playerName: String,
    playerRole: String,
    matches: Int,
    isCaptain: Boolean,
    isKeeper: Boolean,
    accent: Color
) {
    val colors = BouncerDesignSystem.colors

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = playerName,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (isCaptain) RoleBadge("C", accent, colors.backgroundBase)
        if (isKeeper) RoleBadge("WK", colors.statusInfo, colors.textPrimary)
    }

    Text(
        text = playerRole,
        color = colors.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "$matches MATCHES",
        color = colors.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "TAP TO VIEW STATS",
        color = accent.copy(alpha = 0.7f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun FlipCardFront(
    playerName: String,
    playerRole: String,
    imageUrl: String?,
    matches: Int,
    isCaptain: Boolean,
    isKeeper: Boolean
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val accent = hierarchy.secondary.primary
    val shimmer by rememberShimmerProgress(durationMillis = 3000, label = "frontCard")

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Box {
            FlipCardFrontBackground(accent = accent, shimmer = shimmer)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PlayerAvatar(
                    name = playerName,
                    imageUrl = imageUrl,
                    size = 100.dp,
                    borderWidth = 3.dp,
                    borderColor = accent,
                    background = colors.glassBorderHighlight,
                    initialsColor = accent,
                    initialsSize = 32.sp,
                    initialsWeight = FontWeight.Black,
                    modifier = Modifier.shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        spotColor = accent.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                FlipCardFrontInfo(
                    playerName = playerName,
                    playerRole = playerRole,
                    matches = matches,
                    isCaptain = isCaptain,
                    isKeeper = isKeeper,
                    accent = accent
                )
            }
        }
    }
}

@Composable
private fun StatSectionHeader(text: String) {
    Text(
        text = text,
        color = BouncerDesignSystem.colors.textMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun BattingStatsSection(runs: Int, average: Float, strikeRate: Float, accent: Color) {
    val colors = BouncerDesignSystem.colors
    StatSectionHeader("BATTING")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatColumn(value = runs.toString(), label = "RUNS", color = accent)
        StatColumn(value = String.format("%.1f", average), label = "AVG", color = ChartGreen)
        StatColumn(value = String.format("%.1f", strikeRate), label = "SR", color = colors.statusInfo)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun BowlingStatsSection(wickets: Int, bowlingAverage: Float, economy: Float) {
    val colors = BouncerDesignSystem.colors
    StatSectionHeader("BOWLING")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatColumn(value = wickets.toString(), label = "WKTS", color = colors.statusInfo)
        StatColumn(value = String.format("%.1f", bowlingAverage), label = "AVG", color = ChartCyan)
        StatColumn(value = String.format("%.2f", economy), label = "ECON", color = ChartOrange)
    }
}

@Composable
private fun ViewProfileButton(accent: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "View Full Profile",
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun FlipCardBack(
    playerName: String,
    runs: Int,
    average: Float,
    strikeRate: Float,
    wickets: Int,
    bowlingAverage: Float,
    economy: Float,
    onPlayerClick: (() -> Unit)?
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val accent = hierarchy.secondary.primary

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = playerName.uppercase(),
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (runs > 0 || average > 0) {
                BattingStatsSection(runs = runs, average = average, strikeRate = strikeRate, accent = accent)
            }

            if (wickets > 0 || economy > 0) {
                BowlingStatsSection(wickets = wickets, bowlingAverage = bowlingAverage, economy = economy)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (onPlayerClick != null) {
                ViewProfileButton(accent = accent, onClick = onPlayerClick)
            }

            Text(
                text = "TAP TO FLIP BACK",
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun radarAngleRadians(index: Int): Double =
    Math.toRadians((RADAR_ANGLE_STEP * index - 90).toDouble())

private fun DrawScope.drawRadarGrid(center: Offset, radius: Float) {
    for (i in 1..RADAR_AXES) {
        drawCircle(
            color = GlassBorder.copy(alpha = 0.3f),
            radius = radius * i / RADAR_AXES,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }

    for (i in 0 until RADAR_AXES) {
        val angle = radarAngleRadians(i)
        drawLine(
            color = GlassBorder.copy(alpha = 0.5f),
            start = center,
            end = Offset(
                center.x + radius * cos(angle).toFloat(),
                center.y + radius * sin(angle).toFloat()
            ),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun buildRadarPath(animatedSkills: List<Pair<String, Float>>, center: Offset, radius: Float): Path {
    val path = Path()
    animatedSkills.forEachIndexed { index, (_, skill) ->
        val angle = radarAngleRadians(index)
        val skillRadius = radius * skill
        val x = center.x + skillRadius * cos(angle).toFloat()
        val y = center.y + skillRadius * sin(angle).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawRadarShape(path: Path, primaryColor: Color, center: Offset) {
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.4f),
                primaryColor.copy(alpha = 0.1f)
            ),
            center = center
        )
    )

    drawPath(
        path = path,
        color = primaryColor,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawRadarPoints(
    animatedSkills: List<Pair<String, Float>>,
    center: Offset,
    radius: Float,
    primaryColor: Color
) {
    animatedSkills.forEachIndexed { index, (_, skill) ->
        val angle = radarAngleRadians(index)
        val skillRadius = radius * skill
        val point = Offset(
            center.x + skillRadius * cos(angle).toFloat(),
            center.y + skillRadius * sin(angle).toFloat()
        )
        drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = point)
        drawCircle(color = Surface1, radius = 3.dp.toPx(), center = point)
    }
}

@Composable
private fun RadarAxisLabels(skills: List<Pair<String, Float>>, chartSize: Dp) {
    val colors = BouncerDesignSystem.colors
    skills.forEachIndexed { index, (label, _) ->
        val angle = radarAngleRadians(index)
        val labelRadius = chartSize.value / 2 - 10

        Box(
            modifier = Modifier
                .offset(
                    x = (labelRadius * cos(angle)).dp,
                    y = (labelRadius * sin(angle)).dp
                )
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = label.take(3).uppercase(),
                color = colors.textSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * PerformanceRadarChart - Radar chart for all-rounder visualization
 * Displays player skills across multiple dimensions
 */
@Composable
fun PerformanceRadarChart(
    battingSkill: Float, // 0-100
    bowlingSkill: Float,
    fieldingSkill: Float,
    fitnessSkill: Float,
    experienceSkill: Float,
    playerName: String = "",
    modifier: Modifier = Modifier,
    chartSize: Dp = 200.dp,
    primaryColor: Color = GoldPrimary
) {
    val skills = listOf(
        "Batting" to battingSkill,
        "Bowling" to bowlingSkill,
        "Fielding" to fieldingSkill,
        "Fitness" to fitnessSkill,
        "Experience" to experienceSkill
    )

    val animatedSkills = skills.map { (label, skill) ->
        val animated by animateFloatAsState(
            targetValue = skill / 100f,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
            label = "skill_$label"
        )
        label to animated
    }

    val chartDescription = "$playerName performance: ${skills.joinToString(", ") { "${it.first} ${it.second.toInt()}%" }}"

    Box(
        modifier = modifier
            .size(chartSize)
            .semantics { contentDescription = chartDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = min(size.width, size.height) / 2 - 30.dp.toPx()

            drawRadarGrid(center, radius)

            val path = buildRadarPath(animatedSkills, center, radius)
            drawRadarShape(path, primaryColor, center)
            drawRadarPoints(animatedSkills, center, radius, primaryColor)
        }

        RadarAxisLabels(skills = skills, chartSize = chartSize)
    }
}

@Composable
private fun VersusBadge(accent: Color) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(colors.glassBorderHighlight, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "VS",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private data class ComparisonBarData(
    val label: String,
    val value1: Float,
    val value2: Float,
    val displayValue1: String,
    val displayValue2: String,
    val lowerIsBetter: Boolean = false
)

private fun buildComparisonBars(
    player1Stats: PlayerComparisonStats,
    player2Stats: PlayerComparisonStats
): List<ComparisonBarData> = listOf(
    ComparisonBarData(
        label = "RUNS",
        value1 = player1Stats.runs.toFloat(),
        value2 = player2Stats.runs.toFloat(),
        displayValue1 = player1Stats.runs.toString(),
        displayValue2 = player2Stats.runs.toString()
    ),
    ComparisonBarData(
        label = "AVERAGE",
        value1 = player1Stats.average,
        value2 = player2Stats.average,
        displayValue1 = String.format("%.1f", player1Stats.average),
        displayValue2 = String.format("%.1f", player2Stats.average)
    ),
    ComparisonBarData(
        label = "STRIKE RATE",
        value1 = player1Stats.strikeRate,
        value2 = player2Stats.strikeRate,
        displayValue1 = String.format("%.1f", player1Stats.strikeRate),
        displayValue2 = String.format("%.1f", player2Stats.strikeRate)
    ),
    ComparisonBarData(
        label = "WICKETS",
        value1 = player1Stats.wickets.toFloat(),
        value2 = player2Stats.wickets.toFloat(),
        displayValue1 = player1Stats.wickets.toString(),
        displayValue2 = player2Stats.wickets.toString()
    ),
    ComparisonBarData(
        label = "ECONOMY",
        value1 = player1Stats.economy,
        value2 = player2Stats.economy,
        displayValue1 = String.format("%.2f", player1Stats.economy),
        displayValue2 = String.format("%.2f", player2Stats.economy),
        lowerIsBetter = true
    )
)

/**
 * HeadToHeadComparison - Side-by-side player stats comparison
 * Compares two players across multiple statistics
 */
@Composable
fun HeadToHeadComparison(
    player1Name: String,
    player1Stats: PlayerComparisonStats,
    player2Name: String,
    player2Stats: PlayerComparisonStats,
    player1ImageUrl: String? = null,
    player2ImageUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerComparisonHeader(
                    name = player1Name,
                    imageUrl = player1ImageUrl,
                    color = ChartGreen
                )

                VersusBadge(accent = hierarchy.secondary.primary)

                PlayerComparisonHeader(
                    name = player2Name,
                    imageUrl = player2ImageUrl,
                    color = colors.statusInfo
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val bars = buildComparisonBars(player1Stats, player2Stats)
            bars.forEachIndexed { index, bar ->
                ComparisonBar(
                    label = bar.label,
                    value1 = bar.value1,
                    value2 = bar.value2,
                    displayValue1 = bar.displayValue1,
                    displayValue2 = bar.displayValue2,
                    lowerIsBetter = bar.lowerIsBetter
                )
                if (index < bars.lastIndex) Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

data class PlayerComparisonStats(
    val runs: Int = 0,
    val average: Float = 0f,
    val strikeRate: Float = 0f,
    val wickets: Int = 0,
    val economy: Float = 0f
)

@Composable
private fun PlayerComparisonHeader(
    name: String,
    imageUrl: String?,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        PlayerAvatar(
            name = name,
            imageUrl = imageUrl,
            size = 50.dp,
            borderWidth = 2.dp,
            borderColor = color,
            background = colors.glassBorderHighlight,
            initialsColor = color,
            initialsSize = 16.sp,
            initialsWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name.split(" ").lastOrNull() ?: name,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ComparisonValueBar(
    fillFraction: Float,
    fillColor: Color,
    alignment: Alignment,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fillFraction)
                .align(alignment)
                .background(fillColor, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun ComparisonBar(
    label: String,
    value1: Float,
    value2: Float,
    displayValue1: String,
    displayValue2: String,
    lowerIsBetter: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val maxValue = maxOf(value1, value2).coerceAtLeast(1f)

    val animatedValue1 by animateFloatAsState(
        targetValue = value1 / maxValue,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "bar1"
    )

    val animatedValue2 by animateFloatAsState(
        targetValue = value2 / maxValue,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "bar2"
    )

    val isPlayer1Better = if (lowerIsBetter) value1 < value2 else value1 > value2
    val isPlayer2Better = !isPlayer1Better && value1 != value2

    Column {
        Text(
            text = label,
            color = colors.textMuted,
            letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayValue1,
                color = if (isPlayer1Better || value1 == value2) ChartGreen else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = if (isPlayer1Better) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(50.dp)
            )

            ComparisonValueBar(
                fillFraction = animatedValue1,
                fillColor = if (isPlayer1Better) ChartGreen else ChartGreen.copy(alpha = 0.5f),
                alignment = Alignment.CenterEnd,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(12.dp)
                    .background(colors.glassBorder, RoundedCornerShape(2.dp))
            )

            ComparisonValueBar(
                fillFraction = animatedValue2,
                fillColor = if (isPlayer2Better) colors.statusInfo else colors.statusInfo.copy(alpha = 0.5f),
                alignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = displayValue2,
                color = if (isPlayer2Better) colors.statusInfo else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = if (isPlayer2Better) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
                modifier = Modifier.width(50.dp)
            )
        }
    }
}

/**
 * AchievementBadges - Row with unlock animations
 * Displays player achievements with animated unlock effects
 */
@Composable
fun AchievementBadges(
    achievements: List<PlayerAchievement>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ACHIEVEMENTS",
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                achievements.forEach { achievement ->
                    AchievementBadge(achievement = achievement)
                }
            }
        }
    }
}

data class PlayerAchievement(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Star,
    val isUnlocked: Boolean = true,
    val tier: AchievementTier = AchievementTier.BRONZE
)

enum class AchievementTier {
    BRONZE, SILVER, GOLD, PLATINUM
}

@Composable
private fun tierColorFor(tier: AchievementTier): Color {
    val hierarchy = BouncerDesignSystem.hierarchy
    return when (tier) {
        AchievementTier.BRONZE -> hierarchy.secondary.primary
        AchievementTier.SILVER -> Color(0xFFC0C0C0)
        AchievementTier.GOLD -> hierarchy.secondary.primary
        AchievementTier.PLATINUM -> Color(0xFFE5E4E2)
    }
}

@Composable
private fun AchievementBadgeIcon(
    achievement: PlayerAchievement,
    tierColor: Color,
    scale: Float,
    glow: Float
) {
    val colors = BouncerDesignSystem.colors
    Box(contentAlignment = Alignment.Center) {
        if (achievement.isUnlocked) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { alpha = glow * 0.5f }
                    .blur(8.dp)
                    .background(tierColor, CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (achievement.isUnlocked) 8.dp else 0.dp,
                    shape = CircleShape,
                    spotColor = tierColor.copy(alpha = 0.5f)
                )
                .background(
                    brush = if (achievement.isUnlocked)
                        Brush.radialGradient(
                            colors = listOf(tierColor, tierColor.copy(alpha = 0.7f))
                        )
                    else Brush.linearGradient(
                        colors = listOf(colors.glassBorderHighlight, colors.glassBorderHighlight)
                    ),
                    shape = CircleShape
                )
                .border(
                    2.dp,
                    if (achievement.isUnlocked) Color.White.copy(alpha = 0.3f) else colors.glassBorder,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = achievement.icon,
                contentDescription = achievement.title,
                tint = if (achievement.isUnlocked) colors.backgroundBase else colors.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AchievementBadge(
    achievement: PlayerAchievement
) {
    val colors = BouncerDesignSystem.colors
    var showAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(achievement.isUnlocked) {
        if (achievement.isUnlocked) showAnimation = true
    }

    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "badgeScale"
    )

    val glow by animateFloatAsState(
        targetValue = if (achievement.isUnlocked) 1f else 0f,
        animationSpec = tween(1000),
        label = "glow"
    )

    val tierColor = tierColorFor(achievement.tier)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp)
    ) {
        AchievementBadgeIcon(
            achievement = achievement,
            tierColor = tierColor,
            scale = scale,
            glow = glow
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = achievement.title,
            color = if (achievement.isUnlocked) colors.textPrimary else colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class FormQualitative(val label: String, val color: Color)

@Composable
private fun rememberFormQualitative(avgScore: Double): FormQualitative {
    val colors = BouncerDesignSystem.colors
    return when {
        avgScore >= 50 -> FormQualitative("EXCELLENT", StatusSuccess)
        avgScore >= 30 -> FormQualitative("GOOD", colors.statusInfo)
        avgScore >= 15 -> FormQualitative("AVERAGE", StatusWarning)
        else -> FormQualitative("POOR", StatusLive)
    }
}

@Composable
private fun formBarColor(score: Int): Color {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    return when {
        score >= 100 -> hierarchy.secondary.primary
        score >= 50 -> StatusSuccess
        score >= 30 -> colors.statusInfo
        score > 0 -> colors.textSecondary
        else -> StatusLive
    }
}

@Composable
private fun FormIndicatorHeader(recentScores: List<Int>) {
    val colors = BouncerDesignSystem.colors
    val avgScore = if (recentScores.isNotEmpty()) recentScores.average() else 0.0
    val qualitative = rememberFormQualitative(avgScore)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RECENT FORM",
            color = colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )

        Text(
            text = qualitative.label,
            color = qualitative.color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun RowScope.FormBar(score: Int, maxScore: Int) {
    val heightFraction = (score.toFloat() / maxScore).coerceIn(0.1f, 1f)
    val barColor = formBarColor(score)

    val animatedHeight by animateFloatAsState(
        targetValue = heightFraction,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "formBar"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp * animatedHeight)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(barColor)
        )

        Text(
            text = score.toString(),
            color = barColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * FormIndicator - Runs in last 5 innings sparkline
 * Shows recent form with visual indicators
 */
@Composable
fun FormIndicator(
    recentScores: List<Int>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
    val maxScore = recentScores.maxOrNull()?.coerceAtLeast(50) ?: 50

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.glassSurface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (showLabels) {
                FormIndicatorHeader(recentScores = recentScores)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                recentScores.takeLast(5).forEach { score ->
                    FormBar(score = score, maxScore = maxScore)
                }
            }
        }
    }
}

@Composable
private fun CompactCardInfo(name: String, role: String) {
    val dsColors = BouncerDesignSystem.colors
    Text(
        text = name,
        color = dsColors.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = role,
        color = dsColors.textSecondary,
        fontSize = 11.sp
    )
}

@Composable
private fun CompactCardStat(primaryStat: String, primaryStatLabel: String, teamColor: Color) {
    val dsColors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = primaryStat,
            color = teamColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = primaryStatLabel,
            color = dsColors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * PlayerCompactCard - For lists with key stats
 * Minimal player card for use in player lists
 */
@Composable
fun PlayerCompactCard(
    name: String,
    role: String,
    primaryStat: String,
    primaryStatLabel: String,
    imageUrl: String? = null,
    teamColor: Color = GoldPrimary,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val dsColors = BouncerDesignSystem.colors

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = teamColor.copy(alpha = 0.2f))
        ) {
            haptic.click()
            onClick()
        }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = RoundedCornerShape(14.dp),
        color = dsColors.glassBorderHighlight,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, dsColors.glassBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlayerAvatar(
                name = name,
                imageUrl = imageUrl,
                size = 44.dp,
                borderWidth = 1.5.dp,
                borderColor = teamColor.copy(alpha = 0.5f),
                background = dsColors.glassSurface,
                initialsColor = teamColor,
                initialsSize = 14.sp,
                initialsWeight = FontWeight.Bold
            )

            Column(modifier = Modifier.weight(1f)) {
                CompactCardInfo(name = name, role = role)
            }

            CompactCardStat(
                primaryStat = primaryStat,
                primaryStatLabel = primaryStatLabel,
                teamColor = teamColor
            )

            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View details",
                    tint = dsColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MilestoneBadge(milestone: String, accent: Color) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .background(accent, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = milestone,
            color = colors.backgroundBase,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun MilestonePlayerInfo(playerName: String, matchesStat: String) {
    val colors = BouncerDesignSystem.colors
    Text(
        text = playerName,
        color = colors.textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )

    if (matchesStat.isNotEmpty()) {
        Text(
            text = matchesStat,
            color = colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

/**
 * PlayerMilestoneCard - Displays player milestone achievements during match
 */
@Composable
fun PlayerMilestoneCard(
    playerName: String,
    milestone: String, // e.g., "50 RUNS", "100 RUNS", "5 WICKETS"
    matchesStat: String = "",
    imageUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val accent = hierarchy.secondary.primary
    val infiniteTransition = rememberInfiniteTransition(label = "milestone")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface,
        border = androidx.compose.foundation.BorderStroke(2.dp, accent)
    ) {
        Box {
            ShimmerOverlay(
                shimmer = shimmer,
                accent = accent,
                cornerRadius = 20.dp,
                alpha = 0.15f,
                spread = 100f,
                height = 200f,
                modifier = Modifier.matchParentSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlayerAvatar(
                    name = playerName,
                    imageUrl = imageUrl,
                    size = 60.dp,
                    borderWidth = 3.dp,
                    borderColor = accent,
                    background = colors.glassBorderHighlight,
                    initialsColor = accent,
                    initialsSize = 20.sp,
                    initialsWeight = FontWeight.Black,
                    modifier = Modifier.shadow(12.dp, CircleShape, spotColor = accent.copy(alpha = 0.5f))
                )

                Column(modifier = Modifier.weight(1f)) {
                    MilestonePlayerInfo(playerName = playerName, matchesStat = matchesStat)
                }

                MilestoneBadge(milestone = milestone, accent = accent)
            }
        }
    }
}
