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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Extras breakdown data
 */
data class ExtrasData(
    val teamName: String,
    val wides: Int,
    val noBalls: Int,
    val byes: Int,
    val legByes: Int,
    val penalty: Int = 0
) {
    val total: Int
        get() = wides + noBalls + byes + legByes + penalty
    
    val bowlingExtras: Int
        get() = wides + noBalls
    
    val fieldingExtras: Int
        get() = byes + legByes
}

/**
 * Premium Extras Analysis Card
 * Detailed breakdown of extras with visual representation
 */
@Composable
fun ExtrasAnalysisCard(
    team1Extras: ExtrasData,
    team2Extras: ExtrasData,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "extras")
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = WarningYellow.copy(alpha = 0.1f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
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
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = WarningYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "EXTRAS ANALYSIS",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                // Total comparison
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExtrasTotalBadge(total = team1Extras.total, teamName = team1Extras.teamName)
                    Text(
                        text = "vs",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                    ExtrasTotalBadge(total = team2Extras.total, teamName = team2Extras.teamName)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Pie chart comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ExtrasPieChart(
                    extras = team1Extras,
                    rotationAngle = rotationAngle
                )
                ExtrasPieChart(
                    extras = team2Extras,
                    rotationAngle = -rotationAngle
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Detailed comparison
            ExtrasComparisonTable(team1 = team1Extras, team2 = team2Extras)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Analysis insights
            ExtrasInsights(team1 = team1Extras, team2 = team2Extras)
        }
    }
}

/**
 * Extras total badge
 */
@Composable
private fun ExtrasTotalBadge(total: Int, teamName: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = WarningYellow.copy(alpha = 0.1f),
        border = BorderStroke(0.5.dp, WarningYellow.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$total",
                color = WarningYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = teamName.take(6),
                color = TextTertiary,
                fontSize = 8.sp
            )
        }
    }
}

/**
 * Extras pie chart
 */
@Composable
private fun ExtrasPieChart(
    extras: ExtrasData,
    rotationAngle: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = extras.teamName,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = this.size.width / 2f - 8.dp.toPx()
                val total = extras.total.coerceAtLeast(1).toFloat()
                
                // Draw outer glow ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            WarningYellow.copy(alpha = 0.1f),
                            ChartBlue.copy(alpha = 0.1f),
                            WarningYellow.copy(alpha = 0.1f)
                        )
                    ),
                    radius = radius + 4.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                var startAngle = rotationAngle - 90f
                
                val segments = listOf(
                    extras.wides.toFloat() to WarningYellow,
                    extras.noBalls.toFloat() to ErrorRed,
                    extras.byes.toFloat() to ChartBlue,
                    extras.legByes.toFloat() to SuccessGreen
                )
                
                segments.forEach { (value, color) ->
                    if (value > 0) {
                        val sweep = (value / total) * 360f
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep - 2f,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )
                        startAngle += sweep
                    }
                }
                
                // Center circle
                drawCircle(
                    color = CardSurface,
                    radius = radius * 0.55f,
                    center = center
                )
            }
            
            // Center text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${extras.total}",
                    color = WarningYellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "extras",
                    color = TextTertiary,
                    fontSize = 8.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Mini legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniLegendDot(color = WarningYellow, value = extras.wides, label = "W")
            MiniLegendDot(color = ErrorRed, value = extras.noBalls, label = "NB")
            MiniLegendDot(color = ChartBlue, value = extras.byes, label = "B")
            MiniLegendDot(color = SuccessGreen, value = extras.legByes, label = "LB")
        }
    }
}

/**
 * Mini legend dot
 */
@Composable
private fun MiniLegendDot(color: Color, value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$value",
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 7.sp
        )
    }
}

/**
 * Extras comparison table
 */
@Composable
private fun ExtrasComparisonTable(team1: ExtrasData, team2: ExtrasData) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f).copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Type",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = team1.teamName.take(8),
                    color = TextTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = team2.teamName.take(8),
                    color = TextTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            
            // Data rows
            ComparisonRow("Wides", team1.wides, team2.wides, WarningYellow)
            ComparisonRow("No Balls", team1.noBalls, team2.noBalls, ErrorRed)
            ComparisonRow("Byes", team1.byes, team2.byes, ChartBlue)
            ComparisonRow("Leg Byes", team1.legByes, team2.legByes, SuccessGreen)
            
            if (team1.penalty > 0 || team2.penalty > 0) {
                ComparisonRow("Penalty", team1.penalty, team2.penalty, TextTertiary)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            
            // Total row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TOTAL",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = "${team1.total}",
                    color = WarningYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${team2.total}",
                    color = WarningYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Comparison row
 */
@Composable
private fun ComparisonRow(label: String, value1: Int, value2: Int, color: Color) {
    val better = when {
        value1 < value2 -> 1
        value2 < value1 -> 2
        else -> 0
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        Text(
            text = "$value1",
            color = if (better == 1) SuccessGreen else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (better == 1) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$value2",
            color = if (better == 2) SuccessGreen else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (better == 2) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Extras insights
 */
@Composable
private fun ExtrasInsights(team1: ExtrasData, team2: ExtrasData) {
    val insights = mutableListOf<Pair<String, Color>>()
    
    // Bowling discipline comparison
    if (team1.bowlingExtras > team2.bowlingExtras + 5) {
        insights.add("${team1.teamName} conceded ${team1.bowlingExtras - team2.bowlingExtras} more bowling extras" to WarningYellow)
    } else if (team2.bowlingExtras > team1.bowlingExtras + 5) {
        insights.add("${team2.teamName} conceded ${team2.bowlingExtras - team1.bowlingExtras} more bowling extras" to WarningYellow)
    }
    
    // High wides
    if (team1.wides >= 10) {
        insights.add("${team1.teamName} bowled ${team1.wides} wides - poor discipline" to ErrorRed)
    }
    if (team2.wides >= 10) {
        insights.add("${team2.teamName} bowled ${team2.wides} wides - poor discipline" to ErrorRed)
    }
    
    // Good keeping
    if (team1.byes <= 2 && team1.legByes <= 2) {
        insights.add("${team1.teamName} had excellent fielding extras (${team1.fieldingExtras})" to SuccessGreen)
    }
    if (team2.byes <= 2 && team2.legByes <= 2) {
        insights.add("${team2.teamName} had excellent fielding extras (${team2.fieldingExtras})" to SuccessGreen)
    }
    
    if (insights.isNotEmpty()) {
        Column {
            Text(
                text = "INSIGHTS",
                color = TextTertiary,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            insights.take(2).forEach { (insight, color) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = insight,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Mini extras display
 */
@Composable
fun MiniExtrasDisplay(
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int,
    modifier: Modifier = Modifier
) {
    val total = wides + noBalls + byes + legByes
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = WarningYellow.copy(alpha = 0.1f),
        border = BorderStroke(0.5.dp, WarningYellow.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Extras:",
                color = TextTertiary,
                fontSize = 10.sp
            )
            Text(
                text = "$total",
                color = WarningYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "(w$wides, nb$noBalls, b$byes, lb$legByes)",
                color = TextTertiary,
                fontSize = 9.sp
            )
        }
    }
}
