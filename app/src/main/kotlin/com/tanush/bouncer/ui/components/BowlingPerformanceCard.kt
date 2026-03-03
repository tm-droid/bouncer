package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

/**
 * Bowler performance data
 */
data class BowlerPerformance(
    val name: String,
    val imageUrl: String? = null,
    val overs: Float,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Float,
    val dots: Int,
    val wides: Int = 0,
    val noBalls: Int = 0,
    val isBowling: Boolean = false
) {
    val economyCategory: EconomyCategory
        get() = when {
            economy < 5f -> EconomyCategory.EXCELLENT
            economy < 7f -> EconomyCategory.GOOD
            economy < 9f -> EconomyCategory.AVERAGE
            else -> EconomyCategory.EXPENSIVE
        }
}

enum class EconomyCategory(val label: String, val color: Color) {
    EXCELLENT("Excellent", StatusSuccess),
    GOOD("Good", StatusInfo),
    AVERAGE("Average", StatusWarning),
    EXPENSIVE("Expensive", StatusLive)
}

/**
 * Premium Bowling Performance Card
 * Shows detailed bowling analysis with visual economy indicator
 */
@Composable
fun BowlingPerformanceCard(
    bowlers: List<BowlerPerformance>,
    teamName: String,
    teamColor: Color = StatusInfo,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = teamColor.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.3f))
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
                        Icons.Default.SportsCricket,
                        contentDescription = null,
                        tint = teamColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "BOWLING ANALYSIS",
                        color = colors.textPrimary,
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Bowlers list
            bowlers.forEachIndexed { index, bowler ->
                BowlerRow(bowler = bowler, teamColor = teamColor)
                if (index < bowlers.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Team bowling summary
            BowlingSummary(bowlers = bowlers, teamColor = teamColor)
        }
    }
}

/**
 * Individual bowler row
 */
@Composable
private fun BowlerRow(
    bowler: BowlerPerformance,
    teamColor: Color
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "bowler")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (bowler.isBowling) teamColor.copy(alpha = 0.1f) else colors.glassBorderHighlight,
        border = BorderStroke(
            0.5.dp,
            if (bowler.isBowling) teamColor.copy(alpha = pulseAlpha) else colors.glassBorder.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Bowler info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Player image/initial
                    if (bowler.imageUrl != null) {
                        CoilImage(
                            model = bowler.imageUrl,
                            contentDescription = bowler.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(2.dp, teamColor.copy(alpha = 0.3f), CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = teamColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, teamColor.copy(alpha = 0.3f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = bowler.name.take(1),
                                    color = teamColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = bowler.name,
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (bowler.isBowling) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.statusSuccess.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "BOWLING",
                                        color = colors.statusSuccess,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${bowler.overs} ov • ${bowler.maidens} maiden(s)",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
                
                // Figures
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${bowler.wickets}",
                            color = if (bowler.wickets >= 3) colors.goldPrimary else teamColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "/${bowler.runs}",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats row with economy indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Economy gauge
                EconomyGauge(
                    economy = bowler.economy,
                    category = bowler.economyCategory,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Additional stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MiniStatBox(
                        value = "${bowler.dots}",
                        label = "Dots",
                        color = colors.statusSuccess
                    )
                    if (bowler.wides + bowler.noBalls > 0) {
                        MiniStatBox(
                            value = "${bowler.wides + bowler.noBalls}",
                            label = "Extras",
                            color = colors.statusWarning
                        )
                    }
                }
            }
        }
    }
}

/**
 * Economy gauge visualization
 */
@Composable
private fun EconomyGauge(
    economy: Float,
    category: EconomyCategory,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val animatedEconomy by animateFloatAsState(
        targetValue = economy,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "economy"
    )
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Economy",
                color = colors.textTertiary,
                fontSize = 10.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = String.format("%.2f", animatedEconomy),
                    color = category.color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = category.color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = category.label,
                        color = category.color,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Economy bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            val barWidth = this.size.width
            val barHeight = this.size.height
            
            // Background
            drawRoundRect(
                color = GlassBorder.copy(alpha = 0.2f),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            
            // Economy zones
            val zones = listOf(
                0f to 0.3f to StatusSuccess,
                0.3f to 0.5f to StatusInfo,
                0.5f to 0.7f to StatusWarning,
                0.7f to 1f to StatusLive
            )
            
            zones.forEach { (range, color) ->
                val (start, end) = range
                drawRoundRect(
                    color = color.copy(alpha = 0.3f),
                    topLeft = Offset(start * barWidth, 0f),
                    size = Size((end - start) * barWidth, barHeight),
                    cornerRadius = if (start == 0f || end == 1f) CornerRadius(4.dp.toPx()) else CornerRadius.Zero
                )
            }
            
            // Indicator
            val indicatorPosition = (economy / 12f).coerceIn(0f, 1f) * barWidth
            drawCircle(
                color = category.color,
                radius = 5.dp.toPx(),
                center = Offset(indicatorPosition.coerceIn(5.dp.toPx(), barWidth - 5.dp.toPx()), barHeight / 2)
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(indicatorPosition.coerceIn(5.dp.toPx(), barWidth - 5.dp.toPx()), barHeight / 2)
            )
        }
    }
}

/**
 * Mini stat box
 */
@Composable
private fun MiniStatBox(value: String, label: String, color: Color) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = colors.textTertiary,
            fontSize = 9.sp
        )
    }
}

/**
 * Team bowling summary
 */
@Composable
private fun BowlingSummary(bowlers: List<BowlerPerformance>, teamColor: Color) {
    val totalOvers = bowlers.sumOf { it.overs.toDouble() }.toFloat()
    val totalRuns = bowlers.sumOf { it.runs }
    val totalWickets = bowlers.sumOf { it.wickets }
    val totalDots = bowlers.sumOf { it.dots }
    val teamEconomy = if (totalOvers > 0) totalRuns / totalOvers else 0f
    val bestBowler = bowlers.maxByOrNull { it.wickets * 100 - it.runs }
    val colors = BouncerDesignSystem.colors
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.glassBorderHighlight,
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "TEAM BOWLING",
                color = colors.textTertiary,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalWickets",
                        color = teamColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Wickets",
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.2f", teamEconomy),
                        color = if (teamEconomy < 7) colors.statusSuccess else if (teamEconomy < 9) colors.statusWarning else colors.statusLive,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Economy",
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalDots",
                        color = colors.statusSuccess,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Dot Balls",
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            
            bestBowler?.let { bowler ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.glassBorder.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = colors.goldDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Best: ${bowler.name} (${bowler.wickets}/${bowler.runs})",
                        color = colors.goldDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Mini bowling card for compact views
 */
@Composable
fun MiniBowlingCard(
    bowler: BowlerPerformance,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.glassBorderHighlight,
        border = BorderStroke(0.5.dp, bowler.economyCategory.color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = bowler.name,
                    color = colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${bowler.overs} ov",
                    color = colors.textTertiary,
                    fontSize = 9.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${bowler.wickets}/${bowler.runs}",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.1f", bowler.economy),
                    color = bowler.economyCategory.color,
                    fontSize = 10.sp
                )
            }
        }
    }
}
