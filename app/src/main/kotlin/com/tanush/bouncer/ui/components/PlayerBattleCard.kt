package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * PlayerBattleCard - Head-to-Head PvP Analytics Component
 * 
 * Displays real-time batsman vs bowler statistics with:
 * - Historical head-to-head data from Cricsheet
 * - Current match performance
 * - Win probability impact
 * - Animated visual indicators
 */
@Composable
fun PlayerBattleCard(
    batsmanName: String,
    bowlerName: String,
    headToHead: BatsmanVsBowlerStats,
    modifier: Modifier = Modifier,
    onExpandChange: ((Boolean) -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    var isExpanded by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "battleCard")
    val vsGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vsGlow"
    )

    GlassCard(
        modifier = modifier.clickable { 
            isExpanded = !isExpanded
            onExpandChange?.invoke(isExpanded)
        },
        cornerRadius = 16.dp,
        importance = BouncerImportance.CRITICAL,
        glowColor = SixerPurple
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with VS indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Batsman Side
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        hierarchy.secondary.primary.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                            .border(1.5.dp, hierarchy.secondary.primary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = batsmanName.take(2).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = hierarchy.secondary.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = batsmanName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "BATSMAN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textTertiary,
                        letterSpacing = 1.5.sp
                    )
                }
                
                // VS Indicator
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            spotColor = SixerPurple.copy(alpha = vsGlow)
                        )
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    SixerPurple.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            CircleShape
                        )
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    SixerPurple.copy(alpha = vsGlow),
                                    ErrorRed.copy(alpha = vsGlow * 0.7f)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                }
                
                // Bowler Side
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        ErrorRed.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                            .border(1.5.dp, ErrorRed.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bowlerName.take(2).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = ErrorRed
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = bowlerName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "BOWLER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 1.5.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BattleStatItem(
                    label = "BALLS",
                    value = "${headToHead.ballsFaced}",
                    color = TextPrimary
                )
                
                BattleStatItem(
                    label = "RUNS",
                    value = "${headToHead.runsScored}",
                    color = hierarchy.secondary.primary
                )
                
                BattleStatItem(
                    label = "OUTS",
                    value = "${headToHead.dismissals}",
                    color = ErrorRed
                )
                
                BattleStatItem(
                    label = "SR",
                    value = String.format("%.1f", headToHead.strikeRate),
                    color = if (headToHead.strikeRate >= 100) SuccessGreen else WarningOrange
                )
            }
            
            // Expand/Collapse Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Expanded Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        GlassBorder,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Detailed Stats Grid
                    DetailedStatsGrid(headToHead)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Dominance Indicator
                    DominanceBar(headToHead)
                }
            }
        }
    }
}

@Composable
private fun BattleStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = color,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun DetailedStatsGrid(stats: BatsmanVsBowlerStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DetailStatColumn(
            stats = listOf(
                "4s" to "${stats.fours}",
                "6s" to "${stats.sixes}",
                "Dots" to "${stats.dotBalls}"
            )
        )
        
        DetailStatColumn(
            stats = listOf(
                "Avg" to String.format("%.1f", stats.average),
                "Contests" to "${stats.totalContests}",
                "Format" to stats.format
            )
        )
    }
}

@Composable
private fun DetailStatColumn(stats: List<Pair<String, String>>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stats.forEach { (label, value) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.width(56.dp)
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun DominanceBar(stats: BatsmanVsBowlerStats) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val batsmanDominance = stats.batsmanDominance
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "BATSMAN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (batsmanDominance > 0.5f) hierarchy.secondary.primary else colors.textTertiary,
                letterSpacing = 1.sp
            )
            Text(
                text = "DOMINANCE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textTertiary,
                letterSpacing = 1.sp
            )
            Text(
                text = "BOWLER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (batsmanDominance < 0.5f) colors.statusLive else colors.textTertiary,
                letterSpacing = 1.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Dominance progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Batsman side (Gold)
                Box(
                    modifier = Modifier
                        .weight(batsmanDominance.coerceIn(0.1f, 0.9f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    hierarchy.secondary.primary,
                                    hierarchy.secondary.primary
                                )
                            )
                        )
                )
                
                // Bowler side (Red)
                Box(
                    modifier = Modifier
                        .weight((1 - batsmanDominance).coerceIn(0.1f, 0.9f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ErrorRed,
                                    ErrorRedMuted
                                )
                            )
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Dominance percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(batsmanDominance * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = hierarchy.secondary.primary
            )
            Text(
                text = "${((1 - batsmanDominance) * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
        }
    }
}

/**
 * Data class for batsman vs bowler head-to-head statistics
 */
data class BatsmanVsBowlerStats(
    val ballsFaced: Int = 0,
    val runsScored: Int = 0,
    val dismissals: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val dotBalls: Int = 0,
    val totalContests: Int = 0,
    val format: String = "ALL",
    val strikeRate: Float = 0f,
    val average: Float = 0f,
    val batsmanDominance: Float = 0.5f // 0-1, where 0.5 is neutral
) {
    companion object {
        /**
         * Creates BatsmanVsBowlerStats from raw data
         */
        fun calculate(
            balls: Int,
            runs: Int,
            outs: Int,
            fours: Int = 0,
            sixes: Int = 0,
            dots: Int = 0,
            contests: Int = 1,
            format: String = "ALL"
        ): BatsmanVsBowlerStats {
            val sr = if (balls > 0) (runs.toFloat() / balls) * 100 else 0f
            val avg = if (outs > 0) runs.toFloat() / outs else runs.toFloat()
            
            // Calculate dominance based on strike rate and dismissal rate
            val expectedSR = 125f // Average T20 strike rate benchmark
            val srFactor = (sr / expectedSR).coerceIn(0f, 2f) / 2f
            val dismissalRate = if (balls > 0) (outs.toFloat() / balls) * 100 else 0f
            val expectedDismissalRate = 5f // Average dismissal rate per 100 balls
            val dismissalFactor = 1f - (dismissalRate / (expectedDismissalRate * 2)).coerceIn(0f, 1f)
            
            val dominance = ((srFactor * 0.6f) + (dismissalFactor * 0.4f)).coerceIn(0.1f, 0.9f)
            
            return BatsmanVsBowlerStats(
                ballsFaced = balls,
                runsScored = runs,
                dismissals = outs,
                fours = fours,
                sixes = sixes,
                dotBalls = dots,
                totalContests = contests,
                format = format,
                strikeRate = sr,
                average = avg,
                batsmanDominance = dominance
            )
        }
    }
}

/**
 * Compact version of PlayerBattleCard for inline display
 */
@Composable
fun PlayerBattleCardCompact(
    batsmanName: String,
    bowlerName: String,
    headToHead: BatsmanVsBowlerStats,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    GlassSurface(
        modifier = modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        cornerRadius = 12.dp,
        importance = BouncerImportance.STANDARD
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Batsman
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = batsmanName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = hierarchy.secondary.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "vs $bowlerName",
                    fontSize = 10.sp,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Quick Stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${headToHead.runsScored}/${headToHead.dismissals}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "R/W",
                        fontSize = 8.sp,
                        color = TextTertiary
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.0f", headToHead.strikeRate),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (headToHead.strikeRate >= 100) SuccessGreen else TextPrimary
                    )
                    Text(
                        text = "SR",
                        fontSize = 8.sp,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}
