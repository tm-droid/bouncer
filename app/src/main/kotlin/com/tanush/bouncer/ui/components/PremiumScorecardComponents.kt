package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Premium batting scorecard data
 */
data class BatsmanScoreData(
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Float,
    val dismissal: String, // "not out", "c Player b Bowler", etc.
    val isNotOut: Boolean = false,
    val isOnStrike: Boolean = false
)

/**
 * Premium bowling scorecard data
 */
data class BowlerScoreData(
    val name: String,
    val overs: Float,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Float,
    val dots: Int = 0,
    val isBowling: Boolean = false
)

/**
 * Fall of wicket data
 */
data class FallOfWicketData(
    val wicketNumber: Int,
    val score: Int,
    val batsmanName: String,
    val overNumber: Float
)

/**
 * Premium Batting Scorecard Component
 */
@Composable
fun PremiumBattingScorecard(
    teamName: String,
    teamColor: Color,
    batsmen: List<BatsmanScoreData>,
    extras: Int,
    total: String,
    overs: String,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = teamColor.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
    ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        teamColor.copy(alpha = 0.3f),
                                        teamColor.copy(alpha = 0.1f)
                                    )
                                ),
                                CircleShape
                            )
                            .border(1.dp, teamColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SportsCricket,
                            contentDescription = null,
                            tint = teamColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            teamName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Batting",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        total,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = teamColor
                    )
                    Text(
                        "($overs ov)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Column Headers
            ScorecardHeader()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HorizontalDivider(color = CardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
            
            // Batsmen rows
            batsmen.forEachIndexed { index, batsman ->
                val delayedVisible by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 500,
                        delayMillis = 50 * index,
                        easing = FastOutSlowInEasing
                    ),
                    label = "batsman_$index"
                )
                
                BatsmanRow(
                    batsman = batsman,
                    teamColor = teamColor,
                    animationProgress = delayedVisible
                )
                
                if (index < batsmen.lastIndex) {
                    HorizontalDivider(
                        color = CardBorder.copy(alpha = 0.15f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            HorizontalDivider(color = CardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Extras
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Extras",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Text(
                    "$extras",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ScorecardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Batter",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "R",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "B",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "4s",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "6s",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "SR",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun BatsmanRow(
    batsman: BatsmanScoreData,
    teamColor: Color,
    animationProgress: Float
) {
    val isHighScore = batsman.runs >= 50
    val isCentury = batsman.runs >= 100
    
    val rowAlpha = animationProgress
    val slideOffset = (1f - animationProgress) * 20f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rowAlpha
                translationX = slideOffset
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    batsman.name,
                    fontSize = 14.sp,
                    fontWeight = if (batsman.isNotOut) FontWeight.Bold else FontWeight.Medium,
                    color = if (batsman.isNotOut) teamColor else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (batsman.isOnStrike) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(ShinyGoldDeep, ShinyGoldLight)
                                ),
                                CircleShape
                            )
                    )
                }
                
                if (isCentury) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MilestoneBadge("100", NeonSuccess)
                } else if (isHighScore) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MilestoneBadge("50", ShinyGoldDeep)
                }
            }
            
            if (!batsman.isNotOut && batsman.dismissal.isNotEmpty()) {
                Text(
                    batsman.dismissal,
                    fontSize = 11.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (batsman.isNotOut) {
                Text(
                    "not out",
                    fontSize = 11.sp,
                    color = NeonSuccess.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Stats
        Text(
            "${batsman.runs}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighScore) teamColor else TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "${batsman.balls}",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "${batsman.fours}",
            fontSize = 13.sp,
            color = if (batsman.fours > 0) BoundaryBlue else TextTertiary,
            fontWeight = if (batsman.fours > 0) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "${batsman.sixes}",
            fontSize = 13.sp,
            color = if (batsman.sixes > 0) SixerPurple else TextTertiary,
            fontWeight = if (batsman.sixes > 0) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            String.format("%.1f", batsman.strikeRate),
            fontSize = 13.sp,
            color = when {
                batsman.strikeRate >= 150 -> NeonSuccess
                batsman.strikeRate >= 100 -> TextPrimary
                else -> TextSecondary
            },
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun MilestoneBadge(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Premium Bowling Scorecard Component
 */
@Composable
fun PremiumBowlingScorecard(
    teamName: String,
    teamColor: Color,
    bowlers: List<BowlerScoreData>,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = teamColor.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.25f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            teamColor.copy(alpha = 0.15f),
                            CircleShape
                        )
                        .border(0.5.dp, teamColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Adjust,
                        contentDescription = null,
                        tint = teamColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Bowling",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Column Headers
            BowlingHeader()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HorizontalDivider(color = CardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
            
            // Bowler rows
            bowlers.forEachIndexed { index, bowler ->
                val delayedVisible by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 500,
                        delayMillis = 50 * index,
                        easing = FastOutSlowInEasing
                    ),
                    label = "bowler_$index"
                )
                
                BowlerRow(
                    bowler = bowler,
                    teamColor = teamColor,
                    animationProgress = delayedVisible
                )
                
                if (index < bowlers.lastIndex) {
                    HorizontalDivider(
                        color = CardBorder.copy(alpha = 0.15f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BowlingHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Bowler",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "O",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "M",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "R",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "W",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "ECO",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

@Composable
private fun BowlerRow(
    bowler: BowlerScoreData,
    teamColor: Color,
    animationProgress: Float
) {
    val isGoodFigures = bowler.wickets >= 3
    val isFiveWicket = bowler.wickets >= 5
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animationProgress
                translationX = (1f - animationProgress) * 20f
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                bowler.name,
                fontSize = 14.sp,
                fontWeight = if (bowler.isBowling) FontWeight.Bold else FontWeight.Medium,
                color = if (bowler.isBowling) teamColor else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (bowler.isBowling) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(ShinyGoldDeep, ShinyGoldLight)
                            ),
                            CircleShape
                        )
                )
            }
            
            if (isFiveWicket) {
                Spacer(modifier = Modifier.width(6.dp))
                MilestoneBadge("5W", NeonSuccess)
            } else if (isGoodFigures) {
                Spacer(modifier = Modifier.width(6.dp))
                MilestoneBadge("3W", ShinyGoldDeep)
            }
        }
        
        Text(
            String.format("%.1f", bowler.overs),
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "${bowler.maidens}",
            fontSize = 13.sp,
            color = if (bowler.maidens > 0) NeonSuccess else TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "${bowler.runs}",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "${bowler.wickets}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isFiveWicket -> NeonSuccess
                isGoodFigures -> teamColor
                bowler.wickets > 0 -> TextPrimary
                else -> TextTertiary
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            String.format("%.1f", bowler.economy),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                bowler.economy <= 6 -> NeonSuccess
                bowler.economy <= 8 -> TextPrimary
                bowler.economy <= 10 -> ShinyGoldDeep
                else -> NeonError
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

/**
 * Fall of Wickets Timeline Component
 */
@Composable
fun FallOfWicketsTimeline(
    fallOfWickets: List<FallOfWicketData>,
    teamColor: Color,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = teamColor.copy(alpha = 0.1f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = ShinyGoldDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Fall of Wickets",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Timeline visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val lineY = size.height * 0.5f
                    
                    // Draw timeline line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                CardBorder.copy(alpha = 0.2f),
                                teamColor.copy(alpha = 0.5f),
                                CardBorder.copy(alpha = 0.2f)
                            )
                        ),
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                
                // Wicket markers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fallOfWickets.forEachIndexed { index, fow ->
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isVisible) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 80 * index,
                                easing = FastOutSlowInEasing
                            ),
                            label = "wicket_$index"
                        )
                        
                        WicketMarker(
                            fow = fow,
                            teamColor = teamColor,
                            scale = animatedScale
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WicketMarker(
    fow: FallOfWicketData,
    teamColor: Color,
    scale: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = scale
        }
    ) {
        Text(
            "${fow.score}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            teamColor.copy(alpha = 0.4f),
                            teamColor.copy(alpha = 0.1f)
                        )
                    ),
                    CircleShape
                )
                .border(1.5.dp, teamColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${fow.wicketNumber}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = teamColor
            )
        }
        
        Text(
            "${fow.overNumber}",
            fontSize = 10.sp,
            color = TextTertiary
        )
    }
}

/**
 * Extras Breakdown Component
 */
@Composable
fun ExtrasBreakdown(
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int,
    penalty: Int = 0,
    modifier: Modifier = Modifier
) {
    val total = wides + noBalls + byes + legByes + penalty
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.08f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtraItem(label = "Wd", value = wides, color = ShinyGoldDeep)
            ExtraItem(label = "Nb", value = noBalls, color = NeonError)
            ExtraItem(label = "B", value = byes, color = TextSecondary)
            ExtraItem(label = "Lb", value = legByes, color = TextSecondary)
            
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp)
                    .background(CardBorder.copy(alpha = 0.3f))
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$total",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    "Total",
                    fontSize = 10.sp,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun ExtraItem(
    label: String,
    value: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) color else TextTertiary
        )
        Text(
            label,
            fontSize = 10.sp,
            color = TextTertiary
        )
    }
}
