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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Head-to-Head Comparison Components.
 * Inspired by Cricbuzz/ESPNcricinfo H2H stats and the design mockups.
 */

data class TeamComparisonData(
    val teamName: String,
    val teamAcronym: String,
    val wins: Int,
    val losses: Int,
    val draws: Int = 0,
    val primaryColor: Color = ChartBlue,
    val recentForm: List<Boolean> = emptyList()
)

/**
 * Head-to-Head Premium Card.
 * Shows team comparison with glassmorphism and neon accents.
 */
@Composable
fun HeadToHeadPremiumCard(
    team1: TeamComparisonData,
    team2: TeamComparisonData,
    format: String = "T20",
    totalMatches: Int = team1.wins + team2.wins + team1.draws,
    onFormatChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "h2hGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassSurface,
        border = BorderStroke(
            0.5.dp,
            Brush.linearGradient(
                colors = listOf(
                    team1.primaryColor.copy(alpha = glowAlpha),
                    colors.goldPrimary.copy(alpha = 0.3f),
                    team2.primaryColor.copy(alpha = glowAlpha)
                )
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.width * 0.5f,
                            center = Offset(size.width * 0.5f, size.height * 0.3f)
                        )
                    }
            )
            
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
                    Text(
                        text = "HEAD TO HEAD",
                        color = colors.goldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    
                    // Format Selector
                    FormatSelectorChips(
                        selectedFormat = format,
                        onFormatSelected = onFormatChange ?: {}
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // VS Section with Team Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team 1
                    TeamH2HCard(
                        team = team1,
                        alignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // VS Badge
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.glassBorderHighlight)
                            .border(1.dp, colors.goldPrimary.copy(alpha = 0.5f), CircleShape)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                spotColor = colors.goldPrimary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VS",
                            color = colors.goldPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    
                    // Team 2
                    TeamH2HCard(
                        team = team2,
                        alignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Total Matches
                Text(
                    text = "$totalMatches MATCHES PLAYED",
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Win Comparison Bar
                WinComparisonBar(
                    team1Wins = team1.wins,
                    team2Wins = team2.wins,
                    draws = team1.draws,
                    team1Color = team1.primaryColor,
                    team2Color = team2.primaryColor
                )
            }
        }
    }
}

@Composable
private fun TeamH2HCard(
    team: TeamComparisonData,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    val dsColors = BouncerDesignSystem.colors
    Column(
        modifier = modifier,
        horizontalAlignment = alignment
    ) {
        // Team Badge
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            team.primaryColor.copy(alpha = 0.15f),
                            dsColors.glassBorderHighlight
                        )
                    )
                )
                .border(1.dp, team.primaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = team.teamAcronym,
                color = dsColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Team Name
        Text(
            text = team.teamName.uppercase(),
            color = dsColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Wins Count
        Text(
            text = "${team.wins} WINS",
            color = team.primaryColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Recent Form
        if (team.recentForm.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (alignment == Alignment.Start) 
                    Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                team.recentForm.takeLast(5).forEach { isWin ->
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isWin) dsColors.statusSuccess else dsColors.statusLive)
                    )
                }
            }
        }
    }
}

@Composable
private fun WinComparisonBar(
    team1Wins: Int,
    team2Wins: Int,
    draws: Int,
    team1Color: Color,
    team2Color: Color
) {
    val total = (team1Wins + team2Wins + draws).coerceAtLeast(1)
    val team1Fraction = team1Wins.toFloat() / total
    val team2Fraction = team2Wins.toFloat() / total
    val drawFraction = draws.toFloat() / total
    
    val animatedTeam1 by animateFloatAsState(
        targetValue = team1Fraction,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team1"
    )
    val animatedTeam2 by animateFloatAsState(
        targetValue = team2Fraction,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team2"
    )
    
    Column {
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$team1Wins",
                color = team1Color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            if (draws > 0) {
                Text(
                    text = "$draws draws",
                    color = BouncerDesignSystem.colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "$team2Wins",
                color = team2Color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BouncerDesignSystem.colors.glassOverlay)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Team 1
                if (animatedTeam1 > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedTeam1)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(team1Color.copy(alpha = 0.7f), team1Color)
                                )
                            )
                    )
                }
                
                // Draws
                if (drawFraction > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(drawFraction / (1 - animatedTeam1).coerceAtLeast(0.01f))
                            .background(TextMuted)
                    )
                }
                
                // Team 2 fills the rest
            }
            
            // Team 2 overlay from right
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(animatedTeam2)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(team2Color, team2Color.copy(alpha = 0.7f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun FormatSelectorChips(
    selectedFormat: String,
    onFormatSelected: (String) -> Unit
) {
    val formats = listOf("T20", "ODI", "TEST")
    val dsColors = BouncerDesignSystem.colors
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        formats.forEach { format ->
            val isSelected = format == selectedFormat
            
            Surface(
                modifier = Modifier.clickable { onFormatSelected(format) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) dsColors.goldPrimary else Color.Transparent,
                border = BorderStroke(
                    0.5.dp,
                    if (isSelected) dsColors.goldPrimary else dsColors.glassBorderHighlight
                )
            ) {
                Text(
                    text = format,
                    color = if (isSelected) dsColors.backgroundBase else dsColors.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Tactical Matrix Comparison - Radar chart style display.
 * Inspired by the design mockups head-to-head tactical matrix.
 */
@Composable
fun TacticalMatrixCard(
    team1Name: String,
    team2Name: String,
    team1Color: Color = ChartBlue,
    team2Color: Color = StatusSuccess,
    metrics: List<TacticalMetric>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.glassSurface,
        border = BorderStroke(0.5.dp, colors.goldPrimary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TACTICAL MATRIX",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(color = team1Color, label = team1Name)
                    LegendDot(color = team2Color, label = team2Name)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Metric Bars
            metrics.forEach { metric ->
                MetricComparisonRow(
                    metricName = metric.name,
                    team1Value = metric.team1Value,
                    team2Value = metric.team2Value,
                    team1Color = team1Color,
                    team2Color = team2Color
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

data class TacticalMetric(
    val name: String,
    val team1Value: Float, // 0-100
    val team2Value: Float  // 0-100
)

@Composable
private fun MetricComparisonRow(
    metricName: String,
    team1Value: Float,
    team2Value: Float,
    team1Color: Color,
    team2Color: Color
) {
    val dsColors = BouncerDesignSystem.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${team1Value.toInt()}%",
                color = team1Color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = metricName.uppercase(),
                color = dsColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "${team2Value.toInt()}%",
                color = team2Color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Dual Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(dsColors.glassOverlay)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Team 1 (from left)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(team1Value / 100f * 0.5f)
                        .background(team1Color)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Team 2 (from right, mirrored)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(team2Value / 100f * 0.5f)
                        .background(team2Color)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .shadow(2.dp, CircleShape, spotColor = color)
        )
        Text(
            text = label.uppercase(),
            color = BouncerDesignSystem.colors.textTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
