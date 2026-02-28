package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Cricbuzz/ESPNcricinfo Inspired Components.
 * Premium live feed UI with real-time match intelligence.
 */

/**
 * Live Commentary Feed Card - Cricbuzz Style.
 * Features real-time ball-by-ball updates with premium glassmorphism.
 */
@Composable
fun LiveCommentaryFeedCard(
    overNumber: String,
    description: String,
    runs: String,
    isWicket: Boolean = false,
    isBoundary: Boolean = false,
    bowlerName: String = "",
    batsmanName: String = "",
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        isWicket -> WicketRed
        isBoundary && runs == "6" -> BoundarySix
        isBoundary -> BoundaryFour
        else -> Color.White.copy(alpha = 0.6f)
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "commentaryPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            0.5.dp,
            if (isWicket || isBoundary) accentColor.copy(alpha = pulseAlpha) 
            else Gold400.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Over Number Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isWicket || isBoundary) 
                            accentColor.copy(alpha = 0.15f) 
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        1.dp,
                        accentColor.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = overNumber,
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Commentary Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bowlerName.isNotEmpty() && batsmanName.isNotEmpty()) {
                        Text(
                            text = "$bowlerName to $batsmanName",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    // Run Badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isWicket) "W" else runs,
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Quick Stats Bar - Inspired by ESPNCricinfo.
 * Horizontal scrolling stats similar to ESPN's match center.
 */
@Composable
fun QuickMatchStatsBar(
    currentRunRate: String,
    requiredRunRate: String? = null,
    partnership: String = "",
    lastWicket: String = "",
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            QuickStatChip(
                label = "CRR",
                value = currentRunRate,
                icon = Icons.Default.Speed,
                accentColor = ShinyGoldDeep
            )
        }
        
        if (!requiredRunRate.isNullOrEmpty()) {
            item {
                QuickStatChip(
                    label = "RRR",
                    value = requiredRunRate,
                    icon = Icons.Default.TrendingUp,
                    accentColor = ChartBlue
                )
            }
        }
        
        if (partnership.isNotEmpty()) {
            item {
                QuickStatChip(
                    label = "Partnership",
                    value = partnership,
                    icon = Icons.Default.Handshake,
                    accentColor = SuccessGreen
                )
            }
        }
        
        if (lastWicket.isNotEmpty()) {
            item {
                QuickStatChip(
                    label = "Last Wkt",
                    value = lastWicket,
                    icon = Icons.Default.Close,
                    accentColor = WicketRed
                )
            }
        }
    }
}

@Composable
private fun QuickStatChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = label.uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * Match Prediction Gauge - Similar to Cricbuzz Win Probability.
 * Premium liquid-style probability slider.
 */
@Composable
fun WinPredictionGauge(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: Color = ChartBlue,
    team2Color: Color = WicketRed,
    modifier: Modifier = Modifier
) {
    val team2Probability = 1f - team1Probability
    
    val animatedTeam1 by animateFloatAsState(
        targetValue = team1Probability,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "team1Prob"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, GoldHairline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "WIN PREDICTION",
                    color = ShinyGoldDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ShinyGoldDeep,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Team Names & Percentages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = team1Name.uppercase(),
                        color = team1Color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${(team1Probability * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = team2Name.uppercase(),
                        color = team2Color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${(team2Probability * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Probability Bar (Liquid Style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Team 1 Fill
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedTeam1)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        team1Color.copy(alpha = 0.8f),
                                        team1Color
                                    )
                                )
                            )
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
                                spotColor = team1Color
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Based on current match situation & historical data",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Recent Form Indicator - Similar to Cricbuzz Team Form.
 * Shows W/L streak with premium badges.
 */
@Composable
fun TeamFormIndicator(
    teamName: String,
    recentForm: List<Boolean>, // true = Win, false = Loss
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "FORM",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        
        recentForm.takeLast(5).forEach { isWin ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isWin) SuccessGreen else WicketRed)
                    .shadow(
                        elevation = 2.dp,
                        shape = CircleShape,
                        spotColor = if (isWin) SuccessGreen else WicketRed
                    )
            )
        }
    }
}

/**
 * Over-by-Over Runs Bar - Inspired by ESPNCricinfo Manhattan chart style.
 * Mini bar visualization of runs per over.
 */
@Composable
fun OverByOverMiniChart(
    oversData: List<Int>, // Runs per over
    maxOvers: Int = 20,
    modifier: Modifier = Modifier
) {
    val maxRuns = oversData.maxOrNull() ?: 1
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, Gold400.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RUNS PER OVER",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "${oversData.size} overs",
                    color = ShinyGoldDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Bar Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                oversData.forEachIndexed { index, runs ->
                    val height = (runs.toFloat() / maxRuns.coerceAtLeast(1)) * 60
                    val color = when {
                        runs >= 12 -> BoundarySix
                        runs >= 8 -> BoundaryFour
                        runs <= 4 -> Color.White.copy(alpha = 0.3f)
                        else -> ShinyGoldDeep
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(height.dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

/**
 * Innings Summary Card - Premium compact innings overview.
 * Inspired by Cricbuzz scorecard header.
 */
@Composable
fun InningsSummaryCard(
    teamName: String,
    score: String,
    overs: String,
    extras: String = "",
    runRate: String = "",
    isCurrentInnings: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isCurrentInnings) ShinyGoldDeep else Color.White.copy(alpha = 0.08f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "inningsPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentInnings) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            if (isCurrentInnings) 1.dp else 0.5.dp,
            if (isCurrentInnings) borderColor.copy(alpha = pulseAlpha) else borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCurrentInnings) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LiveBlue)
                        )
                    }
                    Text(
                        text = teamName.uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                
                if (extras.isNotEmpty()) {
                    Text(
                        text = "Extras: $extras",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = score,
                        color = if (isCurrentInnings) ShinyGoldDeep else Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "($overs)",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                if (runRate.isNotEmpty()) {
                    Text(
                        text = "RR: $runRate",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
