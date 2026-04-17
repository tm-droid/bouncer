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
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
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
        // Front side - Photo
        if (rotation <= 90f) {
            FlipCardFront(
                playerName = playerName,
                playerRole = playerRole,
                imageUrl = imageUrl,
                matches = matches,
                isCaptain = isCaptain,
                isKeeper = isKeeper
            )
        }
        
        // Back side - Stats
        if (rotation > 90f) {
            Box(
                modifier = Modifier.graphicsLayer { rotationY = 180f }
            ) {
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
    val infiniteTransition = rememberInfiniteTransition(label = "frontCard")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface
    ) {
        Box {
            // Background gradient
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
            
            // Shimmer effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                hierarchy.secondary.primary.copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            start = Offset(shimmer * 500f - 150f, 0f),
                            end = Offset(shimmer * 500f + 150f, 300f)
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Player photo
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            spotColor = hierarchy.secondary.primary.copy(alpha = 0.4f)
                        )
                        .border(
                            3.dp,
                            Brush.linearGradient(
                                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                            ),
                            CircleShape
                        )
                        .clip(CircleShape)
                        .background(colors.glassBorderHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        CoilImage(
                            model = imageUrl,
                            contentDescription = playerName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = playerName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                            color = hierarchy.secondary.primary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Player name with badges
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
                    
                    if (isCaptain) {
                        Box(
                            modifier = Modifier
                                .background(hierarchy.secondary.primary, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "C",
                                color = colors.backgroundBase,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    
                    if (isKeeper) {
                        Box(
                            modifier = Modifier
                                .background(colors.statusInfo, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "WK",
                                color = colors.textPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
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
                    color = hierarchy.secondary.primary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
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
            // Header
            Text(
                text = playerName.uppercase(),
                color = hierarchy.secondary.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Batting stats
            if (runs > 0 || average > 0) {
                Text(
                    text = "BATTING",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn(value = runs.toString(), label = "RUNS", color = hierarchy.secondary.primary)
                    StatColumn(value = String.format("%.1f", average), label = "AVG", color = ChartGreen)
                    StatColumn(value = String.format("%.1f", strikeRate), label = "SR", color = colors.statusInfo)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Bowling stats
            if (wickets > 0 || economy > 0) {
                Text(
                    text = "BOWLING",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn(value = wickets.toString(), label = "WKTS", color = colors.statusInfo)
                    StatColumn(value = String.format("%.1f", bowlingAverage), label = "AVG", color = ChartCyan)
                    StatColumn(value = String.format("%.2f", economy), label = "ECON", color = ChartOrange)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // View more button
            if (onPlayerClick != null) {
                TextButton(
                    onClick = onPlayerClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "View Full Profile",
                        color = hierarchy.secondary.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = hierarchy.secondary.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
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
            val angleStep = 360f / 5
            
            // Draw grid circles
            for (i in 1..5) {
                val gridRadius = radius * i / 5
                drawCircle(
                    color = GlassBorder.copy(alpha = 0.3f),
                    radius = gridRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            // Draw axis lines
            for (i in 0 until 5) {
                val angle = Math.toRadians((angleStep * i - 90).toDouble())
                val endX = center.x + radius * cos(angle).toFloat()
                val endY = center.y + radius * sin(angle).toFloat()
                
                drawLine(
                    color = GlassBorder.copy(alpha = 0.5f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            // Draw skill polygon
            val path = Path()
            animatedSkills.forEachIndexed { index, (_, skill) ->
                val angle = Math.toRadians((angleStep * index - 90).toDouble())
                val skillRadius = radius * skill
                val x = center.x + skillRadius * cos(angle).toFloat()
                val y = center.y + skillRadius * sin(angle).toFloat()
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
            
            // Draw filled polygon
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
            
            // Draw polygon outline
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Draw points
            animatedSkills.forEachIndexed { index, (_, skill) ->
                val angle = Math.toRadians((angleStep * index - 90).toDouble())
                val skillRadius = radius * skill
                val x = center.x + skillRadius * cos(angle).toFloat()
                val y = center.y + skillRadius * sin(angle).toFloat()
                
                drawCircle(
                    color = primaryColor,
                    radius = 6.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Surface1,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        
        // Labels around the chart
        skills.forEachIndexed { index, (label, skill) ->
            val angleStep = 360f / 5
            val angle = Math.toRadians((angleStep * index - 90).toDouble())
            val labelRadius = chartSize.value / 2 - 10
            
            val offsetX = (labelRadius * cos(angle)).dp
            val offsetY = (labelRadius * sin(angle)).dp
            
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${label.take(3).uppercase()}",
                    color = colors.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with avatars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player 1
                PlayerComparisonHeader(
                    name = player1Name,
                    imageUrl = player1ImageUrl,
                    color = ChartGreen
                )
                
                // VS badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.glassBorderHighlight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = hierarchy.secondary.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                // Player 2
                PlayerComparisonHeader(
                    name = player2Name,
                    imageUrl = player2ImageUrl,
                    color = colors.statusInfo
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Comparison bars
            ComparisonBar(
                label = "RUNS",
                value1 = player1Stats.runs.toFloat(),
                value2 = player2Stats.runs.toFloat(),
                displayValue1 = player1Stats.runs.toString(),
                displayValue2 = player2Stats.runs.toString()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ComparisonBar(
                label = "AVERAGE",
                value1 = player1Stats.average,
                value2 = player2Stats.average,
                displayValue1 = String.format("%.1f", player1Stats.average),
                displayValue2 = String.format("%.1f", player2Stats.average)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ComparisonBar(
                label = "STRIKE RATE",
                value1 = player1Stats.strikeRate,
                value2 = player2Stats.strikeRate,
                displayValue1 = String.format("%.1f", player1Stats.strikeRate),
                displayValue2 = String.format("%.1f", player2Stats.strikeRate)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ComparisonBar(
                label = "WICKETS",
                value1 = player1Stats.wickets.toFloat(),
                value2 = player2Stats.wickets.toFloat(),
                displayValue1 = player1Stats.wickets.toString(),
                displayValue2 = player2Stats.wickets.toString()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ComparisonBar(
                label = "ECONOMY",
                value1 = player1Stats.economy,
                value2 = player2Stats.economy,
                displayValue1 = String.format("%.2f", player1Stats.economy),
                displayValue2 = String.format("%.2f", player2Stats.economy),
                lowerIsBetter = true
            )
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
        Box(
            modifier = Modifier
                .size(50.dp)
                .border(2.dp, color, CircleShape)
                .clip(CircleShape)
                .background(colors.glassBorderHighlight),
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
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
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
            // Player 1 value
            Text(
                text = displayValue1,
                color = if (isPlayer1Better || value1 == value2) ChartGreen else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = if (isPlayer1Better) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(50.dp)
            )
            
            // Player 1 bar (right to left)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedValue1)
                        .align(Alignment.CenterEnd)
                        .background(
                            if (isPlayer1Better) ChartGreen else ChartGreen.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
            
            // Spacer/divider
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(12.dp)
                    .background(colors.glassBorder, RoundedCornerShape(2.dp))
            )
            
            // Player 2 bar (left to right)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedValue2)
                        .align(Alignment.CenterStart)
                        .background(
                            if (!isPlayer1Better && value1 != value2) colors.statusInfo else colors.statusInfo.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
            
            // Player 2 value
            Text(
                text = displayValue2,
                color = if (!isPlayer1Better && value1 != value2) colors.statusInfo else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = if (!isPlayer1Better && value1 != value2) FontWeight.Bold else FontWeight.Normal,
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
private fun AchievementBadge(
    achievement: PlayerAchievement
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var showAnimation by remember { mutableStateOf(false) }
    
    LaunchedEffect(achievement.isUnlocked) {
        if (achievement.isUnlocked) {
            showAnimation = true
        }
    }
    
    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        ),
        label = "badgeScale"
    )
    
    val glow by animateFloatAsState(
        targetValue = if (achievement.isUnlocked) 1f else 0f,
        animationSpec = tween(1000),
        label = "glow"
    )
    
    val tierColor = when (achievement.tier) {
        AchievementTier.BRONZE -> hierarchy.secondary.primary
        AchievementTier.SILVER -> Color(0xFFC0C0C0)
        AchievementTier.GOLD -> hierarchy.secondary.primary
        AchievementTier.PLATINUM -> Color(0xFFE5E4E2)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Glow effect
            if (achievement.isUnlocked) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer { alpha = glow * 0.5f }
                        .blur(8.dp)
                        .background(tierColor, CircleShape)
                )
            }
            
            // Badge
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val maxScore = recentScores.maxOrNull()?.coerceAtLeast(50) ?: 50
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.glassSurface
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (showLabels) {
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
                    
                    // Form rating
                    val avgScore = if (recentScores.isNotEmpty()) recentScores.average() else 0.0
                    val formLabel = when {
                        avgScore >= 50 -> "EXCELLENT"
                        avgScore >= 30 -> "GOOD"
                        avgScore >= 15 -> "AVERAGE"
                        else -> "POOR"
                    }
                    val formColor = when {
                        avgScore >= 50 -> StatusSuccess
                        avgScore >= 30 -> colors.statusInfo
                        avgScore >= 15 -> StatusWarning
                        else -> StatusLive
                    }
                    
                    Text(
                        text = formLabel,
                        color = formColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                recentScores.takeLast(5).forEach { score ->
                    val heightFraction = (score.toFloat() / maxScore).coerceIn(0.1f, 1f)
                    
                    val barColor = when {
                        score >= 100 -> hierarchy.secondary.primary
                        score >= 50 -> StatusSuccess
                        score >= 30 -> colors.statusInfo
                        score > 0 -> colors.textSecondary
                        else -> StatusLive
                    }
                    
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
            }
        }
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    val dsColors = BouncerDesignSystem.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = teamColor.copy(alpha = 0.2f))
                    ) {
                        haptic.click()
                        onClick()
                    }
                } else Modifier
            ),
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
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(1.5.dp, teamColor.copy(alpha = 0.5f), CircleShape)
                    .clip(CircleShape)
                    .background(dsColors.glassSurface),
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
                        color = teamColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
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
            
            // Stat
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
            
            // Arrow if clickable
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
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            Brush.linearGradient(
                colors = listOf(
                    hierarchy.secondary.primary,
                    hierarchy.secondary.primary,
                    hierarchy.secondary.primary
                )
            )
        )
    ) {
        Box {
            // Shimmer overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                hierarchy.secondary.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            start = Offset(shimmer * 500f - 100f, 0f),
                            end = Offset(shimmer * 500f + 100f, 200f)
                        )
                    )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Player image
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .shadow(12.dp, CircleShape, spotColor = hierarchy.secondary.primary.copy(alpha = 0.5f))
                        .border(3.dp, hierarchy.secondary.primary, CircleShape)
                        .clip(CircleShape)
                        .background(colors.glassBorderHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        CoilImage(
                            model = imageUrl,
                            contentDescription = playerName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = playerName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                            color = hierarchy.secondary.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
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
                
                // Milestone badge
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                            ),
                            RoundedCornerShape(12.dp)
                        )
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
        }
    }
}
